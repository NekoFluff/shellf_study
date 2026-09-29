package com.crazyfluff.shellfstudy.shared.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.audio.playMatchingReading
import com.crazyfluff.shellfstudy.shared.audio.selectAudioFor
import com.crazyfluff.shellfstudy.shared.coroutines.runDurably
import com.crazyfluff.shellfstudy.shared.data.LastSessionKind
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import com.crazyfluff.shellfstudy.shared.data.PersistedReviewSession
import com.crazyfluff.shellfstudy.shared.lifecycle.AppForegroundTracker
import com.crazyfluff.shellfstudy.shared.quiz.AnswerFeedback
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType
import com.crazyfluff.shellfstudy.shared.quiz.QuizSession
import com.crazyfluff.shellfstudy.shared.quiz.QuizSessionSummary
import com.crazyfluff.shellfstudy.shared.quiz.toLastSessionSummary
import com.crazyfluff.shellfstudy.shared.quiz.QuizSessionTiming
import com.crazyfluff.shellfstudy.shared.quiz.QuizTimingUiState
import com.crazyfluff.shellfstudy.shared.quiz.SlowAnswer
import com.crazyfluff.shellfstudy.shared.quiz.isPitchAccentEligible
import com.crazyfluff.shellfstudy.shared.quiz.requiresTapToRevealAnswer
import com.crazyfluff.shellfstudy.shared.quiz.QuizSessionPhase
import com.crazyfluff.shellfstudy.shared.quiz.QuizSessionState
import com.crazyfluff.shellfstudy.shared.quiz.QuizSessionViewModel
import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.isAuthError
import com.crazyfluff.shellfstudy.shared.data.AppSettings
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.PitchAccentRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.model.RankChange
import com.crazyfluff.shellfstudy.shared.data.model.ReviewItem
import com.crazyfluff.shellfstudy.shared.data.model.ReviewPriority
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.AnswerReadingHint
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentUiState
import com.crazyfluff.shellfstudy.shared.session.ReviewSessionController
import kotlin.time.Clock
import com.crazyfluff.shellfstudy.shared.sync.SyncOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewUiState(
    val phase: Phase = Phase.Loading,
    // Deliberately not folded into Phase — see LessonUiState.exit's doc comment for why. A review has
    // only the one way out, so a boolean rather than that feature's sealed exit request.
    val isAbandoned: Boolean = false
) : QuizSessionState<ReviewUiState, ReviewUiState.Phase.Active, ReviewItem> {

    override val quizPhase: Phase.Active? get() = phase as? Phase.Active

    override fun withQuizPhase(phase: Phase.Active): ReviewUiState = copy(phase = phase)

    sealed interface Phase {
        data object Loading : Phase
        data class Error(val message: String) : Phase
        data object NoReviewsAvailable : Phase

        data class Active(
            // Non-nullable by construction — see LessonUiState.Phase.Quiz's doc comment for why.
            override val currentItem: ReviewItem,
            override val currentQuestionType: QuestionType,
            override val answerInput: String = "",
            override val feedback: AnswerFeedback? = null,
            override val rankChange: RankChange? = null,
            override val undoCounter: Int = 0,
            // Bumped on every advance to a new current question, even a requeued one that repeats
            // the same item/type — see QuizQuestionContent's focusResetKey, which needs a signal
            // that's guaranteed to change on advance regardless of whether the question repeats.
            override val questionSequence: Int = 0,
            override val isDetailsExpanded: Boolean = false,
            override val answerTypeMismatchCount: Int = 0,
            val totalCount: Int = 0,
            val remainingCount: Int = 0,
            // A modifier within the active variant, not a separate mode — wrapUp() changes what
            // happens to the queue, but the screen still renders exactly the same question UI either
            // way, so this doesn't warrant its own Phase (unlike Lesson's Select/Study/Quiz, which
            // really are different rendering modes).
            val isWrappingUp: Boolean = false,
            override val timing: QuizTimingUiState = QuizTimingUiState(),
            // Whether the correct-answer text is visible for the current (wrong) feedback. Always
            // true except right after a wrong submit while the "require tap to reveal answer"
            // setting is on — see gradeAnswer/revealAnswer. Give-ups and correct/close-match answers
            // are never gated, so this stays true for them regardless of the setting.
            override val answerRevealed: Boolean = true,
            // The reading + pitch-accent patterns + audio for the just-graded reading question. The
            // reading/audio are published with the feedback (see gradeAnswer); the pitch patterns are
            // *observed* live for as long as this question is the current one (see
            // pitchAccentHintKey/init), so an update from any writer to the bundled dictionary's
            // cache lands here without a refetch.
            val answerHint: AnswerReadingHint? = null
        ) : Phase, QuizSessionPhase<ReviewItem, Active> {
            override fun withAnswerInput(value: String): Active = copy(answerInput = value)
            override fun withAnswerRevealed(revealed: Boolean): Active = copy(answerRevealed = revealed)
            override fun withDetailsExpanded(expanded: Boolean): Active = copy(isDetailsExpanded = expanded)
            override fun withAnswerTypeMismatchCount(count: Int): Active = copy(answerTypeMismatchCount = count)
            override fun withTiming(timing: QuizTimingUiState): Active = copy(timing = timing)
        }

        data class Complete(
            val sessionItemsReviewed: Int = 0,
            val sessionItemsCorrectFirstTry: Int = 0,
            val sessionMissedItems: List<ReviewItem> = emptyList(),
            val sessionTotalElapsedMs: Long = 0L,
            val sessionAverageTimePerItemMs: Long = 0L,
            val sessionSlowestAnswers: List<SlowAnswer<ReviewItem>> = emptyList()
        ) : Phase
    }
}

