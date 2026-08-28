package com.crazyfluff.shellfstudy.shared.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.audio.selectAudioFor
import com.crazyfluff.shellfstudy.shared.coroutines.SerialDurableWork
import com.crazyfluff.shellfstudy.shared.data.LastSessionKind
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummary
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import com.crazyfluff.shellfstudy.shared.data.PersistedAnsweredQuestion
import com.crazyfluff.shellfstudy.shared.data.PersistedItemProgress
import com.crazyfluff.shellfstudy.shared.data.PersistedQuestion
import com.crazyfluff.shellfstudy.shared.data.PersistedReviewSession
import com.crazyfluff.shellfstudy.shared.data.ReviewSessionRepository
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
import com.crazyfluff.shellfstudy.shared.quiz.SlowAnswer
import com.crazyfluff.shellfstudy.shared.quiz.candidatesFor
import com.crazyfluff.shellfstudy.shared.quiz.evaluateAnswer
import com.crazyfluff.shellfstudy.shared.quiz.questionTypesFor
import com.crazyfluff.shellfstudy.shared.quiz.summarizeQuizSession
import com.crazyfluff.shellfstudy.shared.quiz.toSessionAnswerRow
import com.crazyfluff.shellfstudy.shared.quiz.toSessionMissedItemRow
import com.crazyfluff.shellfstudy.shared.quiz.undoLastIncorrectAnswer
import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.AppSettings
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.model.RankChange
import com.crazyfluff.shellfstudy.shared.data.model.ReviewGrade
import com.crazyfluff.shellfstudy.shared.data.model.ReviewItem
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val totalCount: Int = 0,
    val remainingCount: Int = 0,
    val currentItem: ReviewItem? = null,
    val currentQuestionType: QuestionType? = null,
    val answerInput: String = "",
    val feedback: AnswerFeedback? = null,
    val rankChange: RankChange? = null,
    val undoCounter: Int = 0,
    val isSessionComplete: Boolean = false,
    val hasNoReviewsAvailable: Boolean = false,
    val isAbandoned: Boolean = false,
    val isWrappingUp: Boolean = false,
    val isDetailsExpanded: Boolean = false,
    val sessionItemsReviewed: Int = 0,
    val sessionItemsCorrectFirstTry: Int = 0,
    val answerTypeMismatchCount: Int = 0,
    val showSubjectTypeLabel: Boolean = false,
    val showTotalTimer: Boolean = false,
    val showQuestionTimer: Boolean = false,
    val useJapaneseKeyboard: Boolean = false,
    // Active time accumulated before the current viewing segment, and (while non-null) when that
    // segment began — see PausableElapsedTimeText and ReviewViewModel's activeElapsedMs /
    // activeSegmentStartMs, which these mirror exactly. Segment goes null while the session isn't
    // actively being viewed (app backgrounded, or navigated off-screen), freezing the total timer
    // instead of letting it count straight through that gap.
    val sessionActiveElapsedMs: Long = 0L,
    val sessionActiveSegmentStartMs: Long? = null,
    // Same pause-aware shape as sessionActiveElapsedMs/sessionActiveSegmentStartMs above, but for
    // the current question rather than the whole session — see ReviewViewModel's questionTiming.
    val questionActiveElapsedMs: Long = 0L,
    val questionActiveSegmentStartMs: Long? = null,
    // Non-null once the current question has been answered — freezes the "time on this question"
    // display at this value instead of letting it keep ticking through the feedback screen. Reset
    // to null whenever a fresh, unanswered question is shown (see advanceToNextQuestion, undo).
    val questionElapsedMs: Long? = null,
    val sessionMissedItems: List<ReviewItem> = emptyList(),
    val sessionTotalElapsedMs: Long = 0L,
    val sessionAverageTimePerItemMs: Long = 0L,
    val sessionSlowestAnswers: List<SlowAnswer<ReviewItem>> = emptyList()
)

private typealias ItemProgress = QuizItemProgress<ReviewItem>

