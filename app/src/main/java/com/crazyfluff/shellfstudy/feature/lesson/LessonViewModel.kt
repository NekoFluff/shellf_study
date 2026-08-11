package com.crazyfluff.shellfstudy.feature.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.core.data.ApiResult
import com.crazyfluff.shellfstudy.core.data.AssignmentRepository
import com.crazyfluff.shellfstudy.core.data.PitchAccentRepository
import com.crazyfluff.shellfstudy.core.data.SettingsRepository
import com.crazyfluff.shellfstudy.core.data.SubjectRepository
import com.crazyfluff.shellfstudy.core.data.model.LessonItem
import com.crazyfluff.shellfstudy.core.data.model.PitchAccent
import com.crazyfluff.shellfstudy.core.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.core.data.model.RankChange
import com.crazyfluff.shellfstudy.core.data.model.SrsStage
import com.crazyfluff.shellfstudy.core.network.SubjectType
import com.crazyfluff.shellfstudy.core.util.CloseEnoughMatcher
import com.crazyfluff.shellfstudy.core.util.RomajiConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Default number of lessons pre-selected on the picker, matching WaniKani's own default batch size. */
private const val DEFAULT_LESSON_SELECTION_SIZE = 5

enum class LessonPhase { SELECT, STUDY, QUIZ }
enum class LessonQuestionType { MEANING, READING }

data class LessonAnswerFeedback(
    val isCorrect: Boolean,
    val correctAnswer: String,
    val wasCloseMatch: Boolean = false,
    val answerCount: Int = 1
)

data class LessonUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val hasNoLessonsAvailable: Boolean = false,
    val phase: LessonPhase = LessonPhase.SELECT,
    val availableLessons: List<LessonItem> = emptyList(),
    val selectedAssignmentIds: Set<Long> = emptySet(),
    val studyItems: List<LessonItem> = emptyList(),
    val studyIndex: Int = 0,
    val currentQuizItem: LessonItem? = null,
    val currentQuestionType: LessonQuestionType? = null,
    val answerInput: String = "",
    val feedback: LessonAnswerFeedback? = null,
    val rankChange: RankChange? = null,
    val totalQuizCount: Int = 0,
    val remainingQuizCount: Int = 0,
    val isSessionComplete: Boolean = false,
    val showPitchAccent: Boolean = true,
    val pitchAccentsBySubjectId: Map<Long, List<PitchAccent>> = emptyMap(),
    val relatedSubjectsById: Map<Long, SubjectSummary> = emptyMap()
)

private data class PendingLessonQuestion(val item: LessonItem, val type: LessonQuestionType)