private fun QuizSessionSummary<ReviewItem>.toCompletePhase() = ReviewUiState.Phase.Complete(
    sessionItemsReviewed = itemsCount,
    sessionItemsCorrectFirstTry = correctFirstTry,
    sessionMissedItems = missedItems,
    sessionTotalElapsedMs = totalElapsedMs,
    sessionAverageTimePerItemMs = averageTimePerItemMs,
    sessionSlowestAnswers = slowestAnswers
)

/** How many distinct items can be in flight (admitted into the queue's working set) at once —
 *  matches WaniKani's own review session, which stops introducing new items once 10 are already
 *  being worked on. Fixed rather than a setting, same as upstream. Applied when the queue is built
 *  ([buildQueue]) and on every grade, which admits one more item whenever one finishes. */
private const val MAX_IN_FLIGHT_REVIEW_ITEMS = 10

/**
 * Identifies the word whose pitch accent the current question's hint should be watching — null
 * whenever there is nothing to show (a meaning question, the "show answer reading pitch accent"
 * setting off, a subject type pitch accent doesn't apply to, or no answer revealed yet).
 *
 * [assignmentId] and [reading] pin the observation to the *question* that asked for it: the answer
 * reading is chosen and published at the same moment the key is set, so an emission can be matched
 * back to the exact hint it belongs to and can't land on the next question's.
 */
