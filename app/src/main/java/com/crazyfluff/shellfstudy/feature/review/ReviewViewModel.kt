package com.crazyfluff.shellfstudy.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.core.audio.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.core.audio.selectAudioFor
import com.crazyfluff.shellfstudy.core.data.ApiResult
import com.crazyfluff.shellfstudy.core.data.AssignmentRepository
import com.crazyfluff.shellfstudy.core.data.PersistedItemProgress
import com.crazyfluff.shellfstudy.core.data.PersistedQuestion
import com.crazyfluff.shellfstudy.core.data.PersistedReviewSession
import com.crazyfluff.shellfstudy.core.data.ReviewSessionRepository
import com.crazyfluff.shellfstudy.core.data.SettingsRepository
import com.crazyfluff.shellfstudy.core.data.WaniKaniRepository
import com.crazyfluff.shellfstudy.core.data.model.RankChange
import com.crazyfluff.shellfstudy.core.data.model.ReviewGrade
import com.crazyfluff.shellfstudy.core.data.model.ReviewItem
import com.crazyfluff.shellfstudy.core.data.model.SrsStage
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
    val sessionItemsCorrectFirstTry: Int = 0
)

private data class PendingQuestion(val item: ReviewItem, val type: QuestionType)

private class ItemProgress {
    var meaningDone = false
    var readingDone = false
    var hadIncorrectMeaning = false
    var hadIncorrectReading = false
    val hasAnyProgress: Boolean get() = meaningDone || readingDone || hadIncorrectMeaning || hadIncorrectReading
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val waniKaniRepository: WaniKaniRepository,
    private val assignmentRepository: AssignmentRepository,
    private val reviewSessionRepository: ReviewSessionRepository,
    private val pronunciationAudioPlayer: PronunciationAudioPlayer,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val queue = ArrayDeque<PendingQuestion>()
    private val progressByAssignmentId = mutableMapOf<Long, ItemProgress>()
    private var totalQuestions = 0

    init {
        loadOrResume()
    }

