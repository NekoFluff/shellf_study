package com.crazyfluff.shellfstudy.feature.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.core.audio.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.core.audio.selectAudioFor
import com.crazyfluff.shellfstudy.core.coroutines.ApplicationScope
import com.crazyfluff.shellfstudy.core.coroutines.runDurably
import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.core.data.LessonSessionRepository
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.core.data.PersistedLessonItemProgress
import com.crazyfluff.shellfstudy.core.data.PersistedLessonPhase
import com.crazyfluff.shellfstudy.core.data.PersistedLessonQuestion
import com.crazyfluff.shellfstudy.core.data.PersistedLessonSession
import com.crazyfluff.shellfstudy.core.data.PitchAccentRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.SubjectRepository
import com.crazyfluff.shellfstudy.shared.data.model.LessonItem
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.shared.data.StrokeOrderRepository
import com.crazyfluff.shellfstudy.core.designsystem.strokeorder.StrokeOrderUiState
import com.crazyfluff.shellfstudy.core.lifecycle.AppForegroundTracker
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.crazyfluff.shellfstudy.core.quiz.AnswerFeedback
import com.crazyfluff.shellfstudy.core.quiz.AnswerOutcome
import com.crazyfluff.shellfstudy.core.quiz.QuestionType
import com.crazyfluff.shellfstudy.core.quiz.PendingQuestion
import com.crazyfluff.shellfstudy.core.quiz.QuizGradingGuard
import com.crazyfluff.shellfstudy.core.quiz.QuizQueue
import com.crazyfluff.shellfstudy.core.quiz.candidatesFor
import com.crazyfluff.shellfstudy.core.quiz.evaluateAnswer
import com.crazyfluff.shellfstudy.core.quiz.questionTypesFor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Default number of lessons pre-selected on the picker, matching WaniKani's own default batch size. */
private const val DEFAULT_LESSON_SELECTION_SIZE = 5

enum class LessonPhase { SELECT, STUDY, QUIZ }

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
    val currentQuestionType: QuestionType? = null,
    val answerInput: String = "",
    val feedback: AnswerFeedback? = null,
    val answerTypeMismatchCount: Int = 0,
    val totalQuizCount: Int = 0,
    val remainingQuizCount: Int = 0,
    val isSessionComplete: Boolean = false,
    val isAbandoned: Boolean = false,
    val showPitchAccent: Boolean = true,
    val showSubjectTypeLabel: Boolean = false,
    val showTotalTimer: Boolean = false,
    val showQuestionTimer: Boolean = false,
    // Active time accumulated before the current viewing segment, and (while non-null) when that
    // segment began — see PausableElapsedTimeText and LessonViewModel's activeElapsedMs /
    // activeSegmentStartMs, which these mirror exactly. Segment goes null while the session isn't
    // actively being viewed (app backgrounded, or navigated off-screen), freezing the total timer
    // instead of letting it count straight through that gap.
    val sessionActiveElapsedMs: Long = 0L,
    val sessionActiveSegmentStartMs: Long? = null,
    val questionStartTimeMs: Long? = null,
    // Non-null once the current question has been answered — freezes the "time on this question"
    // display at this value instead of letting it keep ticking through the feedback screen. Reset
    // to null whenever a fresh, unanswered question is shown (see beginQuiz, advanceQuiz).
    val questionElapsedMs: Long? = null,
    val pitchAccentsBySubjectId: Map<Long, List<PitchAccent>> = emptyMap(),
    val relatedSubjectsById: Map<Long, SubjectSummary> = emptyMap(),
    val strokeOrderBySubjectId: Map<Long, StrokeOrderUiState> = emptyMap(),
    val sessionItemsLearned: Int = 0,
    val sessionItemsCorrectFirstTry: Int = 0,
    val sessionMissedItems: List<LessonItem> = emptyList(),
    val sessionTotalElapsedMs: Long = 0L,
    val sessionAverageTimePerItemMs: Long = 0L,
    val sessionSlowestAnswers: List<LessonSlowAnswer> = emptyList()
)

data class LessonSlowAnswer(val item: LessonItem, val type: QuestionType, val elapsedMs: Long, val isCorrect: Boolean)

