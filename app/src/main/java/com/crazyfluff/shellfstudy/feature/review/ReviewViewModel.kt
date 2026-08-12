package com.crazyfluff.shellfstudy.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.core.audio.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.core.audio.selectAudioFor
import com.crazyfluff.shellfstudy.core.data.ApiResult
import com.crazyfluff.shellfstudy.core.data.AssignmentRepository
import com.crazyfluff.shellfstudy.core.data.containsKana
import com.crazyfluff.shellfstudy.core.data.OutboxRepository
import com.crazyfluff.shellfstudy.core.data.PersistedItemProgress
import com.crazyfluff.shellfstudy.core.data.PersistedQuestion
import com.crazyfluff.shellfstudy.core.data.PersistedReviewSession
import com.crazyfluff.shellfstudy.core.data.ReviewSessionRepository
import com.crazyfluff.shellfstudy.core.data.SettingsRepository
import com.crazyfluff.shellfstudy.core.data.StatsRepository
import com.crazyfluff.shellfstudy.core.data.model.RankChange
import com.crazyfluff.shellfstudy.core.data.model.ReviewGrade
import com.crazyfluff.shellfstudy.core.data.model.ReviewItem
import com.crazyfluff.shellfstudy.core.network.SubjectType
import com.crazyfluff.shellfstudy.core.util.CloseEnoughMatcher
import com.crazyfluff.shellfstudy.core.util.RomajiConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class QuestionType { MEANING, READING }

data class AnswerFeedback(
    val isCorrect: Boolean,
    val correctAnswer: String,
    val wasCloseMatch: Boolean = false,
    val answerCount: Int = 1
)

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
    val isAbandoned: Boolean = false,
    val isWrappingUp: Boolean = false,
    val isDetailsExpanded: Boolean = false,
    val sessionItemsReviewed: Int = 0,
    val sessionItemsCorrectFirstTry: Int = 0,
    val answerTypeMismatchCount: Int = 0,
    val showSubjectTypeLabel: Boolean = false,
    val showReviewTimer: Boolean = false,
    val sessionStartTimeMs: Long? = null,
    val sessionMissedItems: List<ReviewItem> = emptyList(),
    val sessionTotalElapsedMs: Long = 0L,
    val sessionAverageTimePerItemMs: Long = 0L,
    val sessionSlowestAnswers: List<SlowAnswer> = emptyList()
)

data class SlowAnswer(val item: ReviewItem, val type: QuestionType, val elapsedMs: Long, val isCorrect: Boolean)

private data class PendingQuestion(val item: ReviewItem, val type: QuestionType)

private class ItemProgress(val item: ReviewItem) {
    var meaningDone = false
    var readingDone = false
    var hadIncorrectMeaning = false
    var hadIncorrectReading = false
    val hasAnyProgress: Boolean get() = meaningDone || readingDone || hadIncorrectMeaning || hadIncorrectReading
}

