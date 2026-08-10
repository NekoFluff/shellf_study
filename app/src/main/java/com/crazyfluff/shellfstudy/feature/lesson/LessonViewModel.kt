package com.crazyfluff.shellfstudy.feature.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.core.data.ApiResult
import com.crazyfluff.shellfstudy.core.data.WaniKaniRepository
import com.crazyfluff.shellfstudy.core.data.model.LessonItem
import com.crazyfluff.shellfstudy.core.network.SubjectType
import com.crazyfluff.shellfstudy.core.util.RomajiConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How many lessons are studied and then quizzed together in one batch, matching WaniKani's own default. */
private const val LESSON_BATCH_SIZE = 5

enum class LessonPhase { STUDY, QUIZ }
enum class LessonQuestionType { MEANING, READING }

data class LessonAnswerFeedback(val isCorrect: Boolean, val correctAnswer: String)

data class LessonUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val hasNoLessonsAvailable: Boolean = false,
    val phase: LessonPhase = LessonPhase.STUDY,
    val studyItems: List<LessonItem> = emptyList(),
    val studyIndex: Int = 0,
    val currentQuizItem: LessonItem? = null,
    val currentQuestionType: LessonQuestionType? = null,
    val answerInput: String = "",
    val feedback: LessonAnswerFeedback? = null,
    val totalQuizCount: Int = 0,
    val remainingQuizCount: Int = 0,
    val isSessionComplete: Boolean = false
)

private data class PendingLessonQuestion(val item: LessonItem, val type: LessonQuestionType)

@HiltViewModel
class LessonViewModel @Inject constructor(
    private val waniKaniRepository: WaniKaniRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LessonUiState())
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    private val quizQueue = ArrayDeque<PendingLessonQuestion>()
    private val startedAssignmentIds = mutableSetOf<Long>()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { LessonUiState(isLoading = true) }
            quizQueue.clear()
            startedAssignmentIds.clear()

            when (val result = waniKaniRepository.refreshLessonQueue()) {
                is ApiResult.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                is ApiResult.Success -> {
                    val items = waniKaniRepository.observeLessonQueue().first().take(LESSON_BATCH_SIZE)
                    if (items.isEmpty()) {
                        _uiState.update { it.copy(isLoading = false, hasNoLessonsAvailable = true) }
                    } else {
                        _uiState.update {
                            it.copy(isLoading = false, phase = LessonPhase.STUDY, studyItems = items, studyIndex = 0)
                        }
                    }
                }
            }
        }
    }

    fun nextStudyCard() {
        val state = _uiState.value
        if (state.phase != LessonPhase.STUDY) return
        val nextIndex = state.studyIndex + 1
        if (nextIndex >= state.studyItems.size) {
            beginQuiz(state.studyItems)
        } else {
            _uiState.update { it.copy(studyIndex = nextIndex) }
        }
    }

    fun previousStudyCard() {
        val state = _uiState.value
        if (state.phase != LessonPhase.STUDY || state.studyIndex == 0) return
        _uiState.update { it.copy(studyIndex = state.studyIndex - 1) }
    }

    private fun beginQuiz(items: List<LessonItem>) {
        quizQueue.clear()
        items.forEach { item ->
            questionTypesFor(item).forEach { type -> quizQueue.add(PendingLessonQuestion(item, type)) }
        }
        quizQueue.shuffle()
        val total = quizQueue.size
        val next = quizQueue.firstOrNull()
        _uiState.update {
            it.copy(
                phase = LessonPhase.QUIZ,
                totalQuizCount = total,
                remainingQuizCount = total,
                currentQuizItem = next?.item,
                currentQuestionType = next?.type,
                answerInput = "",
                feedback = null,
                isSessionComplete = next == null
            )
        }
    }

    private fun questionTypesFor(item: LessonItem): List<LessonQuestionType> =
        if (item.subjectType == SubjectType.RADICAL) {
            listOf(LessonQuestionType.MEANING)
        } else {
            listOf(LessonQuestionType.MEANING, LessonQuestionType.READING)
        }

    fun onAnswerInputChange(value: String) {
        _uiState.update { it.copy(answerInput = value) }
    }

    fun submitAnswer() {
        val state = _uiState.value
        if (state.feedback != null) return
        val item = state.currentQuizItem ?: return
        val type = state.currentQuestionType ?: return
        if (state.answerInput.isBlank()) return

        val candidates = if (type == LessonQuestionType.MEANING) item.meanings else item.readings
        val normalizedAnswer = if (type == LessonQuestionType.READING) {
            RomajiConverter.toHiragana(state.answerInput.trim())
        } else {
            state.answerInput.trim()
        }
        val isCorrect = candidates.any { it.trim().equals(normalizedAnswer, ignoreCase = true) }
        gradeAnswer(item, type, isCorrect, candidates)
    }

    /** Gives up on the current question — treated the same as a wrong answer, requeued for another pass. */
    fun dontKnowAnswer() {
        val state = _uiState.value
        if (state.feedback != null) return
        val item = state.currentQuizItem ?: return
        val type = state.currentQuestionType ?: return
        val candidates = if (type == LessonQuestionType.MEANING) item.meanings else item.readings
        gradeAnswer(item, type, isCorrect = false, candidates)
    }

    private fun gradeAnswer(
        item: LessonItem,
        type: LessonQuestionType,
        isCorrect: Boolean,
        candidates: List<String>
    ) {
        quizQueue.removeFirstOrNull()
        if (!isCorrect) {
            quizQueue.addLast(PendingLessonQuestion(item, type))
        } else if (quizQueue.none { it.item.assignmentId == item.assignmentId }) {
            // No more pending questions for this item — it's been answered correctly on every
            // question type it has, so the lesson for it is done.
            markStarted(item)
        }

        _uiState.update {
            it.copy(
                feedback = LessonAnswerFeedback(isCorrect, candidates.joinToString(", ")),
                remainingQuizCount = quizQueue.size
            )
        }
    }

    private fun markStarted(item: LessonItem) {
        if (!startedAssignmentIds.add(item.assignmentId)) return
        viewModelScope.launch { waniKaniRepository.startAssignment(item.assignmentId) }
    }

    fun onContinue() {
        advanceQuiz()
    }

    private fun advanceQuiz() {
        val next = quizQueue.firstOrNull()
        if (next == null) {
            _uiState.update {
                it.copy(isSessionComplete = true, currentQuizItem = null, currentQuestionType = null)
            }
            return
        }
        _uiState.update {
            it.copy(
                currentQuizItem = next.item,
                currentQuestionType = next.type,
                answerInput = "",
                feedback = null,
                remainingQuizCount = quizQueue.size
            )
        }
    }
}