private class LessonItemProgress(val item: LessonItem) {
    var meaningDone = false
    var readingDone = false
    var hadIncorrectMeaning = false
    var hadIncorrectReading = false
}

private data class LessonAnsweredQuestionRecord(val item: LessonItem, val type: QuestionType, val isCorrect: Boolean, val elapsedMs: Long)

@HiltViewModel
class LessonViewModel @Inject constructor(
    private val assignmentRepository: AssignmentRepository,
    private val outboxRepository: OutboxRepository,
    private val lessonSessionRepository: LessonSessionRepository,
    private val pitchAccentRepository: PitchAccentRepository,
    private val settingsRepository: SettingsRepository,
    private val subjectRepository: SubjectRepository,
    private val strokeOrderRepository: StrokeOrderRepository,
    private val pronunciationAudioPlayer: PronunciationAudioPlayer,
    private val appForegroundTracker: AppForegroundTracker,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(LessonUiState())
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    private val quizQueue = QuizQueue<LessonItem>()
    private val startedAssignmentIds = mutableSetOf<Long>()
    private var totalQuizCount = 0

    private val gradingGuard = QuizGradingGuard(viewModelScope)

    private val progressByAssignmentId = mutableMapOf<Long, LessonItemProgress>()
    // Individual per-answer records (used for the "slowest answers" summary) stay in-memory only —
    // a resume starts this list fresh, so that card only reflects answers given since the most
    // recent resume. activeElapsedMs, by contrast, is restored from persisted state on resume (see
    // resumeQuizPhase) so the total/average time summaries stay accurate across a resume.
    private val answeredQuestions = mutableListOf<LessonAnsweredQuestionRecord>()

    // Together, these track only the time the quiz was actively being viewed: activeElapsedMs is
    // the accumulated total as of the end of the last viewing segment, and activeSegmentStartMs —
    // non-null exactly while actively viewing — is when the current one began. currentActiveElapsedMs
    // combines them; see it, resumeActiveSegment, and pauseActiveSegment for how the two ever change.
    private var activeElapsedMs: Long = 0L
    private var activeSegmentStartMs: Long? = null
    private var questionShownAtMs: Long = 0L

    init {
        loadOrResume()
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        showPitchAccent = settings.showPitchAccent,
                        showSubjectTypeLabel = settings.showSubjectTypeLabel,
                        showTotalTimer = settings.showTotalTimer,
                        showQuestionTimer = settings.showQuestionTimer
                    )
                }
            }
        }
        // The initial value is handled by beginQuiz/resumeQuizPhase/resumeActiveSegment below instead
        // — dropped here so a ViewModel created while already in the foreground (the overwhelmingly
        // common case) doesn't publish a redundant extra uiState emission for it.
        viewModelScope.launch {
            appForegroundTracker.isForeground.drop(1).collect { isForeground ->
                if (isForeground) resumeActiveSegment() else pauseActiveSegment()
            }
        }
    }

    /** Explicit fresh fetch — bound to the error screen's retry action, so it always discards any
     *  persisted quiz-in-progress rather than resuming a session that may be what's broken. */
    fun load() {
        viewModelScope.launch {
            _uiState.update { LessonUiState(isLoading = true) }
            assignmentRepository.warmSrsSystemCache()
            lessonSessionRepository.clear()
            fetchFreshQueue()
        }
    }

    /** Resumes a persisted in-progress quiz if one exists, otherwise fetches a fresh queue. */
    private fun loadOrResume() {
        viewModelScope.launch {
            _uiState.update { LessonUiState(isLoading = true) }
            // Warmed once here, during the loading spinner, so applyOptimisticLessonStart can
            // resolve the SRS system for each item started during this session with zero DB
            // round trips.
            assignmentRepository.warmSrsSystemCache()
            val persisted = lessonSessionRepository.load()
            if (persisted != null) {
                resumeFromPersisted(persisted)
            } else {
                fetchFreshQueue()
            }
        }
    }

    private suspend fun resumeFromPersisted(persisted: PersistedLessonSession) {
        when (persisted.phase) {
            PersistedLessonPhase.STUDY -> resumeStudyPhase(persisted)
            PersistedLessonPhase.QUIZ -> resumeQuizPhase(persisted)
        }
    }

    /** Resumes a session left mid-flashcard-study — after "Start session" but before the last card
     *  hands off to the quiz. Reconstructs the same study batch, in the same order, and jumps back
     *  to the card the user was on, rather than forcing lesson re-selection and restudying from the
     *  first card, the same way [resumeQuizPhase] avoids re-fetching a fresh quiz queue. */
    private suspend fun resumeStudyPhase(persisted: PersistedLessonSession) {
        val itemsById = assignmentRepository.observeLessonQueue().first().associateBy { it.assignmentId }

        // The cache backing this persisted session is gone, or somehow nothing was actually
        // selected — fall back to a fresh fetch rather than show a broken study session.
        if (persisted.studyAssignmentIds.isEmpty() || persisted.studyAssignmentIds.any { it !in itemsById }) {
            lessonSessionRepository.clear()
            fetchFreshQueue()
            return
        }

        val items = persisted.studyAssignmentIds.map { itemsById.getValue(it) }
        val (pitchAccents, relatedSubjects, strokeOrders) = coroutineScope {
            val pitchAccentsDeferred = async { fetchPitchAccents(items) }
            val relatedSubjectsDeferred = async { fetchRelatedSubjects(items) }
            val strokeOrdersDeferred = async { fetchStrokeOrders(items) }
            Triple(pitchAccentsDeferred.await(), relatedSubjectsDeferred.await(), strokeOrdersDeferred.await())
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                phase = LessonPhase.STUDY,
                studyItems = items,
                studyIndex = persisted.studyIndex.coerceIn(0, items.lastIndex),
                pitchAccentsBySubjectId = pitchAccents,
                relatedSubjectsById = relatedSubjects,
                strokeOrderBySubjectId = strokeOrders
            )
        }
    }

    private suspend fun resumeQuizPhase(persisted: PersistedLessonSession) {
        val itemsById = assignmentRepository.observeLessonQueue().first().associateBy { it.assignmentId }

        // The cache backing this persisted session is gone (e.g. app storage was cleared) — fall
        // back to a fresh fetch rather than show a broken quiz.
        if (persisted.quizQueue.any { it.assignmentId !in itemsById }) {
            lessonSessionRepository.clear()
            fetchFreshQueue()
            return
        }

        quizQueue.restore(
            persisted.quizQueue.map { entry ->
                PendingQuestion(itemsById.getValue(entry.assignmentId), QuestionType.valueOf(entry.questionType))
            }
        )
        startedAssignmentIds.clear()
        progressByAssignmentId.clear()
        persisted.progress.forEach { p ->
            val item = itemsById[p.assignmentId] ?: return@forEach
            progressByAssignmentId[p.assignmentId] = LessonItemProgress(item).apply {
                meaningDone = p.meaningDone
                readingDone = p.readingDone
                hadIncorrectMeaning = p.hadIncorrectMeaning
                hadIncorrectReading = p.hadIncorrectReading
            }
        }
        answeredQuestions.clear()
        // Restores the quiz's accumulated active time rather than restarting the clock — this is
        // deliberately *not* wall-clock time since the quiz began; time spent away (backgrounded, or
        // navigated off and back) must not count. resumeActiveSegment then starts a fresh viewing
        // segment on top of that restored base, so the clock resumes right where it left off.
        activeElapsedMs = persisted.sessionActiveElapsedMs
        resumeActiveSegment()
        questionShownAtMs = System.currentTimeMillis()
        totalQuizCount = persisted.totalQuizCount
        val next = quizQueue.current
        // A persisted queue is only ever written mid-quiz (see advanceQuiz's completion branch,
        // which clears it), so next == null here is an unreachable edge case in practice — handled
        // defensively anyway, mirroring ReviewViewModel.resumeFromPersisted's equivalent.
        val summary = if (next == null) sessionSummary() else null
        _uiState.update {
            it.copy(
                isLoading = false,
                phase = LessonPhase.QUIZ,
                totalQuizCount = totalQuizCount,
                remainingQuizCount = quizQueue.size,
                currentQuizItem = next?.item,
                currentQuestionType = next?.type,
                isSessionComplete = next == null,
                sessionActiveElapsedMs = activeElapsedMs,
                sessionActiveSegmentStartMs = activeSegmentStartMs,
                questionStartTimeMs = questionShownAtMs,
                questionElapsedMs = null,
                sessionItemsLearned = summary?.itemsLearned ?: it.sessionItemsLearned,
                sessionItemsCorrectFirstTry = summary?.correctFirstTry ?: it.sessionItemsCorrectFirstTry,
                sessionMissedItems = summary?.missedItems ?: it.sessionMissedItems,
                sessionTotalElapsedMs = summary?.totalElapsedMs ?: it.sessionTotalElapsedMs,
                sessionAverageTimePerItemMs = summary?.averageTimePerItemMs ?: it.sessionAverageTimePerItemMs,
                sessionSlowestAnswers = summary?.slowestAnswers ?: it.sessionSlowestAnswers
            )
        }
    }

    private suspend fun fetchFreshQueue() {
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
            val (pitchAccents, relatedSubjects, strokeOrders) = coroutineScope {
                val pitchAccentsDeferred = async { fetchPitchAccents(selected) }
                val relatedSubjectsDeferred = async { fetchRelatedSubjects(selected) }
                val strokeOrdersDeferred = async { fetchStrokeOrders(selected) }
                Triple(pitchAccentsDeferred.await(), relatedSubjectsDeferred.await(), strokeOrdersDeferred.await())
            }
            _uiState.update {
                it.copy(
                    phase = LessonPhase.STUDY,
                    studyItems = selected,
                    studyIndex = 0,
                    pitchAccentsBySubjectId = pitchAccents,
                    relatedSubjectsById = relatedSubjects,
                    strokeOrderBySubjectId = strokeOrders
                )
            }
            applicationScope.runDurably { persistStudySnapshot(selected, 0) }
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

    /** Stroke data is keyed purely by character (same lookup [SubjectDetailViewModel] uses), so only
     *  single-glyph items — kanji, and any radical with a real Unicode glyph — resolve to anything
     *  other than [StrokeOrderUiState.Unavailable]. Fanned out in parallel for the same reason
     *  [fetchPitchAccents] is: a large study batch shouldn't serialize dozens of lookups. */
    private suspend fun fetchStrokeOrders(items: List<LessonItem>): Map<Long, StrokeOrderUiState> = coroutineScope {
        items
            .mapNotNull { item -> item.characters?.singleOrNull()?.let { item.subjectId to it } }
            .map { (subjectId, character) ->
                subjectId to async {
                    strokeOrderRepository.getStrokeOrder(character)?.let { StrokeOrderUiState.Available(it) }
                        ?: StrokeOrderUiState.Unavailable
                }
            }
            .associate { (subjectId, deferred) -> subjectId to deferred.await() }
    }

    fun onStudyCardSwiped(index: Int) {
        val state = _uiState.value
        if (state.phase != LessonPhase.STUDY || index !in state.studyItems.indices) return
        _uiState.update { it.copy(studyIndex = index) }
        viewModelScope.launch { applicationScope.runDurably { persistStudySnapshot(state.studyItems, index) } }
    }

    fun nextStudyCard() {
        val state = _uiState.value
        if (state.phase != LessonPhase.STUDY) return
        val nextIndex = state.studyIndex + 1
        if (nextIndex >= state.studyItems.size) {
            viewModelScope.launch { beginQuiz(state.studyItems) }
        } else {
            _uiState.update { it.copy(studyIndex = nextIndex) }
            viewModelScope.launch { applicationScope.runDurably { persistStudySnapshot(state.studyItems, nextIndex) } }
        }
    }

    fun previousStudyCard() {
        val state = _uiState.value
        if (state.phase != LessonPhase.STUDY || state.studyIndex == 0) return
        val previousIndex = state.studyIndex - 1
        _uiState.update { it.copy(studyIndex = previousIndex) }
        viewModelScope.launch { applicationScope.runDurably { persistStudySnapshot(state.studyItems, previousIndex) } }
    }

    /** Persists just enough to resume mid-flashcard-study: which items are in the batch (in order)
     *  and which card the user is on — see [resumeStudyPhase]. Called on every card change rather
     *  than only at study's start, so a resume lands on the exact card left off on, not card one. */
    private suspend fun persistStudySnapshot(items: List<LessonItem>, index: Int) {
        lessonSessionRepository.save(
            PersistedLessonSession(
                phase = PersistedLessonPhase.STUDY,
                studyAssignmentIds = items.map { it.assignmentId },
                studyIndex = index
            )
        )
    }

    private suspend fun beginQuiz(items: List<LessonItem>) {
        quizQueue.build(items, typesFor = { item -> questionTypesFor(item.subjectType) })
        totalQuizCount = quizQueue.size

        progressByAssignmentId.clear()
        items.forEach { item -> progressByAssignmentId[item.assignmentId] = LessonItemProgress(item) }
        answeredQuestions.clear()
        activeElapsedMs = 0L
        resumeActiveSegment()
        questionShownAtMs = System.currentTimeMillis()

        val next = quizQueue.current
        _uiState.update {
            it.copy(
                phase = LessonPhase.QUIZ,
                totalQuizCount = totalQuizCount,
                remainingQuizCount = totalQuizCount,
                currentQuizItem = next?.item,
                currentQuestionType = next?.type,
                answerInput = "",
                feedback = null,
                isSessionComplete = next == null,
                sessionActiveElapsedMs = activeElapsedMs,
                sessionActiveSegmentStartMs = activeSegmentStartMs,
                questionStartTimeMs = questionShownAtMs,
                questionElapsedMs = null
            )
        }
        applicationScope.runDurably { persistCurrentState() }
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

        gradingGuard.launchIfIdle {
            val candidates = candidatesFor(item.meanings, item.auxiliaryMeanings, item.readings, type)
            when (val outcome = evaluateAnswer(state.answerInput, type, item.meanings, item.auxiliaryMeanings, item.readings)) {
                AnswerOutcome.TypeMismatch ->
                    _uiState.update { it.copy(answerTypeMismatchCount = it.answerTypeMismatchCount + 1) }
                is AnswerOutcome.Graded ->
                    gradeAnswer(item, type, outcome.isCorrect, candidates, wasCloseMatch = outcome.wasCloseMatch)
            }
        }
    }

    /** Gives up on the current question — treated the same as a wrong answer, requeued for another pass. */
    fun dontKnowAnswer() {
        val state = _uiState.value
        if (state.feedback != null) return
        val item = state.currentQuizItem ?: return
        val type = state.currentQuestionType ?: return
        val candidates = candidatesFor(item.meanings, item.auxiliaryMeanings, item.readings, type)
        gradingGuard.launchIfIdle {
            gradeAnswer(item, type, isCorrect = false, candidates)
        }
    }

    private suspend fun gradeAnswer(
        item: LessonItem,
        type: QuestionType,
        isCorrect: Boolean,
        candidates: List<String>,
        wasCloseMatch: Boolean = false
    ) {
        val itemProgress = progressByAssignmentId.getOrPut(item.assignmentId) { LessonItemProgress(item) }
        val questionElapsedMs = System.currentTimeMillis() - questionShownAtMs
        answeredQuestions.add(LessonAnsweredQuestionRecord(item, type, isCorrect, questionElapsedMs))

        quizQueue.removeCurrent()
        val justCompletedItem = if (!isCorrect) {
            when (type) {
                QuestionType.MEANING -> itemProgress.hadIncorrectMeaning = true
                QuestionType.READING -> itemProgress.hadIncorrectReading = true
            }
            quizQueue.requeue(PendingQuestion(item, type))
            false
        } else {
            when (type) {
                QuestionType.MEANING -> itemProgress.meaningDone = true
                QuestionType.READING -> itemProgress.readingDone = true
            }
            // No more pending questions for this item — it's been answered correctly on every
            // question type it has, so the lesson for it is done.
            quizQueue.noneMatches { it.item.assignmentId == item.assignmentId }
        }

        // Snapshotted synchronously, right after mutating quizQueue above, so the detached
        // durability write below can safely run concurrently with the next question's own
        // grading/advance — quizQueue is a plain, non-thread-safe collection, and once feedback
        // is visible the user is free to act immediately.
        val snapshot = currentPersistSnapshot()

        // startedAssignmentIds.add(...) is the idempotency guard (an item should only ever be
        // marked started once) — computed once so both the optimistic patch below and the outbox
        // enqueue afterward agree on whether this is really a first-time completion.
        val isNewlyStarted = justCompletedItem && startedAssignmentIds.add(item.assignmentId)

        _uiState.update {
            it.copy(
                feedback = AnswerFeedback(isCorrect, candidates.joinToString(", "), wasCloseMatch, candidates.size),
                remainingQuizCount = quizQueue.size,
                // Freezes the "time on this question" display the instant feedback appears, rather
                // than letting it keep ticking while the feedback/Continue screen is up — matches
                // the elapsedMs recorded for the slowest-answers summary above, stamped at this
                // same moment.
                questionElapsedMs = questionElapsedMs
            )
        }

        val settings = settingsRepository.settings.first()
        if (type == QuestionType.READING && settings.autoplayPronunciationAudio) {
            candidates.firstOrNull()?.let { reading ->
                selectAudioFor(item.pronunciationAudios, reading, mp3Only = settings.restrictAudioToMp3)
                    ?.let(pronunciationAudioPlayer::play)
            }
        }

        persistDurabilityWork(isNewlyStarted, item, snapshot)
    }

    /** Manual play from the study card's reading row — mirrors SubjectDetailViewModel.playReading. */
    fun playReading(item: LessonItem, reading: String) {
        viewModelScope.launch {
            val restrictAudioToMp3 = settingsRepository.settings.first().restrictAudioToMp3
            selectAudioFor(item.pronunciationAudios, reading, mp3Only = restrictAudioToMp3)
                ?.let(pronunciationAudioPlayer::play)
        }
    }

    /** Captures the current quiz queue as an immutable, ready-to-persist value — safe to hold
     *  across a suspension point even if the live quizQueue is mutated by something else
     *  afterward (see [gradeAnswer]'s deferred [persistDurabilityWork] call). */
    private fun currentPersistSnapshot(): PersistedLessonSession = PersistedLessonSession(
        phase = PersistedLessonPhase.QUIZ,
        quizQueue = quizQueue.toList().map { PersistedLessonQuestion(it.item.assignmentId, it.type.name) },
        progress = progressByAssignmentId.map { (id, p) ->
            PersistedLessonItemProgress(id, p.meaningDone, p.readingDone, p.hadIncorrectMeaning, p.hadIncorrectReading)
        },
        totalQuizCount = totalQuizCount,
        sessionActiveElapsedMs = currentActiveElapsedMs()
    )

    private suspend fun persistSnapshot(snapshot: PersistedLessonSession) {
        lessonSessionRepository.save(snapshot)
    }

    private suspend fun persistCurrentState() {
        persistSnapshot(currentPersistSnapshot())
    }

    /** Runs the post-grading durability writes (outbox enqueue, session persistence) — see
     *  [runDurably] for why this needs [applicationScope] rather than `viewModelScope`. */
    private suspend fun persistDurabilityWork(isNewlyStarted: Boolean, item: LessonItem, snapshot: PersistedLessonSession) {
        applicationScope.runDurably {
            if (isNewlyStarted) {
                assignmentRepository.applyOptimisticLessonStart(item.assignmentId, item.srsSystemId)
                outboxRepository.enqueueLessonStart(item.assignmentId, item.subjectId)
            }
            persistSnapshot(snapshot)
        }
    }

    fun onContinue() {
        viewModelScope.launch { advanceQuiz() }
    }

    /** Discards a persisted in-progress lesson session (study or quiz) and exits — a clean slate
     *  next time. Mirrors ReviewViewModel.abandonSession. */
    fun abandonSession() {
        viewModelScope.launch {
            lessonSessionRepository.clear()
            _uiState.update { it.copy(isAbandoned = true) }
        }
    }

    /** The single source of truth for "how long has this quiz actually been viewed" — both the
     *  live-ticking total timer (via [PausableElapsedTimeText] reading the mirrored uiState fields)
     *  and [sessionSummary]'s final total derive from this same formula over the same two fields, so
     *  they can never disagree. */
    private fun currentActiveElapsedMs(nowMs: Long = System.currentTimeMillis()): Long =
        activeElapsedMs + (activeSegmentStartMs?.let { nowMs - it } ?: 0L)

    /** Starts a fresh viewing segment — called once the quiz is actually being looked at (right
     *  after beginning/resuming it, and whenever [appForegroundTracker] reports the app came back to
     *  the foreground). Idempotent: a no-op if a segment is already running. */
    private fun resumeActiveSegment() {
        if (activeSegmentStartMs != null) return
        val now = System.currentTimeMillis()
        activeSegmentStartMs = now
        _uiState.update { it.copy(sessionActiveSegmentStartMs = now) }
    }

    /** Folds the current viewing segment into the accumulated total and stops the clock — called
     *  when the quiz is no longer being actively viewed ([appForegroundTracker] reports the app
     *  backgrounded, or this ViewModel is cleared because the user navigated away). Persists via
     *  [applicationScope] rather than [viewModelScope] since the latter may already be in the
     *  process of being cancelled by the time this runs (see [onCleared]). Idempotent: a no-op if no
     *  segment is running (including during the STUDY phase, before the quiz clock has started). */
    private fun pauseActiveSegment() {
        val startedAt = activeSegmentStartMs ?: return
        activeElapsedMs += System.currentTimeMillis() - startedAt
        activeSegmentStartMs = null
        _uiState.update { it.copy(sessionActiveElapsedMs = activeElapsedMs, sessionActiveSegmentStartMs = null) }
        // The quiz-complete branch already cleared lessonSessionRepository once the session
        // finished — re-persisting here (this fires from onCleared when the user navigates off the
        // complete screen, or from the app backgrounding while still on it) would resurrect a
        // stale, empty-queue "active session" record. The dashboard would then offer to resume a
        // 0-lesson session that, once opened, immediately re-completes.
        if (_uiState.value.isSessionComplete) return
        applicationScope.launch { persistCurrentState() }
    }

    override fun onCleared() {
        super.onCleared()
        pauseActiveSegment()
    }

    private data class LessonSessionSummary(
        val itemsLearned: Int,
        val correctFirstTry: Int,
        val missedItems: List<LessonItem>,
        val totalElapsedMs: Long,
        val averageTimePerItemMs: Long,
        val slowestAnswers: List<LessonSlowAnswer>
    )

    /** Items learned, how many were correct without ever missing, which were missed at least once,
     *  and timing — mirrors ReviewViewModel.sessionSummary(). "Missed" here means at least one wrong
     *  attempt during the quiz, not a real SRS miss — every lesson item is requeued until correct. */
    private fun sessionSummary(): LessonSessionSummary {
        val itemsLearned = progressByAssignmentId.size
        val correctFirstTry = progressByAssignmentId.values.count { !it.hadIncorrectMeaning && !it.hadIncorrectReading }
        val missedItems = progressByAssignmentId.values
            .filter { it.hadIncorrectMeaning || it.hadIncorrectReading }
            .map { it.item }
        val totalElapsedMs = currentActiveElapsedMs()
        val averageTimePerItemMs = if (itemsLearned == 0) 0L else totalElapsedMs / itemsLearned
        val slowestAnswers = answeredQuestions.sortedByDescending { it.elapsedMs }.take(5)
            .map { LessonSlowAnswer(it.item, it.type, it.elapsedMs, it.isCorrect) }
        return LessonSessionSummary(
            itemsLearned = itemsLearned,
            correctFirstTry = correctFirstTry,
            missedItems = missedItems,
            totalElapsedMs = totalElapsedMs,
            averageTimePerItemMs = averageTimePerItemMs,
            slowestAnswers = slowestAnswers
        )
    }

    private suspend fun advanceQuiz() {
        val next = quizQueue.current
        if (next == null) {
            applicationScope.runDurably { lessonSessionRepository.clear() }
            val summary = sessionSummary()
            _uiState.update {
                it.copy(
                    isSessionComplete = true,
                    currentQuizItem = null,
                    currentQuestionType = null,
                    sessionItemsLearned = summary.itemsLearned,
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
                currentQuizItem = next.item,
                currentQuestionType = next.type,
                answerInput = "",
                feedback = null,
                remainingQuizCount = quizQueue.size,
                questionStartTimeMs = questionShownAtMs,
                questionElapsedMs = null
            )
        }
    }
}