    /** Resumes a persisted in-progress session if one exists, otherwise fetches a fresh queue. */
    fun loadOrResume() {
        viewModelScope.launch {
            _uiState.update { ReviewUiState(isLoading = true) }
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

        // The cache backing this persisted session is gone (e.g. app storage was cleared) —
        // fall back to a fresh fetch rather than show a broken queue.
        if (persisted.queue.any { it.assignmentId !in itemsById }) {
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
            progressByAssignmentId[p.assignmentId] = ItemProgress().apply {
                meaningDone = p.meaningDone
                readingDone = p.readingDone
                hadIncorrectMeaning = p.hadIncorrectMeaning
                hadIncorrectReading = p.hadIncorrectReading
            }
        }
        totalQuestions = persisted.totalQuestions
        advanceToNextQuestion()
    }

    private suspend fun buildQueue(items: List<ReviewItem>) {
        queue.clear()
        progressByAssignmentId.clear()

        items.forEach { item ->
            progressByAssignmentId[item.assignmentId] = ItemProgress()
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
        if (state.feedback != null) return
        val item = state.currentItem ?: return
        val type = state.currentQuestionType ?: return
        if (state.answerInput.isBlank()) return

        viewModelScope.launch {
            val candidates = candidatesFor(item, type)
            if (type == QuestionType.MEANING) {
                // A small typo is graded as correct but flagged, rather than a flat miss — readings
                // stay exact-match, matching WaniKani's own convention for kana.
                val match = CloseEnoughMatcher.match(state.answerInput, candidates)
                gradeAnswer(item, type, match.isMatch, candidates, expandDetails = false, wasCloseMatch = match.isMatch && !match.isExact)
            } else {
                val normalizedAnswer = convertReadingSafely(state.answerInput.trim())
                val isCorrect = candidates.any { it.trim().equals(normalizedAnswer, ignoreCase = true) }
                gradeAnswer(item, type, isCorrect, candidates, expandDetails = false)
            }
        }
    }

    /** Gives up on the current question — grades it as a miss without requiring a typed guess. */
    fun dontKnowAnswer() {
        val state = _uiState.value
        if (state.feedback != null) return
        val item = state.currentItem ?: return
        val type = state.currentQuestionType ?: return

        viewModelScope.launch {
            val candidates = candidatesFor(item, type)
            gradeAnswer(item, type, isCorrect = false, candidates, expandDetails = true)
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
        val itemProgress = progressByAssignmentId.getOrPut(item.assignmentId) { ItemProgress() }

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

        if (isCorrect && isFullyDone(item, itemProgress)) {
            submitReviewResult(item, itemProgress)
        }

        persistCurrentState()
        _uiState.update {
            it.copy(
                feedback = AnswerFeedback(isCorrect, candidates.joinToString(", "), wasCloseMatch, candidates.size),
                remainingCount = queue.size,
                isDetailsExpanded = it.isDetailsExpanded || expandDetails
            )
        }

        if (type == QuestionType.READING && settingsRepository.settings.first().autoplayPronunciationAudio) {
            candidates.firstOrNull()?.let { reading ->
                selectAudioFor(item.pronunciationAudios, reading)?.let(pronunciationAudioPlayer::play)
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

            persistCurrentState()
            // undoCounter changes even though currentItem/currentQuestionType don't — this is what
            // the answer field's focus-restoring LaunchedEffect keys on, since undo doesn't change
            // either of those but still needs to refocus the field the user just tapped away from.
            _uiState.update { it.copy(feedback = null, answerInput = "", remainingCount = queue.size, undoCounter = it.undoCounter + 1) }
        }
    }

    private fun submitReviewResult(item: ReviewItem, progress: ItemProgress) {
        viewModelScope.launch {
            val result = waniKaniRepository.submitReview(
                item.assignmentId,
                ReviewGrade(
                    meaningCorrect = !progress.hadIncorrectMeaning,
                    readingCorrect = !progress.hadIncorrectReading
                )
            )
            if (result is ApiResult.Success && result.data.startingSrsStage != result.data.endingSrsStage) {
                val rankChange = RankChange(
                    from = SrsStage.fromRaw(result.data.startingSrsStage),
                    to = SrsStage.fromRaw(result.data.endingSrsStage)
                )
                _uiState.update { it.copy(rankChange = rankChange) }
            }
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

    /** Items reviewed vs. how many of those were answered correctly without ever missing. */
    private fun sessionStats(): Pair<Int, Int> {
        val itemsReviewed = progressByAssignmentId.size
        val correctFirstTry = progressByAssignmentId.values.count { !it.hadIncorrectMeaning && !it.hadIncorrectReading }
        return itemsReviewed to correctFirstTry
    }

    private suspend fun advanceToNextQuestion() {
        val next = queue.firstOrNull()
        if (next == null) {
            reviewSessionRepository.clear()
            val (itemsReviewed, correctFirstTry) = sessionStats()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isSessionComplete = true,
                    currentItem = null,
                    currentQuestionType = null,
                    remainingCount = 0,
                    feedback = null,
                    rankChange = null,
                    sessionItemsReviewed = itemsReviewed,
                    sessionItemsCorrectFirstTry = correctFirstTry
                )
            }
            return
        }
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
                remainingCount = queue.size
            )
        }
    }

    private suspend fun persistCurrentState() {
        reviewSessionRepository.save(
            PersistedReviewSession(
                queue = queue.map { PersistedQuestion(it.item.assignmentId, it.type.name) },
                progress = progressByAssignmentId.map { (id, p) ->
                    PersistedItemProgress(id, p.meaningDone, p.readingDone, p.hadIncorrectMeaning, p.hadIncorrectReading)
                },
                totalQuestions = totalQuestions
            )
        )
    }
}