@HiltViewModel
class LessonViewModel @Inject constructor(
    private val assignmentRepository: AssignmentRepository,
    private val pitchAccentRepository: PitchAccentRepository,
    private val settingsRepository: SettingsRepository,
    private val subjectRepository: SubjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LessonUiState())
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    private val quizQueue = ArrayDeque<PendingLessonQuestion>()
    private val startedAssignmentIds = mutableSetOf<Long>()

    init {
        load()
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(showPitchAccent = settings.showPitchAccent) }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { LessonUiState(isLoading = true) }
            quizQueue.clear()
            startedAssignmentIds.clear()

            when (val result = assignmentRepository.refreshLessonQueue()) {
                is ApiResult.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                is ApiResult.Success -> {
                    val items = assignmentRepository.observeLessonQueue().first()
                        .sortedWith(compareBy({ it.level }, { it.subjectType.ordinal }, { it.assignmentId }))
                    if (items.isEmpty()) {
                        _uiState.update { it.copy(isLoading = false, hasNoLessonsAvailable = true) }
                    } else {
                        val defaultSelection = items.take(DEFAULT_LESSON_SELECTION_SIZE)
                            .map { it.assignmentId }
                            .toSet()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                phase = LessonPhase.SELECT,
                                availableLessons = items,
                                selectedAssignmentIds = defaultSelection
                            )
                        }
                    }
                }
            }
        }
    }

    fun toggleLessonSelection(assignmentId: Long) {
        _uiState.update { state ->
            val selected = state.selectedAssignmentIds.toMutableSet()
            if (!selected.add(assignmentId)) selected.remove(assignmentId)
            state.copy(selectedAssignmentIds = selected)
        }
    }

    fun selectFirst(n: Int) {
        _uiState.update { state ->
            state.copy(selectedAssignmentIds = state.availableLessons.take(n).map { it.assignmentId }.toSet())
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedAssignmentIds = state.availableLessons.map { it.assignmentId }.toSet())
        }
    }

    fun selectNone() {
        _uiState.update { it.copy(selectedAssignmentIds = emptySet()) }
    }

    fun startSelectedLessons() {
        val state = _uiState.value
        val selected = state.availableLessons.filter { it.assignmentId in state.selectedAssignmentIds }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val (pitchAccents, relatedSubjects) = coroutineScope {
                val pitchAccentsDeferred = async { fetchPitchAccents(selected) }
                val relatedSubjectsDeferred = async { fetchRelatedSubjects(selected) }
                pitchAccentsDeferred.await() to relatedSubjectsDeferred.await()
            }
            _uiState.update {
                it.copy(
                    phase = LessonPhase.STUDY,
                    studyItems = selected,
                    studyIndex = 0,
                    pitchAccentsBySubjectId = pitchAccents,
                    relatedSubjectsById = relatedSubjects
                )
            }
        }
    }

    /** Fanned out in parallel rather than sequentially, so a large "Select All" batch of vocabulary
     * items doesn't serialize dozens of individual pitch-accent lookups one after another. */
    private suspend fun fetchPitchAccents(items: List<LessonItem>): Map<Long, List<PitchAccent>> = coroutineScope {
        items
            .filter { (it.subjectType == SubjectType.VOCABULARY || it.subjectType == SubjectType.KANA_VOCABULARY) && it.characters != null }
            .map { item -> item.subjectId to async { pitchAccentRepository.observePitchAccents(item.characters!!).first() } }
            .associate { (subjectId, deferred) -> subjectId to deferred.await() }
    }

    /** One batch lookup for every related subject (radicals/kanji/visually-similar/used-in) across
     * the whole study set, so the glyphs the detail view shows for these can render on the study
     * card too without a per-tile network/DB round trip. */
    private suspend fun fetchRelatedSubjects(items: List<LessonItem>): Map<Long, SubjectSummary> {
        val relatedIds = items
            .flatMap { it.componentSubjectIds + it.amalgamationSubjectIds + it.visuallySimilarSubjectIds }
            .distinct()
        if (relatedIds.isEmpty()) return emptyMap()
        return subjectRepository.observeSubjectSummaries(relatedIds).first().associateBy { it.subjectId }
    }

    fun onStudyCardSwiped(index: Int) {
        val state = _uiState.value
        if (state.phase != LessonPhase.STUDY || index !in state.studyItems.indices) return
        _uiState.update { it.copy(studyIndex = index) }
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

    /** Meaning answers pool the primary meanings with WaniKani's own whitelist synonyms — both are
     *  equally acceptable. Reading answers stay exact-match-only, so no auxiliary readings exist. */
    private fun candidatesFor(item: LessonItem, type: LessonQuestionType): List<String> =
        if (type == LessonQuestionType.MEANING) item.meanings + item.auxiliaryMeanings else item.readings

    fun submitAnswer() {
        val state = _uiState.value
        if (state.feedback != null) return
        val item = state.currentQuizItem ?: return
        val type = state.currentQuestionType ?: return
        if (state.answerInput.isBlank()) return

        val candidates = candidatesFor(item, type)
        if (type == LessonQuestionType.MEANING) {
            val match = CloseEnoughMatcher.match(state.answerInput, candidates)
            gradeAnswer(item, type, match.isMatch, candidates, wasCloseMatch = match.isMatch && !match.isExact)
        } else {
            val normalizedAnswer = convertReadingSafely(state.answerInput.trim())
            val isCorrect = candidates.any { it.trim().equals(normalizedAnswer, ignoreCase = true) }
            gradeAnswer(item, type, isCorrect, candidates)
        }
    }

    /** Never lets a malformed answer crash grading — falls back to the raw (untranslated) text. */
    private fun convertReadingSafely(rawAnswer: String): String =
        try {
            RomajiConverter.toHiragana(rawAnswer)
        } catch (e: Exception) {
            rawAnswer
        }

    /** Gives up on the current question — treated the same as a wrong answer, requeued for another pass. */
    fun dontKnowAnswer() {
        val state = _uiState.value
        if (state.feedback != null) return
        val item = state.currentQuizItem ?: return
        val type = state.currentQuestionType ?: return
        val candidates = candidatesFor(item, type)
        gradeAnswer(item, type, isCorrect = false, candidates)
    }

    private fun gradeAnswer(
        item: LessonItem,
        type: LessonQuestionType,
        isCorrect: Boolean,
        candidates: List<String>,
        wasCloseMatch: Boolean = false
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
                feedback = LessonAnswerFeedback(isCorrect, candidates.joinToString(", "), wasCloseMatch, candidates.size),
                remainingQuizCount = quizQueue.size
            )
        }
    }

    private fun markStarted(item: LessonItem) {
        if (!startedAssignmentIds.add(item.assignmentId)) return
        viewModelScope.launch {
            val result = assignmentRepository.startAssignment(item.assignmentId)
            if (result is ApiResult.Success) {
                // Every lesson item starts the same way — locked/unstarted straight to Apprentice I —
                // so unlike a review's SRS-stage change, no server round-trip data is needed to know
                // the transition; only whether it actually succeeded.
                _uiState.update { it.copy(rankChange = RankChange(SrsStage.LOCKED, SrsStage.APPRENTICE_1)) }
            }
        }
    }

    fun onContinue() {
        advanceQuiz()
    }

    private fun advanceQuiz() {
        val next = quizQueue.firstOrNull()
        if (next == null) {
            _uiState.update {
                it.copy(isSessionComplete = true, currentQuizItem = null, currentQuestionType = null, rankChange = null)
            }
            return
        }
        _uiState.update {
            it.copy(
                currentQuizItem = next.item,
                currentQuestionType = next.type,
                answerInput = "",
                feedback = null,
                rankChange = null,
                remainingQuizCount = quizQueue.size
            )
        }
    }
}