private data class PitchAccentHintKey(val assignmentId: Long, val characters: String, val reading: String)

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModel(
    private val assignmentRepository: AssignmentRepository,
    private val outboxRepository: OutboxRepository,
    private val statsRepository: StatsRepository,
    private val sessionController: ReviewSessionController,
    private val lastSessionSummaryRepository: LastSessionSummaryRepository,
    override val pronunciationAudioPlayer: PronunciationAudioPlayer,
    private val settingsRepository: SettingsRepository,
    private val pitchAccentRepository: PitchAccentRepository,
    private val appForegroundTracker: AppForegroundTracker,
    private val applicationScope: CoroutineScope,
    private val syncOrchestrator: SyncOrchestrator
) : QuizSessionViewModel<ReviewItem, ReviewUiState.Phase.Active, ReviewUiState>(), ReviewActions {

    override val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    /** The word the current question's reading hint is watching — see [PitchAccentHintKey]. A plain
     *  field rather than part of [ReviewUiState] because the screen never reads the key itself; it
     *  only ever sees the [PitchAccentUiState] the collector below derives from it. */
    private val pitchAccentHintKey = MutableStateFlow<PitchAccentHintKey?>(null)

    /** The session's questions and its pending submission — every change replaces it whole, so a
     *  snapshot taken for persisting can never be changed underneath the write. */
    private var session = ReviewSession()

    // Tracks only the time the session was actively being viewed — see QuizSessionTiming. A
    // completed/abandoned/empty-queue session is handled structurally by sessionController.persist()
    // itself (a no-op once the session isn't ACTIVE), not by a flag check here — see
    // QuizSessionController. Runs on applicationScope rather than viewModelScope so this flush
    // actually executes when triggered from onCleared() (viewModelScope is cancelled just before
    // onCleared() runs, so a viewModelScope.launch here would silently never execute).
    override val sessionTiming = QuizSessionTiming(
        onResume = { now -> updateQuizTiming { it.copy(sessionActiveSegmentStartMs = now) } },
        onPause = { newElapsed ->
            updateQuizTiming { it.copy(sessionActiveElapsedMs = newElapsed, sessionActiveSegmentStartMs = null) }
            applicationScope.launch { persistCurrentState() }
        }
    )

    // Same idea as sessionTiming, but for the current question — pauses on backgrounding just like
    // the session timer, instead of counting straight through time spent away (see restart()/
    // freeze(), used when a new question is shown / the current one is graded, versus resume()/
    // pause(), used only by wireForegroundTracking below for background/foreground transitions).
    private val questionTiming = QuizSessionTiming(
        onResume = { now -> updateQuizTiming { it.copy(questionActiveSegmentStartMs = now) } },
        onPause = { newElapsed -> updateQuizTiming { it.copy(questionActiveElapsedMs = newElapsed, questionActiveSegmentStartMs = null) } }
    )

    // Mirrors the settings collector below so gradeAnswer can read the autoplay/mp3-restriction
    // flags as a plain field instead of calling `settingsRepository.settings.first()` — starting a
    // fresh Flow collection (new coroutine, map{}, distinctUntilChanged()) on Main measured at
    // 40-70ms on a cold JIT (real device profiling, not Robolectric), sitting squarely inside the
    // ~250ms window between publishing feedback/rankChange and the RankChangeChip/IME-dismiss
    // animation actually running — dropping enough frames that the animation appeared to "snap"
    // rather than animate. AppSettings()'s defaults match SettingsRepository's DataStore defaults,
    // so the narrow window before this field's first real emission lands is harmless.
    override var latestSettings = AppSettings()

    init {
        loadOrResume()
        viewModelScope.launch {
            // Only kept warm for the ViewModel's own use (see latestSettings): the display flags it
            // used to mirror into the UI state are provided app-wide by LocalDisplaySettings instead,
            // so a settings change no longer re-emits a whole ReviewUiState.
            settingsRepository.settings.collect { latestSettings = it }
        }
        // The hint's pitch patterns are followed live rather than fetched once at grading time: the
        // repository's Room flow is the single source of truth, so whichever writer resolves the word
        // (this screen's own check, the detail sheet's) the hint updates in place with no propagation
        // code anywhere. flatMapLatest swaps the observation when the question changes, which also
        // cancels it outright when the key goes null.
        viewModelScope.launch {
            pitchAccentHintKey
                .flatMapLatest { key ->
                    key?.let { hint -> pitchAccentRepository.observePitchAccents(hint.characters).map { hint to it } }
                        ?: flowOf(null)
                }
                .collect { hint ->
                    updateQuiz {
                        val current = it.answerHint
                        // Drop anything that doesn't belong to the hint on screen right now — an
                        // emission can land after an undo or an advance, and must neither resurrect a
                        // stale word's patterns nor overwrite the next question's.
                        if (hint == null || it.currentItem.assignmentId != hint.first.assignmentId ||
                            current == null || current.reading != hint.first.reading || it.feedback == null
                        ) {
                            it.copy(answerHint = current?.copy(pitchAccents = PitchAccentUiState.Unavailable))
                        } else {
                            it.copy(answerHint = current.copy(pitchAccents = hint.second))
                        }
                    }
                }
        }
        // The initial value is handled by loadOrResume/sessionTiming.resume() below instead — see
        // QuizSessionTiming.wireForegroundTracking's doc comment.
        sessionTiming.wireForegroundTracking(viewModelScope, appForegroundTracker)
        questionTiming.wireForegroundTracking(viewModelScope, appForegroundTracker)
    }

    /** Resumes a persisted in-progress session if one exists, otherwise fetches a fresh queue. */
    override fun loadOrResume() {
        viewModelScope.launch {
            _uiState.update { ReviewUiState() }
            pitchAccentHintKey.value = null
            // Warmed once here, during the loading spinner, so every answer graded during this
            // session can compute its rank change synchronously — see
            // AssignmentRepository.computeReviewRankChange.
            assignmentRepository.warmSrsSystemCache()
            val persisted = sessionController.load()
            if (persisted != null) {
                resumeFromPersisted(persisted)
            } else {
                fetchFreshQueue()
            }
        }
    }

    private suspend fun fetchFreshQueue() {
        val result = syncOrchestrator.syncQueue()
        // Again after the sync, which may have been the first to bring SRS systems in.
        assignmentRepository.warmSrsSystemCache()
        when (result) {
            is ApiResult.Error -> {
                // Auth errors require user action (re-login) — surface them explicitly. Network
                // errors auto-fall back to cached data so the user can review without connectivity,
                // consistent with the dashboard's own offline behavior.
                if (result.isAuthError) {
                    _uiState.update { it.copy(phase = ReviewUiState.Phase.Error(result.message)) }
                } else {
                    buildQueue(assignmentRepository.observeReviewQueue().first())
                }
            }
            is ApiResult.Success -> buildQueue(assignmentRepository.observeReviewQueue().first())
        }
    }

    /** Bound to the error screen's "Study offline" action — builds the review queue from whatever
     *  was cached as of the last successful sync instead of retrying the network refresh that just
     *  failed in [fetchFreshQueue]. */
    override fun studyOffline() {
        viewModelScope.launch { buildQueue(assignmentRepository.observeReviewQueue().first()) }
    }

    private suspend fun resumeFromPersisted(persisted: PersistedReviewSession) {
        // Resolve exactly the assignments this persisted session references, by id — not via
        // observeReviewQueue()'s due filter. A fully-completed item's next-review time is pushed
        // into the future the moment it's finished (applyOptimisticReviewResult), so by the time
        // the user pauses and resumes it may no longer be "due" even though it's still part of
        // this session's progress tally.
        val neededIds = (
            persisted.queue.map { it.assignmentId } + persisted.reserve.map { it.assignmentId } +
                persisted.progress.map { it.assignmentId }
            ).toSet()
        val itemsById = assignmentRepository.getReviewItems(neededIds).associateBy { it.assignmentId }

        // A queued entry referencing an item we can no longer look up (e.g. app storage was cleared),
        // or carrying a question type this build no longer knows, is genuinely unrecoverable — fall
        // back to a fresh fetch rather than crash on that.
        val quiz = QuizSession.restore(
            itemsById = itemsById,
            inFlight = persisted.queue,
            reserve = persisted.reserve,
            progress = persisted.progress,
            answered = persisted.answeredQuestions,
            totalQuestions = persisted.totalQuestions
        )
        if (quiz == null) {
            sessionController.complete()
            fetchFreshQueue()
            return
        }
        session = ReviewSession(quiz, persisted.pendingSubmissionAssignmentId)
        // Restores the session's accumulated active time rather than restarting the clock — this is
        // deliberately *not* wall-clock time since the session began; time spent away (backgrounded,
        // or navigated off and back) must not count. sessionTiming.resume() then starts a fresh
        // viewing segment on top of that restored base, so the clock resumes right where it left off.
        sessionTiming.elapsedMs = persisted.sessionActiveElapsedMs
        sessionTiming.resume()
        sessionController.begin()
        advanceToNextQuestion()
    }

    private suspend fun buildQueue(items: List<ReviewItem>) {
        sessionTiming.elapsedMs = 0L
        sessionTiming.resume()

        // Progress is created as items are graded rather than seeded for every item: seeding a queue
        // of a few hundred due reviews would serialize every entry into the persisted snapshot after
        // each answer. The readers tolerate a missing entry — the summary counts only items with
        // progress, and wrap-up treats "no entry" as "never attempted".
        //
        // Read straight from the DataStore rather than the `latestSettings` field the per-answer
        // paths use: this runs once, during the loading spinner, so the extra read costs nothing,
        // and `latestSettings` isn't reliable here — loadOrResume() (called first in init) can build
        // this queue before the settings collector coroutine has taken its first emission.
        // The level is likewise fetched once per build (not per admission), matching
        // MAX_IN_FLIGHT_REVIEW_ITEMS's "sort once at session start" contract — an item that reaches
        // Guru mid-session keeps whatever admission order it was queued with rather than being
        // re-sorted out from under the reserve.
        val priority = settingsRepository.settings.first().reviewPriority
        val tierOf = ReviewPrioritizer.tierSelector(statsRepository.observeCurrentLevel().first())
        // The tier key, not a pre-sorted list: QuizQueue sorts the *whole* queue by tier, so the
        // reserve stays in priority order too and admitNext keeps feeding level-up kanji in as slots
        // free. DEFAULT passes no key at all, leaving build on its original shuffled-selection path.
        session = ReviewSession(
            QuizSession<ReviewItem>().withQuestionsFor(
                items,
                cap = MAX_IN_FLIGHT_REVIEW_ITEMS,
                priorityOf = if (priority == ReviewPriority.DEFAULT) null else tierOf
            )
        )

        if (session.quiz.isEmpty) {
            // Distinct from Phase.Complete — nothing was ever reviewed this visit, so there's no
            // summary to show. Mirrors LessonViewModel's NoLessonsAvailable, set in the same
            // fresh-fetch-came-back-empty spot (as opposed to advanceToNextQuestion, where the queue
            // draining to empty after real progress is a genuine completion).
            _uiState.update { it.copy(phase = ReviewUiState.Phase.NoReviewsAvailable) }
        } else {
            sessionController.begin()
            persistCurrentState()
            advanceToNextQuestion()
        }
    }



    /** The autoplay audio and reading/pitch-accent hint for a just-graded (and, if gated, now
     *  revealed) reading question — held back while a wrong answer's text is still gated behind
     *  "require tap to reveal answer" (see [gradeAnswer]/[revealAnswer]), so a learner can't hear or
     *  see the correct reading before choosing to look at the answer. */
    override fun publishReadingRevealEffects(item: ReviewItem, type: QuestionType, candidates: List<String>, settings: AppSettings) {
        if (type == QuestionType.READING && settings.autoplayPronunciationAudio) {
            candidates.firstOrNull()?.let { reading ->
                pronunciationAudioPlayer.playMatchingReading(item.pronunciationAudios, reading, mp3Only = settings.restrictAudioToMp3)
            }
        }

        // The reading and its audio are snapshots (subject audio can't change mid-session); the pitch
        // patterns are watched live off `pitchAccentHintKey` (see init), which is set *after* the
        // reading is published so the collector's first emission always finds it in place.
        val characters = item.characters
        if (type == QuestionType.READING && settings.showAnswerReadingPitchAccent && isPitchAccentEligible(item.subjectType) && characters != null) {
            val answerReading = item.readings.firstOrNull()
            // Selected here, where the item and the settings are already in hand, so the hint's row
            // only has to render whatever clip this produced — null when none survives the filter.
            val answerReadingAudio = answerReading?.let { reading ->
                selectAudioFor(item.pronunciationAudios, reading, mp3Only = settings.restrictAudioToMp3)
            }
            updateQuiz {
                it.copy(
                    answerHint = answerReading?.let { reading -> AnswerReadingHint(reading = reading, audio = answerReadingAudio) }
                )
            }
            pitchAccentHintKey.value = answerReading?.let { PitchAccentHintKey(item.assignmentId, characters, it) }
        }
    }

    override suspend fun gradeAnswer(
        item: ReviewItem,
        type: QuestionType,
        isCorrect: Boolean,
        candidates: List<String>,
        wasCloseMatch: Boolean,
        isGiveUp: Boolean
    ) {
        // Whether this grade is visible right away, or gated behind an explicit revealAnswer() tap —
        // see ReviewUiState.Phase.Active.answerRevealed. Computed once up front since both the
        // published feedback state and the reveal-effects gate below must agree on it. Meaning and
        // reading each have their own setting (requiresTapToRevealAnswer), so this can gate one
        // question type and not the other.
        val revealedNow = isCorrect || isGiveUp || !requiresTapToRevealAnswer(latestSettings, type)
        val questionElapsedMs = questionTiming.freeze()
        val quiz = session.quiz.grade(
            isCorrect = isCorrect,
            elapsedMs = questionElapsedMs,
            // An item with a still-pending sibling question type has that one pushed to the back, so
            // it isn't the entry most likely to be drawn again right away.
            deferSiblingOnCorrect = true,
            cap = MAX_IN_FLIGHT_REVIEW_ITEMS
        )
        val graded = quiz.lastGraded ?: return
        val grade = if (graded.completedItem) quiz.progress.getValue(item.assignmentId).toReviewGrade() else null
        // Only recorded as pending here — actually submitting to WaniKani (and bumping the local SRS
        // stage) waits for commitPendingSubmission, so the user can still undo a correct answer
        // before pressing Continue.
        session = ReviewSession(quiz, pendingSubmissionAssignmentId = grade?.let { item.assignmentId })

        // Whether this answer was the very last one due — if so, commitGradeDurably completes the
        // session outright instead of saving a snapshot of the now-empty queue.
        val queueIsEmpty = quiz.current == null
        val snapshot = session.toPersisted(sessionTiming.currentElapsedMs())

        // Computed synchronously against AssignmentRepository's in-memory SRS-system cache — no DB
        // access on this critical path. Purely a UI prediction; the actual DB write of the new stage
        // happens in commitPendingSubmission.
        val newRankChange = grade?.let { assignmentRepository.computeReviewRankChange(item, it)?.takeIf { rc -> rc.from != rc.to } }

        updateQuiz {
            it.copy(
                feedback = AnswerFeedback(isCorrect, candidates.joinToString(", "), wasCloseMatch, candidates.size),
                answerRevealed = revealedNow,
                remainingCount = quiz.remainingQuestions,
                rankChange = newRankChange ?: it.rankChange,
                // Freezes the "time on this question" display the instant feedback appears — the same
                // elapsedMs the slowest-answers summary records for this answer.
                timing = it.timing.copy(questionElapsedMs = questionElapsedMs, questionActiveSegmentStartMs = null)
            )
        }
        // Withheld entirely while gated (revealedNow == false) — a wrong reading answer's audio/hint
        // wait for revealAnswer() to trigger them instead, same as its answer text.
        if (revealedNow) {
            // Reads the field kept warm by the settings collector in init{} instead of
            // `settingsRepository.settings.first()` — see `latestSettings`'s doc comment for why a
            // fresh Flow collection here measurably janked the post-submit animation.
            publishReadingRevealEffects(item, type, candidates, latestSettings)
        }

        commitGradeDurably(snapshot, queueIsEmpty)
    }

    /** Reverts the most recent answer — a typo (incorrect) or a change of mind (correct, and not yet
     *  submitted to WaniKani — see [ReviewSession.pendingSubmissionAssignmentId]). */
    override fun undoLastAnswer() {
        val active = _uiState.value.phase as? ReviewUiState.Phase.Active ?: return
        val feedback = active.feedback ?: return

        viewModelScope.launch {
            val undone = session.quiz.undoLastGrade() ?: return@launch
            // Retracting a correct answer retracts the submission it made pending.
            session = ReviewSession(
                quiz = undone,
                pendingSubmissionAssignmentId = if (feedback.isCorrect) null else session.pendingSubmissionAssignmentId
            )
            persistCurrentState()
            // Restarts this question's clock so the retry's timing doesn't inherit time spent
            // before the undo.
            val questionStartedAt = questionTiming.restart()

            // undoCounter changes even though currentItem/currentQuestionType don't — this is what
            // the answer field's focus-restoring LaunchedEffect keys on, since undo doesn't change
            // either of those but still needs to refocus the field the user just tapped away from.
            updateQuiz {
                it.copy(
                    feedback = null,
                    // Undoing a correct answer retracts the rank change it predicted; an incorrect
                    // answer never had one, so this is a no-op in that branch.
                    rankChange = if (feedback.isCorrect) null else it.rankChange,
                    answerHint = null,
                    answerRevealed = true,
                    answerInput = "",
                    remainingCount = undone.remainingQuestions,
                    undoCounter = it.undoCounter + 1,
                    timing = it.timing.copy(
                        questionActiveElapsedMs = 0L,
                        questionActiveSegmentStartMs = questionStartedAt,
                        questionElapsedMs = null
                    )
                )
            }
            // The hint the answer revealed goes away with it.
            pitchAccentHintKey.value = null
        }
    }

    override fun onContinue() {
        viewModelScope.launch { advanceToNextQuestion() }
    }

    /** Stops introducing brand-new items; only the current item and ones already attempted remain.
     *  persistCurrentState()'s save is a no-op if the session already completed between the last
     *  question being graded and this menu action running — see QuizSessionController.persist(). */
    override fun wrapUp() {
        viewModelScope.launch {
            val quiz = session.quiz.wrappedUp()
            session = session.copy(quiz = quiz)
            persistCurrentState()
            updateQuiz { it.copy(isWrappingUp = true, totalCount = quiz.totalQuestions, remainingCount = quiz.remainingQuestions) }
        }
    }

    /** Discards progress on not-yet-submitted items and exits — a clean slate next time. A
     *  correct-but-not-yet-continued answer is committed first rather than discarded with the
     *  rest — the user already saw "Correct!" feedback for it, so it reads as finished to them,
     *  matching what the abandon confirmation dialog's copy promises ("this won't affect items
     *  you've already submitted"). */
    override fun abandonSession() {
        viewModelScope.launch {
            commitPendingSubmission()
            sessionController.abandon()
            _uiState.update { it.copy(isAbandoned = true) }
        }
    }

    /** Only items with progress count as reviewed: after [wrapUp] drops never-attempted items, counting
     *  them would overstate items reviewed and understate the average time per item. The average
     *  divides total active session time by distinct items reviewed. */
    private fun sessionSummary(): QuizSessionSummary<ReviewItem> =
        session.quiz.summary(sessionTiming.currentElapsedMs()) { it.hasAnyProgress }

    /** Snapshots a just-completed session's summary so it can be revisited later from the
     *  dashboard, after this ViewModel (and its otherwise-ephemeral session-complete state) is
     *  gone. Mirrors LessonViewModel.persistLastSessionSummary(). */
    private fun persistLastSessionSummary(summary: QuizSessionSummary<ReviewItem>) {
        applicationScope.launch {
            lastSessionSummaryRepository.save(
                summary.toLastSessionSummary(
                    kind = LastSessionKind.REVIEW,
                    completedAtMillis = Clock.System.now().toEpochMilliseconds()
                )
            )
        }
    }

    private suspend fun advanceToNextQuestion() {
        // Actually submits a fully-done item's grade to WaniKani — see
        // pendingSubmissionAssignmentId's doc comment for why this is deferred to here rather than
        // done at grading time. Also runs when a resumed session carried a pending submission across
        // (process death, or navigating away and back), treating that the same as an implicit
        // Continue, since undo only works on the live in-memory session.
        commitPendingSubmission()
        val next = session.quiz.current
        if (next == null) {
            sessionController.complete()
            outboxRepository.requestSyncNow()
            val summary = sessionSummary()
            persistLastSessionSummary(summary)
            // Nothing is on screen to check any more, so the hint goes with the session.
            pitchAccentHintKey.value = null
            _uiState.update { it.copy(phase = summary.toCompletePhase()) }
            return
        }
        val questionStartedAt = questionTiming.restart()
        // The new question owns neither the previous one's hint — a stale word's patterns must not
        // leak into this question's caption.
        pitchAccentHintKey.value = null
        _uiState.update {
            val previousPhase = it.phase as? ReviewUiState.Phase.Active
            it.copy(
                phase = ReviewUiState.Phase.Active(
                    currentItem = next.item,
                    currentQuestionType = next.type,
                    totalCount = session.quiz.totalQuestions,
                    remainingCount = session.quiz.remainingQuestions,
                    questionSequence = (previousPhase?.questionSequence ?: 0) + 1,
                    timing = QuizTimingUiState(
                        sessionActiveElapsedMs = sessionTiming.elapsedMs,
                        sessionActiveSegmentStartMs = sessionTiming.segmentStartMs,
                        questionActiveSegmentStartMs = questionStartedAt
                    )
                )
            )
        }
    }

    private suspend fun persistCurrentState() {
        sessionController.persist(session.toPersisted(sessionTiming.currentElapsedMs()))
    }

    /** Runs the post-grading durability write (session persistence only — see
     *  [commitPendingSubmission] for the outbox enqueue/SRS bump, deferred separately until
     *  Continue). Completes the session instead of saving [snapshot] when [queueIsEmpty] — this was
     *  the last due question, so [snapshot] is already an empty-queue shell that
     *  advanceToNextQuestion's own completion would just overwrite once the user taps Continue; not
     *  saving it in the first place is simpler than saving it and relying on a later completion to
     *  overwrite it. The one exception is a still-pending submission: that grade hasn't reached the
     *  outbox yet (see [commitPendingSubmission]), so completing here would lose it outright if the
     *  process dies before Continue — persist the (empty-queue) snapshot instead so resuming can
     *  still recover and commit it (see [ReviewSessionRepository.load]'s matching resumability
     *  check for this same exception on the read side). */
    private suspend fun commitGradeDurably(snapshot: PersistedReviewSession, queueIsEmpty: Boolean) {
        if (queueIsEmpty && snapshot.pendingSubmissionAssignmentId == null) {
            sessionController.complete()
        } else {
            sessionController.persist(snapshot)
        }
    }

    /** Actually submits a correctly-answered, fully-done item's grade to WaniKani (outbox enqueue +
     *  local SRS-stage bump) — deferred here from [gradeAnswer] so pressing Continue (via
     *  [advanceToNextQuestion]) is what commits it, keeping the answer undoable up to that point.
     *  No-ops if nothing is pending — e.g. the last-graded answer was incorrect, or this was already
     *  committed. */
    private suspend fun commitPendingSubmission() {
        val assignmentId = session.pendingSubmissionAssignmentId ?: return
        val progress = session.quiz.progress[assignmentId] ?: return
        session = session.copy(pendingSubmissionAssignmentId = null)
        val item = progress.item
        val grade = progress.toReviewGrade()
        // Durable against this ViewModel being cleared mid-write, via applicationScope rather than
        // viewModelScope — doesn't need session-write ordering (it never touches the session
        // repository), only survival past teardown, so it uses runDurably rather than going through
        // sessionController.
        applicationScope.runDurably {
            // The actual DB write of the new SRS stage — already reflected in the UI via the
            // synchronous computeReviewRankChange prediction in gradeAnswer, so this just makes the
            // local cache catch up. Recomputes from a fresh DB read rather than trusting the
            // in-memory item, so a concurrent change elsewhere still wins.
            assignmentRepository.applyOptimisticReviewResult(item.assignmentId, item.srsSystemId, grade)
            outboxRepository.enqueueReviewSubmission(item.assignmentId, item.subjectId, grade)
            statsRepository.markStudyActivityToday()
        }
    }
}