private data class AnsweredQuestionRecord(val item: ReviewItem, val type: QuestionType, val isCorrect: Boolean, val elapsedMs: Long)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val assignmentRepository: AssignmentRepository,
    private val outboxRepository: OutboxRepository,
    private val statsRepository: StatsRepository,
    private val reviewSessionRepository: ReviewSessionRepository,
    private val pronunciationAudioPlayer: PronunciationAudioPlayer,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val queue = ArrayDeque<PendingQuestion>()
    private val progressByAssignmentId = mutableMapOf<Long, ItemProgress>()
    private var totalQuestions = 0

    // Set synchronously (not via _uiState) so a second rapid tap is rejected immediately, before
    // the first submission's suspend work (grading, optimistic SRS write, outbox enqueue) has had
    // a chance to land feedback in state — otherwise both submissions race gradeAnswer, double the
    // queue mutation, and the loser's stale rank change flashes on screen before the winner's.
    private var isGrading = false

    private val answeredQuestions = mutableListOf<AnsweredQuestionRecord>()
    // In-memory only, matching the rest of this session-stat tracking — a process death mid-session
    // simply restarts the clock on resume rather than resuming the original elapsed time.
    private var sessionStartTimeMs: Long = 0L
    private var questionShownAtMs: Long = 0L

    init {
        loadOrResume()
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(showSubjectTypeLabel = settings.showSubjectTypeLabel, showReviewTimer = settings.showReviewTimer)
                }
            }
        }
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
        val itemsById = assignmentRepository.observeReviewQueue().first().associateBy { it.assignmentId }

        // The cache backing this persisted session is gone (e.g. app storage was cleared), or a
        // progress entry belongs to an item that was fully completed before the process died —
        // applyOptimisticReviewResult() pushes a completed item's next-review time into the
        // future, so it drops out of observeReviewQueue()/itemsById even though its progress (kept
        // around for session stats) is still persisted. Either way, fall back to a fresh fetch
        // rather than crash building ItemProgress for an item we can no longer look up.
        if (persisted.queue.any { it.assignmentId !in itemsById } || persisted.progress.any { it.assignmentId !in itemsById }) {
            reviewSessionRepository.clear()
            fetchFreshQueue()
            return
        }

        queue.clear()
        persisted.queue.forEach { entry ->
            queue.add(PendingQuestion(itemsById.getValue(entry.assignmentId), QuestionType.valueOf(entry.questionType)))
        }
        progressByAssignmentId.clear()
        persisted.progress.forEach { p ->
            progressByAssignmentId[p.assignmentId] = ItemProgress(itemsById.getValue(p.assignmentId)).apply {
                meaningDone = p.meaningDone
                readingDone = p.readingDone
                hadIncorrectMeaning = p.hadIncorrectMeaning
                hadIncorrectReading = p.hadIncorrectReading
            }
        }
        totalQuestions = persisted.totalQuestions
        answeredQuestions.clear()
        sessionStartTimeMs = System.currentTimeMillis()
        advanceToNextQuestion()
    }

    private suspend fun buildQueue(items: List<ReviewItem>) {
        queue.clear()
        progressByAssignmentId.clear()
        answeredQuestions.clear()
        sessionStartTimeMs = System.currentTimeMillis()

        items.forEach { item ->
            progressByAssignmentId[item.assignmentId] = ItemProgress(item)
            questionTypesFor(item).forEach { type -> queue.add(PendingQuestion(item, type)) }
        }
        queue.shuffle()
        totalQuestions = queue.size

        if (queue.isEmpty()) {
            _uiState.update { it.copy(isLoading = false, isSessionComplete = true, totalCount = 0, remainingCount = 0) }
        } else {
            persistCurrentState()
            advanceToNextQuestion()
        }
    }

    /** Never lets a malformed answer crash grading — falls back to the raw (untranslated) text. */
    private fun convertReadingSafely(rawAnswer: String): String =
        try {
            RomajiConverter.toHiragana(rawAnswer)
        } catch (e: Exception) {
            rawAnswer
        }

    private fun questionTypesFor(item: ReviewItem): List<QuestionType> =
        if (item.subjectType == SubjectType.RADICAL) {
            listOf(QuestionType.MEANING)
        } else {
            listOf(QuestionType.MEANING, QuestionType.READING)
        }

    fun onAnswerInputChange(value: String) {
        _uiState.update { it.copy(answerInput = value) }
    }

    fun toggleDetails() {
        _uiState.update { it.copy(isDetailsExpanded = !it.isDetailsExpanded) }
    }

    /** Meaning answers pool the primary meanings with WaniKani's own whitelist synonyms — both are
     *  equally acceptable. Reading answers stay exact-match-only, so no auxiliary readings exist. */
    private fun candidatesFor(item: ReviewItem, type: QuestionType): List<String> =
        if (type == QuestionType.MEANING) item.meanings + item.auxiliaryMeanings else item.readings

    fun submitAnswer() {
        val state = _uiState.value
        if (state.feedback != null || isGrading) return
        val item = state.currentItem ?: return
        val type = state.currentQuestionType ?: return
        if (state.answerInput.isBlank()) return

        isGrading = true
        viewModelScope.launch {
            try {
                val candidates = candidatesFor(item, type)
                if (type == QuestionType.MEANING) {
                    // A small typo is graded as correct but flagged, rather than a flat miss — readings
                    // stay exact-match, matching WaniKani's own convention for kana.
                    val match = CloseEnoughMatcher.match(state.answerInput, candidates)
                    // Typing a reading into a meaning answer is a habit slip, not a genuine miss — reject
                    // it outright rather than spending an SRS attempt on it.
                    if (!match.isMatch && state.answerInput.containsKana()) {
                        _uiState.update { it.copy(answerTypeMismatchCount = it.answerTypeMismatchCount + 1) }
                        return@launch
                    }
                    gradeAnswer(item, type, match.isMatch, candidates, expandDetails = false, wasCloseMatch = match.isMatch && !match.isExact)
                } else {
                    val normalizedAnswer = convertReadingSafely(state.answerInput.trim())
                    val isCorrect = candidates.any { it.trim().equals(normalizedAnswer, ignoreCase = true) }
                    // Same idea in reverse: a wrong reading that closely matches this item's own meaning
                    // is almost certainly the other question type typed by habit, not a real miss.
                    if (!isCorrect && CloseEnoughMatcher.match(state.answerInput, candidatesFor(item, QuestionType.MEANING)).isMatch) {
                        _uiState.update { it.copy(answerTypeMismatchCount = it.answerTypeMismatchCount + 1) }
                        return@launch
                    }
                    gradeAnswer(item, type, isCorrect, candidates, expandDetails = false)
                }
            } finally {
                isGrading = false
            }
        }
    }

    /** Gives up on the current question — grades it as a miss without requiring a typed guess. */
    fun dontKnowAnswer() {
        val state = _uiState.value
        if (state.feedback != null || isGrading) return
        val item = state.currentItem ?: return
        val type = state.currentQuestionType ?: return

        isGrading = true
        viewModelScope.launch {
            try {
                val candidates = candidatesFor(item, type)
                gradeAnswer(item, type, isCorrect = false, candidates, expandDetails = true)
            } finally {
                isGrading = false
            }
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
        val itemProgress = progressByAssignmentId.getOrPut(item.assignmentId) { ItemProgress(item) }
        answeredQuestions.add(AnsweredQuestionRecord(item, type, isCorrect, System.currentTimeMillis() - questionShownAtMs))

        queue.removeFirstOrNull()
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
            queue.addLast(PendingQuestion(item, type))
        }

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
                rankChange = newRankChange ?: it.rankChange
            )
        }

        persistDurabilityWork(grade, item, snapshot)

        val settings = settingsRepository.settings.first()
        if (type == QuestionType.READING && settings.autoplayPronunciationAudio) {
            candidates.firstOrNull()?.let { reading ->
                selectAudioFor(item.pronunciationAudios, reading, mp3Only = settings.restrictAudioToMp3)
                    ?.let(pronunciationAudioPlayer::play)
            }
        }
    }

    /** Reverts the most recent incorrect answer — for a typo, not a genuine miss. */
    fun undoLastAnswer() {
        val state = _uiState.value
        val item = state.currentItem ?: return
        val type = state.currentQuestionType ?: return
        val feedback = state.feedback ?: return
        if (feedback.isCorrect) return

        viewModelScope.launch {
            val itemProgress = progressByAssignmentId[item.assignmentId] ?: return@launch
            when (type) {
                QuestionType.MEANING -> itemProgress.hadIncorrectMeaning = false
                QuestionType.READING -> itemProgress.hadIncorrectReading = false
            }
            // The wrong submission moved this question to the back of the queue via addLast;
            // move it back to the front so it stays "current" (queue.first() == currentItem is
            // the invariant advanceToNextQuestion relies on), rather than dropping it entirely.
            val requeuedIndex = queue.indexOfLast { it.item.assignmentId == item.assignmentId && it.type == type }
            if (requeuedIndex >= 0) queue.addFirst(queue.removeAt(requeuedIndex))

            // Undo removes the incorrect attempt just recorded by gradeAnswer, and restarts this
            // question's clock so the retry's timing doesn't inherit time spent before the undo.
            answeredQuestions.removeLastOrNull()
            questionShownAtMs = System.currentTimeMillis()

            persistCurrentState()
            // undoCounter changes even though currentItem/currentQuestionType don't — this is what
            // the answer field's focus-restoring LaunchedEffect keys on, since undo doesn't change
            // either of those but still needs to refocus the field the user just tapped away from.
            _uiState.update { it.copy(feedback = null, answerInput = "", remainingCount = queue.size, undoCounter = it.undoCounter + 1) }
        }
    }

    private fun isFullyDone(item: ReviewItem, progress: ItemProgress): Boolean {
        val requiresReading = item.subjectType != SubjectType.RADICAL
        return progress.meaningDone && (!requiresReading || progress.readingDone)
    }

    fun onContinue() {
        viewModelScope.launch { advanceToNextQuestion() }
    }

    /** Stops introducing brand-new items; only the current item and ones already attempted remain. */
    fun wrapUp() {
        viewModelScope.launch {
            val current = queue.firstOrNull()
            val rest = queue.drop(1).filter { progressByAssignmentId[it.item.assignmentId]?.hasAnyProgress == true }
            queue.clear()
            current?.let(queue::add)
            queue.addAll(rest)
            totalQuestions = queue.size + completedQuestionCount()

            persistCurrentState()
            _uiState.update { it.copy(isWrappingUp = true, totalCount = totalQuestions, remainingCount = queue.size) }
        }
    }

    /** Discards progress on not-yet-submitted items and exits — a clean slate next time. */
    fun abandonSession() {
        viewModelScope.launch {
            reviewSessionRepository.clear()
            _uiState.update { it.copy(isAbandoned = true) }
        }
    }

    private fun completedQuestionCount(): Int =
        progressByAssignmentId.values.sumOf { (if (it.meaningDone) 1 else 0) + (if (it.readingDone) 1 else 0) }

    private data class SessionSummary(
        val itemsReviewed: Int,
        val correctFirstTry: Int,
        val missedItems: List<ReviewItem>,
        val totalElapsedMs: Long,
        val averageTimePerItemMs: Long,
        val slowestAnswers: List<SlowAnswer>
    )

    /** Items reviewed, how many were correct without ever missing, which were missed at least once,
     *  and timing — total session time, average time per item reviewed, and the slowest answers.
     *  The average divides total wall-clock session time (start to finish, including feedback
     *  screens and rank-change animations between questions) by the count of distinct items
     *  reviewed — that's what a user actually means by "average time per item." */
    private fun sessionSummary(): SessionSummary {
        val itemsReviewed = progressByAssignmentId.size
        val correctFirstTry = progressByAssignmentId.values.count { !it.hadIncorrectMeaning && !it.hadIncorrectReading }
        val missedItems = progressByAssignmentId.values
            .filter { it.hadIncorrectMeaning || it.hadIncorrectReading }
            .map { it.item }
        val totalElapsedMs = System.currentTimeMillis() - sessionStartTimeMs
        val averageTimePerItemMs = if (itemsReviewed == 0) 0L else totalElapsedMs / itemsReviewed
        val slowestAnswers = answeredQuestions.sortedByDescending { it.elapsedMs }.take(5)
            .map { SlowAnswer(it.item, it.type, it.elapsedMs, it.isCorrect) }
        return SessionSummary(
            itemsReviewed = itemsReviewed,
            correctFirstTry = correctFirstTry,
            missedItems = missedItems,
            totalElapsedMs = totalElapsedMs,
            averageTimePerItemMs = averageTimePerItemMs,
            slowestAnswers = slowestAnswers
        )
    }

    private suspend fun advanceToNextQuestion() {
        val next = queue.firstOrNull()
        if (next == null) {
            reviewSessionRepository.clear()
            val summary = sessionSummary()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isSessionComplete = true,
                    currentItem = null,
                    currentQuestionType = null,
                    remainingCount = 0,
                    feedback = null,
                    rankChange = null,
                    sessionItemsReviewed = summary.itemsReviewed,
                    sessionItemsCorrectFirstTry = summary.correctFirstTry,
                    sessionMissedItems = summary.missedItems,
                    sessionTotalElapsedMs = summary.totalElapsedMs,
                    sessionAverageTimePerItemMs = summary.averageTimePerItemMs,
                    sessionSlowestAnswers = summary.slowestAnswers
                )
            }
            return
        }
        questionShownAtMs = System.currentTimeMillis()
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
                sessionStartTimeMs = sessionStartTimeMs
            )
        }
    }

    /** Captures the current queue/progress as an immutable, ready-to-persist value — safe to hold
     *  across a suspension point even if the live queue/progressByAssignmentId are mutated by
     *  something else afterward (see [gradeAnswer]'s deferred [persistDurabilityWork] call). */
    private fun currentPersistSnapshot(): PersistedReviewSession = PersistedReviewSession(
        queue = queue.map { PersistedQuestion(it.item.assignmentId, it.type.name) },
        progress = progressByAssignmentId.map { (id, p) ->
            PersistedItemProgress(id, p.meaningDone, p.readingDone, p.hadIncorrectMeaning, p.hadIncorrectReading)
        },
        totalQuestions = totalQuestions
    )

    private suspend fun persistSnapshot(snapshot: PersistedReviewSession) {
        reviewSessionRepository.save(snapshot)
    }

    private suspend fun persistCurrentState() {
        persistSnapshot(currentPersistSnapshot())
    }

    /** Fires the post-grading durability writes (outbox enqueue, study-streak mark, session
     *  persistence) as their own child coroutine, detached from [gradeAnswer]'s own suspend chain
     *  — none of it needs to complete before the caller (submitAnswer/dontKnowAnswer) considers
     *  grading "done" and clears [isGrading], so the UI unlocks the instant feedback is visible
     *  rather than waiting on bookkeeping the user never sees. */
    private fun persistDurabilityWork(grade: ReviewGrade?, item: ReviewItem, snapshot: PersistedReviewSession) {
        viewModelScope.launch {
            if (grade != null) {
                // The actual DB write of the new SRS stage — already reflected in the UI via the
                // synchronous computeReviewRankChange prediction above, so this just makes the
                // local cache catch up. Recomputes from a fresh DB read rather than trusting the
                // in-memory item, so a concurrent change elsewhere still wins.
                assignmentRepository.applyOptimisticReviewResult(item.assignmentId, item.srsSystemId, grade)
                outboxRepository.enqueueReviewSubmission(item.assignmentId, item.subjectId, grade)
                statsRepository.markStudyActivityToday()
            }
            persistSnapshot(snapshot)
        }
    }
}