class ReviewViewModel(
    private val assignmentRepository: AssignmentRepository,
    private val outboxRepository: OutboxRepository,
    private val statsRepository: StatsRepository,
    private val reviewSessionRepository: ReviewSessionRepository,
    private val lastSessionSummaryRepository: LastSessionSummaryRepository,
    private val pronunciationAudioPlayer: PronunciationAudioPlayer,
    private val settingsRepository: SettingsRepository,
    private val appForegroundTracker: AppForegroundTracker,
    private val applicationScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val queue = QuizQueue<ReviewItem>()
    private val progressByAssignmentId = mutableMapOf<Long, ItemProgress>()
    private var totalQuestions = 0

    private val gradingGuard = QuizGradingGuard(viewModelScope)

    // Every write to reviewSessionRepository (save or clear) is routed through this queue so
    // writes apply in the order they were issued, even though each one actually runs on
    // applicationScope's multi-threaded dispatcher — otherwise grading the last question's save
    // (which does extra outbox/stats work first) can land after the completion-time clear that
    // logically followed it, resurrecting a savepoint the app just erased.
    private val sessionWriteQueue = SerialDurableWork(applicationScope)

    // Individual per-answer records, used for the "slowest answers" summary — persisted and
    // restored across a resume just like progressByAssignmentId (see resumeFromPersisted), so the
    // summary reflects the whole session, not just the segment since the most recent resume.
    private val answeredQuestions = mutableListOf<AnsweredQuestionRecord<ReviewItem>>()

    // Tracks only the time the session was actively being viewed — see QuizSessionTiming. Pause
    // always re-persists unless the session has already completed, OR the queue has already
    // emptied but isSessionComplete hasn't caught up yet (gradeAnswer clears reviewSessionRepository
    // the instant the last question is graded, before the user taps Continue — re-persisting here
    // in that window would resurrect the stale, empty-queue session it just cleared).
    private val sessionTiming = QuizSessionTiming(
        onResume = { now -> _uiState.update { it.copy(sessionActiveSegmentStartMs = now) } },
        onPause = pause@{ newElapsed ->
            _uiState.update { it.copy(sessionActiveElapsedMs = newElapsed, sessionActiveSegmentStartMs = null) }
            if (_uiState.value.isSessionComplete || queue.current == null) return@pause
            viewModelScope.launch { sessionWriteQueue.run { persistCurrentState() } }
        }
    )

    // Same idea as sessionTiming, but for the current question — pauses on backgrounding just like
    // the session timer, instead of counting straight through time spent away (see restart()/
    // freeze(), used when a new question is shown / the current one is graded, versus resume()/
    // pause(), used only by wireForegroundTracking below for background/foreground transitions).
    private val questionTiming = QuizSessionTiming(
        onResume = { now -> _uiState.update { it.copy(questionActiveSegmentStartMs = now) } },
        onPause = { newElapsed -> _uiState.update { it.copy(questionActiveElapsedMs = newElapsed, questionActiveSegmentStartMs = null) } }
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
            settingsRepository.settings.collect { settings ->
                latestSettings = settings
                _uiState.update {
                    it.copy(
                        showSubjectTypeLabel = settings.showSubjectTypeLabel,
                        showTotalTimer = settings.showTotalTimer,
                        showQuestionTimer = settings.showQuestionTimer,
                        useJapaneseKeyboard = settings.useJapaneseKeyboard
                    )
                }
            }
        }
        // The initial value is handled by loadOrResume/sessionTiming.resume() below instead — see
        // QuizSessionTiming.wireForegroundTracking's doc comment.
        sessionTiming.wireForegroundTracking(viewModelScope, appForegroundTracker)
        questionTiming.wireForegroundTracking(viewModelScope, appForegroundTracker)
    }

    /** Resumes a persisted in-progress session if one exists, otherwise fetches a fresh queue. */
    fun loadOrResume() {
        viewModelScope.launch {
            _uiState.update { ReviewUiState(isLoading = true) }
            // Warmed once here, during the loading spinner, so every answer graded during this
            // session can compute its rank change synchronously — see
            // AssignmentRepository.computeReviewRankChange.
            assignmentRepository.warmSrsSystemCache()
            val persisted = reviewSessionRepository.load()
            if (persisted != null) {
                resumeFromPersisted(persisted)
            } else {
                fetchFreshQueue()
            }
        }
    }

    private suspend fun fetchFreshQueue() {
        when (val result = assignmentRepository.refreshReviewQueue()) {
            is ApiResult.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            is ApiResult.Success -> buildQueue(assignmentRepository.observeReviewQueue().first())
        }
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
            reviewSessionRepository.clear()
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
            // Distinct from isSessionComplete — nothing was ever reviewed this visit, so there's no
            // summary to show. Mirrors LessonViewModel's hasNoLessonsAvailable, set in the same
            // fresh-fetch-came-back-empty spot (as opposed to advanceToNextQuestion, where the queue
            // draining to empty after real progress is a genuine completion).
            _uiState.update { it.copy(isLoading = false, hasNoReviewsAvailable = true) }
        } else {
            sessionWriteQueue.run { persistCurrentState() }
            advanceToNextQuestion()
        }
    }

    fun onAnswerInputChange(value: String) {
        _uiState.update { it.copy(answerInput = value) }
    }

    fun toggleDetails() {
        _uiState.update { it.copy(isDetailsExpanded = !it.isDetailsExpanded) }
    }

    /** Unlike [toggleDetails] (a real flip, driven by the swipe handle/gesture-settle sync), this is
     *  the definitively-directional close used by the scrim tap, the close button, and the back
     *  handler — those always mean "close", never "toggle", so they must not risk re-opening the
     *  sheet if called while it's already collapsed. */
    fun closeDetails() {
        _uiState.update { it.copy(isDetailsExpanded = false) }
    }

    fun submitAnswer() {
        val state = _uiState.value
        if (state.feedback != null) return
        val item = state.currentItem ?: return
        val type = state.currentQuestionType ?: return
        if (state.answerInput.isBlank()) return

        gradingGuard.launchIfIdle {
            val candidates = candidatesFor(item.meanings, item.auxiliaryMeanings, item.readings, type)
            when (val outcome = evaluateAnswer(state.answerInput, type, item.meanings, item.auxiliaryMeanings, item.readings)) {
                AnswerOutcome.TypeMismatch ->
                    _uiState.update { it.copy(answerTypeMismatchCount = it.answerTypeMismatchCount + 1) }
                is AnswerOutcome.Graded ->
                    gradeAnswer(item, type, outcome.isCorrect, candidates, expandDetails = false, wasCloseMatch = outcome.wasCloseMatch)
            }
        }
    }

    /** Gives up on the current question — grades it as a miss without requiring a typed guess. */
    fun dontKnowAnswer() {
        val state = _uiState.value
        if (state.feedback != null) return
        val item = state.currentItem ?: return
        val type = state.currentQuestionType ?: return

        gradingGuard.launchIfIdle {
            val candidates = candidatesFor(item.meanings, item.auxiliaryMeanings, item.readings, type)
            gradeAnswer(item, type, isCorrect = false, candidates, expandDetails = false)
        }
    }

    private suspend fun gradeAnswer(
        item: ReviewItem,
        type: QuestionType,
        isCorrect: Boolean,
        candidates: List<String>,
        expandDetails: Boolean,
        wasCloseMatch: Boolean = false
    ) {
        val (grade, snapshot, queueIsEmpty) = run {
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
                    QuestionType.MEANING -> itemProgress.hadIncorrectMeaning = true
                    QuestionType.READING -> itemProgress.hadIncorrectReading = true
                }
                queue.requeue(PendingQuestion(item, type))
            }

            // Whether this answer was the very last one due — if so, persistDurabilityWork clears
            // reviewSessionRepository outright instead of saving a snapshot of the now-empty queue.
            // That snapshot would only ever get overwritten by advanceToNextQuestion's own clear once
            // the user taps Continue anyway; not writing it in the first place, right when the queue
            // empties, is simpler and safer than writing it and racing a later clear against it.
            val queueIsEmpty = queue.current == null

            // Snapshotted synchronously, right after mutating the queue/progress above, so the
            // detached durability write below can safely run concurrently with the next question's
            // own grading/advance — queue/progressByAssignmentId are plain, non-thread-safe
            // collections, and once feedback is visible the user is free to act immediately.
            val snapshot = currentPersistSnapshot()

            val grade = if (isCorrect && isFullyDone(item, itemProgress)) {
                ReviewGrade(meaningCorrect = !itemProgress.hadIncorrectMeaning, readingCorrect = !itemProgress.hadIncorrectReading)
            } else {
                null
            }
            // Computed synchronously against AssignmentRepository's in-memory SRS-system cache
            // (warmed once when the queue loaded) — zero DB access on this critical path at all now.
            // The actual DB write (persisting the new stage) is durability bookkeeping the user never
            // waits on, so it's launched as its own detached coroutine below instead of awaited inline
            // — previously this alone was a sequential DB read-then-write standing between tapping
            // Submit and the rank-change badge appearing, on top of three more writes after it.
            val newRankChange = grade?.let { assignmentRepository.computeReviewRankChange(item, it)?.takeIf { rc -> rc.from != rc.to } }

            _uiState.update {
                it.copy(
                    feedback = AnswerFeedback(isCorrect, candidates.joinToString(", "), wasCloseMatch, candidates.size),
                    remainingCount = queue.size,
                    isDetailsExpanded = it.isDetailsExpanded || expandDetails,
                    rankChange = newRankChange ?: it.rankChange,
                    // Freezes the "time on this question" display the instant feedback appears,
                    // rather than letting it keep ticking while the feedback/Continue screen is up
                    // — matches the elapsedMs recorded for the slowest-answers summary above, which
                    // is stamped at this same moment.
                    questionElapsedMs = questionElapsedMs,
                    questionActiveSegmentStartMs = null
                )
            }

            Triple(grade, snapshot, queueIsEmpty)
        }
        // Reads the field kept warm by the settings collector in init{} instead of
        // `settingsRepository.settings.first()` — see `latestSettings`'s doc comment for why a
        // fresh Flow collection here measurably janked the post-submit animation.
        val settings = latestSettings
        if (type == QuestionType.READING && settings.autoplayPronunciationAudio) {
            candidates.firstOrNull()?.let { reading ->
                selectAudioFor(item.pronunciationAudios, reading, mp3Only = settings.restrictAudioToMp3)
                    ?.let(pronunciationAudioPlayer::play)
            }
        }

        persistDurabilityWork(grade, item, snapshot, queueIsEmpty)
    }

    /** Reverts the most recent incorrect answer — for a typo, not a genuine miss. The queue/
     *  progress mutation itself is shared via [undoLastIncorrectAnswer]. */
    fun undoLastAnswer() {
        val state = _uiState.value
        val item = state.currentItem ?: return
        val type = state.currentQuestionType ?: return
        val feedback = state.feedback ?: return
        if (feedback.isCorrect) return

        viewModelScope.launch {
            val didUndo = undoLastIncorrectAnswer(
                queue = queue,
                progressByAssignmentId = progressByAssignmentId,
                answeredQuestions = answeredQuestions,
                item = item,
                questionType = type,
                persist = { sessionWriteQueue.run { persistCurrentState() } }
            )
            if (!didUndo) return@launch
            // Restarts this question's clock so the retry's timing doesn't inherit time spent
            // before the undo.
            val questionStartedAt = questionTiming.restart()

            // undoCounter changes even though currentItem/currentQuestionType don't — this is what
            // the answer field's focus-restoring LaunchedEffect keys on, since undo doesn't change
            // either of those but still needs to refocus the field the user just tapped away from.
            _uiState.update {
                it.copy(
                    feedback = null,
                    answerInput = "",
                    remainingCount = queue.size,
                    undoCounter = it.undoCounter + 1,
                    questionActiveElapsedMs = 0L,
                    questionActiveSegmentStartMs = questionStartedAt,
                    questionElapsedMs = null
                )
            }
        }
    }

    private fun isFullyDone(item: ReviewItem, progress: ItemProgress): Boolean {
        val requiresReading = item.subjectType != SubjectType.RADICAL && item.subjectType != SubjectType.KANA_VOCABULARY
        return progress.meaningDone && (!requiresReading || progress.readingDone)
    }

    fun onContinue() {
        viewModelScope.launch { advanceToNextQuestion() }
    }

    /** Stops introducing brand-new items; only the current item and ones already attempted remain. */
    fun wrapUp() {
        viewModelScope.launch {
            val currentAssignmentId = queue.current?.item?.assignmentId
            queue.retainCurrentAndMatching {
                progressByAssignmentId[it.item.assignmentId]?.hasAnyProgress == true ||
                    it.item.assignmentId == currentAssignmentId
            }
            totalQuestions = queue.size + completedQuestionCount()

            sessionWriteQueue.run { persistCurrentState() }
            _uiState.update { it.copy(isWrappingUp = true, totalCount = totalQuestions, remainingCount = queue.size) }
        }
    }

    /** Discards progress on not-yet-submitted items and exits — a clean slate next time. */
    fun abandonSession() {
        viewModelScope.launch {
            sessionWriteQueue.run { reviewSessionRepository.clear() }
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
        val next = queue.current
        if (next == null) {
            sessionWriteQueue.run { reviewSessionRepository.clear() }
            outboxRepository.requestSyncNow()
            val summary = sessionSummary()
            persistLastSessionSummary(summary)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isSessionComplete = true,
                    currentItem = null,
                    currentQuestionType = null,
                    remainingCount = 0,
                    feedback = null,
                    rankChange = null,
                    isDetailsExpanded = false,
                    sessionItemsReviewed = summary.itemsCount,
                    sessionItemsCorrectFirstTry = summary.correctFirstTry,
                    sessionMissedItems = summary.missedItems,
                    sessionTotalElapsedMs = summary.totalElapsedMs,
                    sessionAverageTimePerItemMs = summary.averageTimePerItemMs,
                    sessionSlowestAnswers = summary.slowestAnswers
                )
            }
            return
        }
        val questionStartedAt = questionTiming.restart()
        _uiState.update {
            it.copy(
                isLoading = false,
                currentItem = next.item,
                currentQuestionType = next.type,
                answerInput = "",
                feedback = null,
                rankChange = null,
                isDetailsExpanded = false,
                totalCount = totalQuestions,
                remainingCount = queue.size,
                sessionActiveElapsedMs = sessionTiming.elapsedMs,
                sessionActiveSegmentStartMs = sessionTiming.segmentStartMs,
                questionActiveElapsedMs = 0L,
                questionActiveSegmentStartMs = questionStartedAt,
                questionElapsedMs = null
            )
        }
    }

    /** Captures the current queue/progress as an immutable, ready-to-persist value — safe to hold
     *  across a suspension point even if the live queue/progressByAssignmentId are mutated by
     *  something else afterward (see [gradeAnswer]'s deferred [persistDurabilityWork] call). Folds
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
        }
    )

    private suspend fun persistCurrentState() {
        reviewSessionRepository.save(currentPersistSnapshot())
    }

    /** Runs the post-grading durability writes (outbox enqueue, study-streak mark, session
     *  persistence) — see [SerialDurableWork] for why this needs [applicationScope] rather than
     *  `viewModelScope`. Clears reviewSessionRepository instead of saving [snapshot] when
     *  [queueIsEmpty] — this was the last due question, so [snapshot] is already an empty-queue
     *  shell that advanceToNextQuestion's own clear would just overwrite once the user taps
     *  Continue; not saving it in the first place is simpler than saving it and racing a later
     *  clear against it. */
    private suspend fun persistDurabilityWork(grade: ReviewGrade?, item: ReviewItem, snapshot: PersistedReviewSession, queueIsEmpty: Boolean) {
        sessionWriteQueue.run {
            if (grade != null) {
                // The actual DB write of the new SRS stage — already reflected in the UI via the
                // synchronous computeReviewRankChange prediction above, so this just makes the
                // local cache catch up. Recomputes from a fresh DB read rather than trusting the
                // in-memory item, so a concurrent change elsewhere still wins.
                assignmentRepository.applyOptimisticReviewResult(item.assignmentId, item.srsSystemId, grade)
                outboxRepository.enqueueReviewSubmission(item.assignmentId, item.subjectId, grade)
                statsRepository.markStudyActivityToday()
            }
            if (queueIsEmpty) reviewSessionRepository.clear() else reviewSessionRepository.save(snapshot)
        }
    }
}
