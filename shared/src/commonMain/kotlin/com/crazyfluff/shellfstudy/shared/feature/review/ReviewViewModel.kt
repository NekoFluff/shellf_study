package com.crazyfluff.shellfstudy.shared.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.audio.playMatchingReading
import com.crazyfluff.shellfstudy.shared.audio.selectAudioFor
import com.crazyfluff.shellfstudy.shared.coroutines.runDurably
import com.crazyfluff.shellfstudy.shared.data.LastSessionKind
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummary
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import com.crazyfluff.shellfstudy.shared.data.PersistedAnsweredQuestion
import com.crazyfluff.shellfstudy.shared.data.PersistedItemProgress
import com.crazyfluff.shellfstudy.shared.data.PersistedQuestion
import com.crazyfluff.shellfstudy.shared.data.PersistedReviewSession
import com.crazyfluff.shellfstudy.shared.lifecycle.AppForegroundTracker
import com.crazyfluff.shellfstudy.shared.quiz.AnsweredQuestionRecord
import com.crazyfluff.shellfstudy.shared.quiz.QuizItemProgress
import com.crazyfluff.shellfstudy.shared.quiz.AnswerFeedback
import com.crazyfluff.shellfstudy.shared.quiz.AnswerOutcome
import com.crazyfluff.shellfstudy.shared.quiz.PendingQuestion
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType
import com.crazyfluff.shellfstudy.shared.quiz.QuizGradingGuard
import com.crazyfluff.shellfstudy.shared.quiz.QuizQueue
import com.crazyfluff.shellfstudy.shared.quiz.QuizSessionSummary
import com.crazyfluff.shellfstudy.shared.quiz.QuizSessionTiming
import com.crazyfluff.shellfstudy.shared.quiz.QuizTimingUiState
import com.crazyfluff.shellfstudy.shared.quiz.SlowAnswer
import com.crazyfluff.shellfstudy.shared.quiz.candidatesFor
import com.crazyfluff.shellfstudy.shared.quiz.evaluateAnswer
import com.crazyfluff.shellfstudy.shared.quiz.isPitchAccentEligible
import com.crazyfluff.shellfstudy.shared.quiz.questionTypesFor
import com.crazyfluff.shellfstudy.shared.quiz.requiresTapToRevealAnswer
import com.crazyfluff.shellfstudy.shared.quiz.summarizeQuizSession
import com.crazyfluff.shellfstudy.shared.quiz.toSessionAnswerRow
import com.crazyfluff.shellfstudy.shared.quiz.toSessionMissedItemRow
import com.crazyfluff.shellfstudy.shared.quiz.undoLastCorrectAnswer
import com.crazyfluff.shellfstudy.shared.quiz.undoLastIncorrectAnswer
import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.isAuthError
import com.crazyfluff.shellfstudy.shared.data.AppSettings
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.PitchAccentRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.model.RankChange
import com.crazyfluff.shellfstudy.shared.data.model.ReviewGrade
import com.crazyfluff.shellfstudy.shared.data.model.ReviewItem
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.AnswerReadingHint
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentUiState
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.crazyfluff.shellfstudy.shared.session.ReviewSessionController
import kotlin.time.Clock
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
    // Deliberately not folded into Phase — see LessonUiState.isAbandoned's doc comment for why.
    val isAbandoned: Boolean = false
) {
    sealed interface Phase {
        data object Loading : Phase
        data class Error(val message: String) : Phase
        data object NoReviewsAvailable : Phase

        data class Active(
            // Non-nullable by construction — see LessonUiState.Phase.Quiz's doc comment for why.
            val currentItem: ReviewItem,
            val currentQuestionType: QuestionType,
            val answerInput: String = "",
            val feedback: AnswerFeedback? = null,
            val rankChange: RankChange? = null,
            val undoCounter: Int = 0,
            // Bumped on every advance to a new current question, even a requeued one that repeats
            // the same item/type — see QuizQuestionContent's focusResetKey, which needs a signal
            // that's guaranteed to change on advance regardless of whether the question repeats.
            val questionSequence: Int = 0,
            val isDetailsExpanded: Boolean = false,
            val answerTypeMismatchCount: Int = 0,
            val totalCount: Int = 0,
            val remainingCount: Int = 0,
            // A modifier within the active variant, not a separate mode — wrapUp() changes what
            // happens to the queue, but the screen still renders exactly the same question UI either
            // way, so this doesn't warrant its own Phase (unlike Lesson's Select/Study/Quiz, which
            // really are different rendering modes).
            val isWrappingUp: Boolean = false,
            val timing: QuizTimingUiState = QuizTimingUiState(),
            // Whether the correct-answer text is visible for the current (wrong) feedback. Always
            // true except right after a wrong submit while the "require tap to reveal answer"
            // setting is on — see gradeAnswer/revealAnswer. Give-ups and correct/close-match answers
            // are never gated, so this stays true for them regardless of the setting.
            val answerRevealed: Boolean = true,
            // The reading + pitch-accent patterns + audio for the just-graded reading question. The
            // reading/audio are published with the feedback (see gradeAnswer); the pitch patterns are
            // *observed* live for as long as this question is the current one (see
            // pitchAccentHintKey/init), so an update from any writer to the bundled dictionary's
            // cache lands here without a refetch.
            val answerHint: AnswerReadingHint? = null
        ) : Phase

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

private typealias ItemProgress = QuizItemProgress<ReviewItem>

/** How many distinct items can be in flight (started but not yet fully answered) at once — matches
 *  WaniKani's own review session, which stops introducing new items once 10 are already being
 *  worked on. Fixed rather than a setting, same as upstream. See ReviewViewModel.admitNextQuestion. */
private const val MAX_IN_FLIGHT_REVIEW_ITEMS = 10

/** Shared by [ReviewViewModel.gradeAnswer]'s synchronous rank-change prediction and
 *  [ReviewViewModel.commitPendingSubmission]'s later, authoritative recomputation — keeps the two
 *  from drifting if the grading formula ever changes. */
private fun ItemProgress.toReviewGrade(): ReviewGrade =
    ReviewGrade(meaningCorrect = !hadIncorrectMeaning, readingCorrect = !hadIncorrectReading)

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
    private val pronunciationAudioPlayer: PronunciationAudioPlayer,
    private val settingsRepository: SettingsRepository,
    private val pitchAccentRepository: PitchAccentRepository,
    private val appForegroundTracker: AppForegroundTracker,
    private val applicationScope: CoroutineScope
) : ViewModel(), ReviewActions {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    /** The word the current question's reading hint is watching — see [PitchAccentHintKey]. A plain
     *  field rather than part of [ReviewUiState] because the screen never reads the key itself; it
     *  only ever sees the [PitchAccentUiState] the collector below derives from it. */
    private val pitchAccentHintKey = MutableStateFlow<PitchAccentHintKey?>(null)

    private val queue = QuizQueue<ReviewItem>()
    private val progressByAssignmentId = mutableMapOf<Long, ItemProgress>()
    private var totalQuestions = 0

    // The assignment whose grade is graded-correct-but-not-yet-submitted — set by gradeAnswer when
    // an item becomes fully done, cleared by commitPendingSubmission (on Continue, or on resuming a
    // session that carried one across) or by undoLastAnswer (retracting it instead). At most one can
    // exist at a time: submitAnswer/dontKnowAnswer both refuse to grade while feedback is showing, so
    // the previous pending submission is always resolved before a new one can be created.
    private var pendingSubmissionAssignmentId: Long? = null

    private val gradingGuard = QuizGradingGuard(viewModelScope)

    // Individual per-answer records, used for the "slowest answers" summary — persisted and
    // restored across a resume just like progressByAssignmentId (see resumeFromPersisted), so the
    // summary reflects the whole session, not just the segment since the most recent resume.
    private val answeredQuestions = mutableListOf<AnsweredQuestionRecord<ReviewItem>>()

    // Tracks only the time the session was actively being viewed — see QuizSessionTiming. A
    // completed/abandoned/empty-queue session is handled structurally by sessionController.persist()
    // itself (a no-op once the session isn't ACTIVE), not by a flag check here — see
    // QuizSessionController. Runs on applicationScope rather than viewModelScope so this flush
    // actually executes when triggered from onCleared() (viewModelScope is cancelled just before
    // onCleared() runs, so a viewModelScope.launch here would silently never execute).
    private val sessionTiming = QuizSessionTiming(
        onResume = { now -> updateActiveTiming { it.copy(sessionActiveSegmentStartMs = now) } },
        onPause = { newElapsed ->
            updateActiveTiming { it.copy(sessionActiveElapsedMs = newElapsed, sessionActiveSegmentStartMs = null) }
            applicationScope.launch { persistCurrentState() }
        }
    )

    // Same idea as sessionTiming, but for the current question — pauses on backgrounding just like
    // the session timer, instead of counting straight through time spent away (see restart()/
    // freeze(), used when a new question is shown / the current one is graded, versus resume()/
    // pause(), used only by wireForegroundTracking below for background/foreground transitions).
    private val questionTiming = QuizSessionTiming(
        onResume = { now -> updateActiveTiming { it.copy(questionActiveSegmentStartMs = now) } },
        onPause = { newElapsed -> updateActiveTiming { it.copy(questionActiveElapsedMs = newElapsed, questionActiveSegmentStartMs = null) } }
    )

    // Mirrors the settings collector below so gradeAnswer can read the autoplay/mp3-restriction
    // flags as a plain field instead of calling `settingsRepository.settings.first()` — starting a
    // fresh Flow collection (new coroutine, map{}, distinctUntilChanged()) on Main measured at
    // 40-70ms on a cold JIT (real device profiling, not Robolectric), sitting squarely inside the
    // ~250ms window between publishing feedback/rankChange and the RankChangeChip/IME-dismiss
    // animation actually running — dropping enough frames that the animation appeared to "snap"
    // rather than animate. AppSettings()'s defaults match SettingsRepository's DataStore defaults,
    // so the narrow window before this field's first real emission lands is harmless.
    private var latestSettings = AppSettings()

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
                    updateActive {
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

    /** Guard-clause helper for updates that only apply while in the Active phase — a safe cast plus
     *  a no-op fallback, not `!!`/unchecked cast. */
    private inline fun updateActive(transform: (ReviewUiState.Phase.Active) -> ReviewUiState.Phase.Active) {
        _uiState.update { state ->
            val active = state.phase as? ReviewUiState.Phase.Active ?: return@update state
            state.copy(phase = transform(active))
        }
    }

    private inline fun updateActiveTiming(transform: (QuizTimingUiState) -> QuizTimingUiState) {
        updateActive { it.copy(timing = transform(it.timing)) }
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
        when (val result = assignmentRepository.refreshReviewQueue()) {
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
        val neededIds = (persisted.queue.map { it.assignmentId } + persisted.progress.map { it.assignmentId }).toSet()
        val itemsById = assignmentRepository.getReviewItems(neededIds).associateBy { it.assignmentId }

        // A *queue* entry referencing an item we can no longer look up (e.g. app storage was
        // cleared) is genuinely unrecoverable — rebuilding its PendingQuestion needs the full
        // ReviewItem. Fall back to a fresh fetch rather than crash on that.
        if (persisted.queue.any { it.assignmentId !in itemsById }) {
            sessionController.complete()
            fetchFreshQueue()
            return
        }

        queue.restore(
            persisted.queue.map { entry ->
                PendingQuestion(itemsById.getValue(entry.assignmentId), QuestionType.valueOf(entry.questionType))
            }
        )
        progressByAssignmentId.clear()
        persisted.progress.forEach { p ->
            // itemsById was resolved by id above, so this only misses for the same
            // genuinely-unrecoverable case handled above — not merely "no longer due".
            val item = itemsById[p.assignmentId] ?: return@forEach
            progressByAssignmentId[p.assignmentId] = ItemProgress(item).apply {
                meaningDone = p.meaningDone
                readingDone = p.readingDone
                hadIncorrectMeaning = p.hadIncorrectMeaning
                hadIncorrectReading = p.hadIncorrectReading
            }
        }
        totalQuestions = persisted.totalQuestions
        pendingSubmissionAssignmentId = persisted.pendingSubmissionAssignmentId
        answeredQuestions.clear()
        answeredQuestions.addAll(
            persisted.answeredQuestions.mapNotNull { p ->
                val item = itemsById[p.assignmentId] ?: return@mapNotNull null
                AnsweredQuestionRecord(item, QuestionType.valueOf(p.questionType), p.isCorrect, p.elapsedMs)
            }
        )
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
        queue.clear()
        progressByAssignmentId.clear()
        answeredQuestions.clear()
        sessionTiming.elapsedMs = 0L
        sessionTiming.resume()

        items.forEach { item -> progressByAssignmentId[item.assignmentId] = ItemProgress(item) }
        queue.build(items, typesFor = { item -> questionTypesFor(item.subjectType) })
        totalQuestions = queue.size

        if (queue.isEmpty) {
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

    override fun onAnswerInputChange(value: String) {
        updateActive { it.copy(answerInput = value) }
    }

    override fun toggleDetails() {
        updateActive { it.copy(isDetailsExpanded = !it.isDetailsExpanded) }
    }

    /** Unlike [toggleDetails] (a real flip, driven by the swipe handle/gesture-settle sync), this is
     *  the definitively-directional close used by the scrim tap, the close button, and the back
     *  handler — those always mean "close", never "toggle", so they must not risk re-opening the
     *  sheet if called while it's already collapsed. */
    override fun closeDetails() {
        updateActive { it.copy(isDetailsExpanded = false) }
    }

    override fun submitAnswer() {
        val active = _uiState.value.phase as? ReviewUiState.Phase.Active ?: return
        if (active.feedback != null) return
        val item = active.currentItem
        val type = active.currentQuestionType
        if (active.answerInput.isBlank()) return

        gradingGuard.launchIfIdle {
            val candidates = candidatesFor(item.meanings, item.auxiliaryMeanings, item.readings, type)
            val outcome = evaluateAnswer(
                active.answerInput, type, item.meanings, item.auxiliaryMeanings, item.readings,
                closeEnoughEnabled = latestSettings.closeEnoughAnswersEnabled
            )
            when (outcome) {
                AnswerOutcome.TypeMismatch ->
                    updateActive { it.copy(answerTypeMismatchCount = it.answerTypeMismatchCount + 1) }
                is AnswerOutcome.Graded ->
                    gradeAnswer(
                        item, type, outcome.isCorrect, candidates, expandDetails = false,
                        wasCloseMatch = outcome.wasCloseMatch
                    )
            }
        }
    }

    /** Gives up on the current question — grades it as a miss without requiring a typed guess. */
    override fun dontKnowAnswer() {
        val active = _uiState.value.phase as? ReviewUiState.Phase.Active ?: return
        if (active.feedback != null) return
        val item = active.currentItem
        val type = active.currentQuestionType

        gradingGuard.launchIfIdle {
            val candidates = candidatesFor(item.meanings, item.auxiliaryMeanings, item.readings, type)
            gradeAnswer(item, type, isCorrect = false, candidates, expandDetails = false, isGiveUp = true)
        }
    }

    /** Reveals a gated wrong answer's correct-answer text — see [ReviewUiState.Phase.Active.answerRevealed].
     *  No-op if there's nothing gated right now (already revealed, or no feedback showing). Doesn't
     *  need [gradingGuard]: it mutates only display state, not grading/SRS state — but a reading
     *  question's audio/pitch-accent hint were themselves withheld at grading time (see
     *  [publishReadingRevealEffects]), so revealing now is what actually triggers them. */
    override fun revealAnswer() {
        val active = _uiState.value.phase as? ReviewUiState.Phase.Active ?: return
        if (active.feedback == null || active.answerRevealed) return
        updateActive { it.copy(answerRevealed = true) }
        val item = active.currentItem
        val type = active.currentQuestionType
        val candidates = candidatesFor(item.meanings, item.auxiliaryMeanings, item.readings, type)
        publishReadingRevealEffects(item, type, candidates, latestSettings)
    }

    /** The autoplay audio and reading/pitch-accent hint for a just-graded (and, if gated, now
     *  revealed) reading question — held back while a wrong answer's text is still gated behind
     *  "require tap to reveal answer" (see [gradeAnswer]/[revealAnswer]), so a learner can't hear or
     *  see the correct reading before choosing to look at the answer. */
    private fun publishReadingRevealEffects(item: ReviewItem, type: QuestionType, candidates: List<String>, settings: AppSettings) {
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
            updateActive {
                it.copy(
                    answerHint = answerReading?.let { reading -> AnswerReadingHint(reading = reading, audio = answerReadingAudio) }
                )
            }
            pitchAccentHintKey.value = answerReading?.let { PitchAccentHintKey(item.assignmentId, characters, it) }
        }
    }

    private suspend fun gradeAnswer(
        item: ReviewItem,
        type: QuestionType,
        isCorrect: Boolean,
        candidates: List<String>,
        expandDetails: Boolean,
        wasCloseMatch: Boolean = false,
        isGiveUp: Boolean = false
    ) {
        // Whether this grade is visible right away, or gated behind an explicit revealAnswer() tap —
        // see ReviewUiState.Phase.Active.answerRevealed. Computed once up front since both the
        // published feedback state and the reveal-effects gate below must agree on it. Meaning and
        // reading each have their own setting (requiresTapToRevealAnswer), so this can gate one
        // question type and not the other.
        val revealedNow = isCorrect || isGiveUp || !requiresTapToRevealAnswer(latestSettings, type)
        val (snapshot, queueIsEmpty) = run {
            val itemProgress = progressByAssignmentId.getOrPut(item.assignmentId) { ItemProgress(item) }
            val questionElapsedMs = questionTiming.freeze()
            answeredQuestions.add(AnsweredQuestionRecord(item, type, isCorrect, questionElapsedMs))

            queue.removeCurrent()
            if (isCorrect) {
                when (type) {
                    QuestionType.MEANING -> itemProgress.meaningDone = true
                    QuestionType.READING -> itemProgress.readingDone = true
                }
            } else {
                when (type) {
                    QuestionType.MEANING -> itemProgress.recordIncorrectMeaning()
                    QuestionType.READING -> itemProgress.recordIncorrectReading()
                }
                queue.requeue(PendingQuestion(item, type))
            }

            // Whether this answer was the very last one due — if so, commitGradeDurably completes
            // the session outright instead of saving a snapshot of the now-empty queue. That snapshot
            // would only ever get overwritten by advanceToNextQuestion's own completion once the user
            // taps Continue anyway; not writing it in the first place, right when the queue empties,
            // is simpler and safer than writing it and relying on a later completion to overwrite it.
            val queueIsEmpty = queue.current == null

            val grade = if (isCorrect && isFullyDone(item, itemProgress)) itemProgress.toReviewGrade() else null
            // Only recorded as pending here — actually submitting to WaniKani (and bumping the local
            // SRS stage) is deferred to commitPendingSubmission, so the user can still undo a correct
            // answer before pressing Continue. See pendingSubmissionAssignmentId's doc comment.
            pendingSubmissionAssignmentId = grade?.let { item.assignmentId }

            // Snapshotted synchronously, right after mutating the queue/progress/pending-submission
            // state above, so the detached durability write below can safely run concurrently with
            // the next question's own grading/advance — queue/progressByAssignmentId are plain,
            // non-thread-safe collections, and once feedback is visible the user is free to act
            // immediately.
            val snapshot = currentPersistSnapshot()

            // Computed synchronously against AssignmentRepository's in-memory SRS-system cache
            // (warmed once when the queue loaded) — zero DB access on this critical path at all now.
            // This is purely a UI prediction; the actual DB write of the new stage happens later, in
            // commitPendingSubmission.
            val newRankChange = grade?.let { assignmentRepository.computeReviewRankChange(item, it)?.takeIf { rc -> rc.from != rc.to } }

            updateActive {
                it.copy(
                    feedback = AnswerFeedback(isCorrect, candidates.joinToString(", "), wasCloseMatch, candidates.size),
                    answerRevealed = revealedNow,
                    remainingCount = queue.size,
                    isDetailsExpanded = it.isDetailsExpanded || expandDetails,
                    rankChange = newRankChange ?: it.rankChange,
                    // Freezes the "time on this question" display the instant feedback appears,
                    // rather than letting it keep ticking while the feedback/Continue screen is up
                    // — matches the elapsedMs recorded for the slowest-answers summary above, which
                    // is stamped at this same moment.
                    timing = it.timing.copy(questionElapsedMs = questionElapsedMs, questionActiveSegmentStartMs = null)
                )
            }

            snapshot to queueIsEmpty
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
     *  submitted to WaniKani — see [pendingSubmissionAssignmentId]). The queue/progress mutation
     *  itself is shared via [undoLastIncorrectAnswer]/[undoLastCorrectAnswer]. */
    override fun undoLastAnswer() {
        val active = _uiState.value.phase as? ReviewUiState.Phase.Active ?: return
        val item = active.currentItem
        val type = active.currentQuestionType
        val feedback = active.feedback ?: return

        viewModelScope.launch {
            // Cleared before persist() (called at the end of the undo functions below) so the
            // snapshot it saves doesn't resurrect a submission this undo just retracted.
            if (feedback.isCorrect) pendingSubmissionAssignmentId = null

            val didUndo = if (feedback.isCorrect) {
                undoLastCorrectAnswer(
                    queue = queue,
                    progressByAssignmentId = progressByAssignmentId,
                    answeredQuestions = answeredQuestions,
                    item = item,
                    questionType = type,
                    persist = { persistCurrentState() }
                )
            } else {
                undoLastIncorrectAnswer(
                    queue = queue,
                    progressByAssignmentId = progressByAssignmentId,
                    answeredQuestions = answeredQuestions,
                    item = item,
                    questionType = type,
                    persist = { persistCurrentState() }
                )
            }
            if (!didUndo) return@launch
            // Restarts this question's clock so the retry's timing doesn't inherit time spent
            // before the undo.
            val questionStartedAt = questionTiming.restart()

            // undoCounter changes even though currentItem/currentQuestionType don't — this is what
            // the answer field's focus-restoring LaunchedEffect keys on, since undo doesn't change
            // either of those but still needs to refocus the field the user just tapped away from.
            updateActive {
                it.copy(
                    feedback = null,
                    // Undoing a correct answer retracts the rank change it predicted; an incorrect
                    // answer never had one, so this is a no-op in that branch.
                    rankChange = if (feedback.isCorrect) null else it.rankChange,
                    answerHint = null,
                    answerRevealed = true,
                    answerInput = "",
                    remainingCount = queue.size,
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

    private fun isFullyDone(item: ReviewItem, progress: ItemProgress): Boolean {
        val requiresReading = item.subjectType != SubjectType.RADICAL && item.subjectType != SubjectType.KANA_VOCABULARY
        return progress.meaningDone && (!requiresReading || progress.readingDone)
    }

    /** Enforces [MAX_IN_FLIGHT_REVIEW_ITEMS] on whatever advanceToNextQuestion is about to draw next
     *  — recomputed from progressByAssignmentId every time rather than tracked as separate state, so
     *  a resumed session re-derives the same in-flight set the paused one had for free. "In flight"
     *  means started (hasAnyProgress) but not yet finished (isFullyDone) — a completed item frees its
     *  slot even though its progress entry is never cleared. */
    private fun admitNextQuestionWithinCap() {
        val inFlightCount = progressByAssignmentId.values.count { it.hasAnyProgress && !isFullyDone(it.item, it) }
        queue.capInFlight(
            isStarted = { item -> progressByAssignmentId[item.assignmentId]?.hasAnyProgress == true },
            inFlightCount = inFlightCount,
            cap = MAX_IN_FLIGHT_REVIEW_ITEMS
        )
    }

    override fun onContinue() {
        viewModelScope.launch { advanceToNextQuestion() }
    }

    /** Stops introducing brand-new items; only the current item and ones already attempted remain.
     *  persistCurrentState()'s save is a no-op if the session already completed between the last
     *  question being graded and this menu action running — see QuizSessionController.persist(). */
    override fun wrapUp() {
        viewModelScope.launch {
            val currentAssignmentId = queue.current?.item?.assignmentId
            queue.retainCurrentAndMatching {
                progressByAssignmentId[it.item.assignmentId]?.hasAnyProgress == true ||
                    it.item.assignmentId == currentAssignmentId
            }
            totalQuestions = queue.size + completedQuestionCount()

            persistCurrentState()
            updateActive { it.copy(isWrappingUp = true, totalCount = totalQuestions, remainingCount = queue.size) }
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

    private fun completedQuestionCount(): Int =
        progressByAssignmentId.values.sumOf { (if (it.meaningDone) 1 else 0) + (if (it.readingDone) 1 else 0) }

    override fun onCleared() {
        super.onCleared()
        sessionTiming.pause()
        pronunciationAudioPlayer.stop()
    }

    /** Only counts items with [QuizItemProgress.hasAnyProgress] — progressByAssignmentId is seeded
     *  with an entry for every item in the original queue up front (see buildQueue), so after a
     *  wrapUp() drops never-attempted items from the queue, their still-present-but-untouched
     *  entries here must not be counted as "reviewed", or this would overcount items reviewed and,
     *  in turn, understate the average time spent per item actually reviewed. The average divides
     *  total wall-clock session time (start to finish, including feedback screens and rank-change
     *  animations between questions) by the count of distinct items reviewed — that's what a user
     *  actually means by "average time per item." Mirrors LessonViewModel.sessionSummary(). */
    private fun sessionSummary(): QuizSessionSummary<ReviewItem> {
        val reviewedProgress = progressByAssignmentId.values.filter { it.hasAnyProgress }
        return summarizeQuizSession(reviewedProgress, answeredQuestions, sessionTiming.currentElapsedMs())
    }

    /** Snapshots a just-completed session's summary so it can be revisited later from the
     *  dashboard, after this ViewModel (and its otherwise-ephemeral session-complete state) is
     *  gone. Mirrors LessonViewModel.persistLastSessionSummary(). */
    private fun persistLastSessionSummary(summary: QuizSessionSummary<ReviewItem>) {
        applicationScope.launch {
            lastSessionSummaryRepository.save(
                LastSessionSummary(
                    kind = LastSessionKind.REVIEW,
                    itemsCount = summary.itemsCount,
                    correctFirstTry = summary.correctFirstTry,
                    totalElapsedMs = summary.totalElapsedMs,
                    averageTimePerItemMs = summary.averageTimePerItemMs,
                    slowestAnswers = summary.slowestAnswers.map { it.toSessionAnswerRow() },
                    missedItems = summary.missedItems.map { it.toSessionMissedItemRow() },
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
        admitNextQuestionWithinCap()
        val next = queue.current
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
                    totalCount = totalQuestions,
                    remainingCount = queue.size,
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

    /** Captures the current queue/progress as an immutable, ready-to-persist value — safe to hold
     *  across a suspension point even if the live queue/progressByAssignmentId are mutated by
     *  something else afterward (see [gradeAnswer]'s deferred [commitGradeDurably] call). Folds
     *  in the currently-running viewing segment (if any) rather than the possibly-stale
     *  [activeElapsedMs] alone, so an abrupt process death loses at most the time since this
     *  snapshot, not the whole segment since the last pause. */
    private fun currentPersistSnapshot(): PersistedReviewSession = PersistedReviewSession(
        queue = queue.toList().map { PersistedQuestion(it.item.assignmentId, it.type.name) },
        progress = progressByAssignmentId.map { (id, p) ->
            PersistedItemProgress(id, p.meaningDone, p.readingDone, p.hadIncorrectMeaning, p.hadIncorrectReading)
        },
        totalQuestions = totalQuestions,
        sessionActiveElapsedMs = sessionTiming.currentElapsedMs(),
        answeredQuestions = answeredQuestions.map {
            PersistedAnsweredQuestion(it.item.assignmentId, it.type.name, it.isCorrect, it.elapsedMs)
        },
        pendingSubmissionAssignmentId = pendingSubmissionAssignmentId
    )

    private suspend fun persistCurrentState() {
        sessionController.persist(currentPersistSnapshot())
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
        val assignmentId = pendingSubmissionAssignmentId ?: return
        val progress = progressByAssignmentId[assignmentId] ?: return
        pendingSubmissionAssignmentId = null
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
