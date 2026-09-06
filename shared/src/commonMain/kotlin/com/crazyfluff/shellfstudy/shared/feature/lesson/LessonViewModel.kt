package com.crazyfluff.shellfstudy.shared.feature.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.audio.selectAudioFor
import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.AppSettings
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.LastSessionKind
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummary
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.PersistedAnsweredQuestion
import com.crazyfluff.shellfstudy.shared.data.PersistedItemProgress
import com.crazyfluff.shellfstudy.shared.data.PersistedLessonPhase
import com.crazyfluff.shellfstudy.shared.data.PersistedQuestion
import com.crazyfluff.shellfstudy.shared.data.PersistedLessonSession
import com.crazyfluff.shellfstudy.shared.data.PitchAccentRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.SubjectRepository
import com.crazyfluff.shellfstudy.shared.data.model.LessonItem
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.data.model.RankChange
import com.crazyfluff.shellfstudy.shared.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.shared.data.StrokeOrderRepository
import com.crazyfluff.shellfstudy.shared.designsystem.strokeorder.StrokeOrderUiState
import com.crazyfluff.shellfstudy.shared.lifecycle.AppForegroundTracker
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.crazyfluff.shellfstudy.shared.quiz.AnsweredQuestionRecord
import com.crazyfluff.shellfstudy.shared.quiz.QuizItemProgress
import com.crazyfluff.shellfstudy.shared.quiz.AnswerFeedback
import com.crazyfluff.shellfstudy.shared.quiz.AnswerOutcome
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType
import com.crazyfluff.shellfstudy.shared.quiz.PendingQuestion
import com.crazyfluff.shellfstudy.shared.quiz.QuizGradingGuard
import com.crazyfluff.shellfstudy.shared.quiz.QuizQueue
import com.crazyfluff.shellfstudy.shared.quiz.QuizSessionSummary
import com.crazyfluff.shellfstudy.shared.quiz.QuizSessionTiming
import com.crazyfluff.shellfstudy.shared.quiz.QuizTimingUiState
import com.crazyfluff.shellfstudy.shared.quiz.SlowAnswer
import com.crazyfluff.shellfstudy.shared.quiz.candidatesFor
import com.crazyfluff.shellfstudy.shared.quiz.evaluateAnswer
import com.crazyfluff.shellfstudy.shared.quiz.questionTypesFor
import com.crazyfluff.shellfstudy.shared.quiz.summarizeQuizSession
import com.crazyfluff.shellfstudy.shared.quiz.toSessionAnswerRow
import com.crazyfluff.shellfstudy.shared.quiz.toSessionMissedItemRow
import com.crazyfluff.shellfstudy.shared.quiz.undoLastIncorrectAnswer
import com.crazyfluff.shellfstudy.shared.session.QuizSessionController
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Default number of lessons pre-selected on the picker, matching WaniKani's own default batch size. */
private const val DEFAULT_LESSON_SELECTION_SIZE = 5

data class LessonUiState(
    val phase: Phase = Phase.Loading,
    val settings: DisplaySettings = DisplaySettings(),
    // Deliberately not folded into Phase — abandoning is a one-shot navigation signal, not a
    // rendering mode. The screen keeps rendering whatever phase was showing for one more frame
    // while a LaunchedEffect(isAbandoned) fires the actual back-navigation; the abandon menu item
    // that triggers this is already only shown while canManageSession is true (never once
    // phase is Complete), so this can't combine with Complete in practice.
    val isAbandoned: Boolean = false
) {
    /** Settings-derived display flags — hoisted here rather than duplicated into every [Phase]
     *  variant, since they apply uniformly regardless of phase (never null/absent in one phase and
     *  required in another) and are only ever read, never used to decide which phase to render. */
    data class DisplaySettings(
        val showPitchAccent: Boolean = true,
        val showSubjectTypeLabel: Boolean = false,
        val showTotalTimer: Boolean = false,
        val showQuestionTimer: Boolean = false,
        val useJapaneseKeyboard: Boolean = false
    )

    sealed interface Phase {
        data object Loading : Phase
        data class Error(val message: String) : Phase
        data object NoLessonsAvailable : Phase

        data class Select(
            val availableLessons: List<LessonItem> = emptyList(),
            val selectedAssignmentIds: Set<Long> = emptySet()
        ) : Phase

        data class Study(
            val studyItems: List<LessonItem> = emptyList(),
            val studyIndex: Int = 0,
            val pitchAccentsBySubjectId: Map<Long, List<PitchAccent>> = emptyMap(),
            val relatedSubjectsById: Map<Long, SubjectSummary> = emptyMap(),
            val strokeOrderBySubjectId: Map<Long, StrokeOrderUiState> = emptyMap()
        ) : Phase

        data class Quiz(
            // Non-nullable by construction — a next-question-or-complete decision always branches
            // into either a Quiz with a real item, or straight into Complete; there's no way to
            // construct a Quiz value with nothing to show.
            val currentItem: LessonItem,
            val currentQuestionType: QuestionType,
            val answerInput: String = "",
            val feedback: AnswerFeedback? = null,
            val rankChange: RankChange? = null,
            val undoCounter: Int = 0,
            val isDetailsExpanded: Boolean = false,
            val answerTypeMismatchCount: Int = 0,
            val totalQuizCount: Int = 0,
            val remainingQuizCount: Int = 0,
            val timing: QuizTimingUiState = QuizTimingUiState()
        ) : Phase

        data class Complete(
            val sessionItemsLearned: Int = 0,
            val sessionItemsCorrectFirstTry: Int = 0,
            val sessionMissedItems: List<LessonItem> = emptyList(),
            val sessionTotalElapsedMs: Long = 0L,
            val sessionAverageTimePerItemMs: Long = 0L,
            val sessionSlowestAnswers: List<SlowAnswer<LessonItem>> = emptyList()
        ) : Phase
    }
}

private fun QuizSessionSummary<LessonItem>.toCompletePhase() = LessonUiState.Phase.Complete(
    sessionItemsLearned = itemsCount,
    sessionItemsCorrectFirstTry = correctFirstTry,
    sessionMissedItems = missedItems,
    sessionTotalElapsedMs = totalElapsedMs,
    sessionAverageTimePerItemMs = averageTimePerItemMs,
    sessionSlowestAnswers = slowestAnswers
)

private typealias LessonItemProgress = QuizItemProgress<LessonItem>

class LessonViewModel(
    private val assignmentRepository: AssignmentRepository,
    private val statsRepository: StatsRepository,
    private val outboxRepository: OutboxRepository,
    private val sessionController: QuizSessionController<PersistedLessonSession>,
    private val lastSessionSummaryRepository: LastSessionSummaryRepository,
    private val pitchAccentRepository: PitchAccentRepository,
    private val settingsRepository: SettingsRepository,
    private val subjectRepository: SubjectRepository,
    private val strokeOrderRepository: StrokeOrderRepository,
    private val pronunciationAudioPlayer: PronunciationAudioPlayer,
    private val appForegroundTracker: AppForegroundTracker,
    private val applicationScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(LessonUiState())
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    private val quizQueue = QuizQueue<LessonItem>()
    private val startedAssignmentIds = mutableSetOf<Long>()
    private var totalQuizCount = 0

    private val gradingGuard = QuizGradingGuard(viewModelScope)

    private val progressByAssignmentId = mutableMapOf<Long, LessonItemProgress>()
    // Individual per-answer records, used for the "slowest answers" summary — persisted and
    // restored across a resume just like progressByAssignmentId (see resumeQuizPhase), so the
    // summary reflects the whole quiz, not just the segment since the most recent resume.
    private val answeredQuestions = mutableListOf<AnsweredQuestionRecord<LessonItem>>()

    // Tracks only the time the quiz was actively being viewed — see QuizSessionTiming. Pause skips
    // re-persisting outside the QUIZ phase — currentPersistSnapshot() always writes phase = QUIZ,
    // and in STUDY phase the correct snapshot is already kept current by persistStudySnapshot on
    // every card change; overwriting it here with an empty-queue QUIZ record would make
    // resumeQuizPhase() misread it as "session complete". The foreground tracker calls resume()
    // unconditionally on app-foreground, so a segment can be running even while in STUDY phase —
    // without this guard, a Home press in STUDY phase would write the corrupt snapshot. Writing the
    // timing fields onto a non-Quiz phase is additionally impossible by construction now —
    // updateQuizTiming() silently no-ops when phase isn't Quiz, since Phase.Study has no such
    // properties to write into. A completed/abandoned/empty-queue session is handled structurally
    // by sessionController.persist() itself (a no-op once the session isn't ACTIVE), not by a flag
    // check here — see QuizSessionController. Runs on applicationScope rather than viewModelScope so
    // this flush actually executes when triggered from onCleared() (viewModelScope is cancelled just
    // before onCleared() runs, so a viewModelScope.launch here would silently never execute).
    private val sessionTiming = QuizSessionTiming(
        onResume = { now -> updateQuizTiming { it.copy(sessionActiveSegmentStartMs = now) } },
        onPause = pause@{ newElapsed ->
            updateQuizTiming { it.copy(sessionActiveElapsedMs = newElapsed, sessionActiveSegmentStartMs = null) }
            if (_uiState.value.phase !is LessonUiState.Phase.Quiz) return@pause
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
    // flags as a plain field instead of calling `settingsRepository.settings.first()` — see
    // ReviewViewModel.latestSettings's doc comment for why a fresh Flow collection here measurably
    // janks the post-submit animation. AppSettings()'s defaults match SettingsRepository's
    // DataStore defaults, so the narrow window before this field's first real emission lands is
    // harmless.
    private var latestSettings = AppSettings()

    init {
        loadOrResume()
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                latestSettings = settings
                _uiState.update {
                    it.copy(
                        settings = it.settings.copy(
                            showPitchAccent = settings.showPitchAccent,
                            showSubjectTypeLabel = settings.showSubjectTypeLabel,
                            showTotalTimer = settings.showTotalTimer,
                            showQuestionTimer = settings.showQuestionTimer,
                            useJapaneseKeyboard = settings.useJapaneseKeyboard
                        )
                    )
                }
            }
        }
        // The initial value is handled by beginQuiz/resumeQuizPhase/sessionTiming.resume() below
        // instead — see QuizSessionTiming.wireForegroundTracking's doc comment.
        sessionTiming.wireForegroundTracking(viewModelScope, appForegroundTracker)
        questionTiming.wireForegroundTracking(viewModelScope, appForegroundTracker)
    }

    /** Guard-clause helper for updates that only apply while in the Quiz phase — a safe cast plus a
     *  no-op fallback, not `!!`/unchecked cast. Also what makes writing session-timing fields onto a
     *  Study value structurally impossible (see [sessionTiming]'s doc comment): there's no such
     *  property on [LessonUiState.Phase.Study] to write into, so this simply no-ops instead. */
    private inline fun updateQuiz(transform: (LessonUiState.Phase.Quiz) -> LessonUiState.Phase.Quiz) {
        _uiState.update { state ->
            val quiz = state.phase as? LessonUiState.Phase.Quiz ?: return@update state
            state.copy(phase = transform(quiz))
        }
    }

    private inline fun updateQuizTiming(transform: (QuizTimingUiState) -> QuizTimingUiState) {
        updateQuiz { it.copy(timing = transform(it.timing)) }
    }

    /** Explicit fresh fetch — bound to the error screen's retry action, so it always discards any
     *  persisted quiz-in-progress rather than resuming a session that may be what's broken. */
    fun load() {
        viewModelScope.launch {
            _uiState.update { LessonUiState() }
            assignmentRepository.warmSrsSystemCache()
            sessionController.complete()
            fetchFreshQueue()
        }
    }

    /** Resumes a persisted in-progress quiz if one exists, otherwise fetches a fresh queue. */
    private fun loadOrResume() {
        viewModelScope.launch {
            _uiState.update { LessonUiState() }
            // Warmed once here, during the loading spinner, so applyOptimisticLessonStart can
            // resolve the SRS system for each item started during this session with zero DB
            // round trips.
            assignmentRepository.warmSrsSystemCache()
            val persisted = sessionController.load()
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
            sessionController.complete()
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
        sessionController.begin()
        _uiState.update {
            it.copy(
                phase = LessonUiState.Phase.Study(
                    studyItems = items,
                    studyIndex = persisted.studyIndex.coerceIn(0, items.lastIndex),
                    pitchAccentsBySubjectId = pitchAccents,
                    relatedSubjectsById = relatedSubjects,
                    strokeOrderBySubjectId = strokeOrders
                )
            )
        }
    }

    private suspend fun resumeQuizPhase(persisted: PersistedLessonSession) {
        // Resolve exactly the assignments this persisted session references, by id — not via
        // observeLessonQueue()'s due filter. An item's "started" transition happens the moment
        // it finishes its quiz questions (applyOptimisticLessonStart), so by the time the user
        // pauses and resumes it may no longer be "due for lesson" even though it's still part of
        // this session's progress tally.
        val neededIds = (persisted.quizQueue.map { it.assignmentId } + persisted.progress.map { it.assignmentId }).toSet()
        val itemsById = assignmentRepository.getLessonItems(neededIds).associateBy { it.assignmentId }

        // The cache backing this persisted session is gone (e.g. app storage was cleared) — fall
        // back to a fresh fetch rather than show a broken quiz.
        if (persisted.quizQueue.any { it.assignmentId !in itemsById }) {
            sessionController.complete()
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
            // itemsById was resolved by id above, so this only misses for the same
            // genuinely-unrecoverable case handled above — not merely "no longer due for lesson".
            val item = itemsById[p.assignmentId] ?: return@forEach
            progressByAssignmentId[p.assignmentId] = LessonItemProgress(item).apply {
                meaningDone = p.meaningDone
                readingDone = p.readingDone
                hadIncorrectMeaning = p.hadIncorrectMeaning
                hadIncorrectReading = p.hadIncorrectReading
            }
        }
        answeredQuestions.clear()
        answeredQuestions.addAll(
            persisted.answeredQuestions.mapNotNull { p ->
                val item = itemsById[p.assignmentId] ?: return@mapNotNull null
                AnsweredQuestionRecord(item, QuestionType.valueOf(p.questionType), p.isCorrect, p.elapsedMs)
            }
        )
        // Restores the quiz's accumulated active time rather than restarting the clock — this is
        // deliberately *not* wall-clock time since the quiz began; time spent away (backgrounded, or
        // navigated off and back) must not count. sessionTiming.resume() then starts a fresh viewing
        // segment on top of that restored base, so the clock resumes right where it left off.
        sessionTiming.elapsedMs = persisted.sessionActiveElapsedMs
        sessionTiming.resume()
        val questionStartedAt = questionTiming.restart()
        totalQuizCount = persisted.totalQuizCount
        // LessonSessionRepository.load() — reached here via sessionController.load() — guarantees
        // a non-empty quizQueue for a QUIZ-phase snapshot (see its resumability check), so
        // quizQueue.current is never null after restore() above.
        val next = requireNotNull(quizQueue.current) { "resumeQuizPhase: persisted QUIZ session had an empty queue despite the repository's resumability check" }
        sessionController.begin()
        _uiState.update {
            it.copy(
                phase = LessonUiState.Phase.Quiz(
                    currentItem = next.item,
                    currentQuestionType = next.type,
                    totalQuizCount = totalQuizCount,
                    remainingQuizCount = quizQueue.size,
                    timing = QuizTimingUiState(
                        sessionActiveElapsedMs = sessionTiming.elapsedMs,
                        sessionActiveSegmentStartMs = sessionTiming.segmentStartMs,
                        questionActiveSegmentStartMs = questionStartedAt
                    )
                )
            )
        }
    }

    private suspend fun fetchFreshQueue() {
        quizQueue.clear()
        startedAssignmentIds.clear()

        when (val result = assignmentRepository.refreshLessonQueue()) {
            is ApiResult.Error -> _uiState.update { it.copy(phase = LessonUiState.Phase.Error(result.message)) }
            is ApiResult.Success -> {
                val currentLevel = statsRepository.observeCurrentLevel().first() ?: 0
                val levelUpProgress = assignmentRepository.observeLevelUpProgress(currentLevel).first()
                val lessonsToday = assignmentRepository.observeLessonsCompletedToday().first()
                val dailyGoal = settingsRepository.settings.first().dailyLessonGoal
                val items = LessonPrioritizer.prioritize(
                    items = assignmentRepository.observeLessonQueue().first(),
                    levelUpProgress = levelUpProgress,
                    isStrained = lessonsToday >= dailyGoal
                )
                if (items.isEmpty()) {
                    _uiState.update { it.copy(phase = LessonUiState.Phase.NoLessonsAvailable) }
                } else {
                    val defaultSelection = items.take(DEFAULT_LESSON_SELECTION_SIZE)
                        .map { it.assignmentId }
                        .toSet()
                    _uiState.update {
                        it.copy(phase = LessonUiState.Phase.Select(availableLessons = items, selectedAssignmentIds = defaultSelection))
                    }
                }
            }
        }
    }

    private inline fun updateSelect(transform: (LessonUiState.Phase.Select) -> LessonUiState.Phase.Select) {
        _uiState.update { state ->
            val select = state.phase as? LessonUiState.Phase.Select ?: return@update state
            state.copy(phase = transform(select))
        }
    }

    fun toggleLessonSelection(assignmentId: Long) {
        updateSelect { select ->
            val selected = select.selectedAssignmentIds.toMutableSet()
            if (!selected.add(assignmentId)) selected.remove(assignmentId)
            select.copy(selectedAssignmentIds = selected)
        }
    }

    fun selectFirst(n: Int) {
        updateSelect { select -> select.copy(selectedAssignmentIds = select.availableLessons.take(n).map { it.assignmentId }.toSet()) }
    }

    fun selectAll() {
        updateSelect { select -> select.copy(selectedAssignmentIds = select.availableLessons.map { it.assignmentId }.toSet()) }
    }

    fun selectNone() {
        updateSelect { select -> select.copy(selectedAssignmentIds = emptySet()) }
    }

    fun startSelectedLessons() {
        val select = _uiState.value.phase as? LessonUiState.Phase.Select ?: return
        val selected = select.availableLessons.filter { it.assignmentId in select.selectedAssignmentIds }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val (pitchAccents, relatedSubjects, strokeOrders) = coroutineScope {
                val pitchAccentsDeferred = async { fetchPitchAccents(selected) }
                val relatedSubjectsDeferred = async { fetchRelatedSubjects(selected) }
                val strokeOrdersDeferred = async { fetchStrokeOrders(selected) }
                Triple(pitchAccentsDeferred.await(), relatedSubjectsDeferred.await(), strokeOrdersDeferred.await())
            }
            sessionController.begin()
            _uiState.update {
                it.copy(
                    phase = LessonUiState.Phase.Study(
                        studyItems = selected,
                        studyIndex = 0,
                        pitchAccentsBySubjectId = pitchAccents,
                        relatedSubjectsById = relatedSubjects,
                        strokeOrderBySubjectId = strokeOrders
                    )
                )
            }
            persistStudySnapshot(selected, 0)
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

    private inline fun updateStudy(transform: (LessonUiState.Phase.Study) -> LessonUiState.Phase.Study) {
        _uiState.update { state ->
            val study = state.phase as? LessonUiState.Phase.Study ?: return@update state
            state.copy(phase = transform(study))
        }
    }

    fun onStudyCardSwiped(index: Int) {
        val study = _uiState.value.phase as? LessonUiState.Phase.Study ?: return
        if (index !in study.studyItems.indices) return
        updateStudy { it.copy(studyIndex = index) }
        viewModelScope.launch { persistStudySnapshot(study.studyItems, index) }
    }

    fun nextStudyCard() {
        val study = _uiState.value.phase as? LessonUiState.Phase.Study ?: return
        val nextIndex = study.studyIndex + 1
        if (nextIndex >= study.studyItems.size) {
            viewModelScope.launch { beginQuiz(study.studyItems) }
        } else {
            updateStudy { it.copy(studyIndex = nextIndex) }
            viewModelScope.launch { persistStudySnapshot(study.studyItems, nextIndex) }
        }
    }

    fun previousStudyCard() {
        val study = _uiState.value.phase as? LessonUiState.Phase.Study ?: return
        if (study.studyIndex == 0) return
        val previousIndex = study.studyIndex - 1
        updateStudy { it.copy(studyIndex = previousIndex) }
        viewModelScope.launch { persistStudySnapshot(study.studyItems, previousIndex) }
    }

    /** Persists just enough to resume mid-flashcard-study: which items are in the batch (in order)
     *  and which card the user is on — see [resumeStudyPhase]. Called on every card change rather
     *  than only at study's start, so a resume lands on the exact card left off on, not card one. */
    private suspend fun persistStudySnapshot(items: List<LessonItem>, index: Int) {
        sessionController.persist(
            PersistedLessonSession(
                phase = PersistedLessonPhase.STUDY,
                studyAssignmentIds = items.map { it.assignmentId },
                studyIndex = index
            )
        )
    }

    private suspend fun beginQuiz(items: List<LessonItem>) {
        sessionController.begin()
        quizQueue.build(items, typesFor = { item -> questionTypesFor(item.subjectType) })
        totalQuizCount = quizQueue.size

        progressByAssignmentId.clear()
        items.forEach { item -> progressByAssignmentId[item.assignmentId] = LessonItemProgress(item) }
        answeredQuestions.clear()
        sessionTiming.elapsedMs = 0L
        sessionTiming.resume()
        val questionStartedAt = questionTiming.restart()

        val next = quizQueue.current
        if (next == null) {
            // Every item in this batch was already fully learned before quizzing began — go
            // straight to Complete instead of ever constructing a Quiz phase with no question.
            sessionController.complete()
            val summary = sessionSummary()
            persistLastSessionSummary(summary)
            _uiState.update { it.copy(phase = summary.toCompletePhase()) }
            return
        }
        _uiState.update {
            it.copy(
                phase = LessonUiState.Phase.Quiz(
                    currentItem = next.item,
                    currentQuestionType = next.type,
                    totalQuizCount = totalQuizCount,
                    remainingQuizCount = totalQuizCount,
                    timing = QuizTimingUiState(
                        sessionActiveElapsedMs = sessionTiming.elapsedMs,
                        sessionActiveSegmentStartMs = sessionTiming.segmentStartMs,
                        questionActiveSegmentStartMs = questionStartedAt
                    )
                )
            )
        }
        persistCurrentState()
    }

    fun onAnswerInputChange(value: String) {
        updateQuiz { it.copy(answerInput = value) }
    }

    fun submitAnswer() {
        val quiz = _uiState.value.phase as? LessonUiState.Phase.Quiz ?: return
        if (quiz.feedback != null) return
        val item = quiz.currentItem
        val type = quiz.currentQuestionType
        if (quiz.answerInput.isBlank()) return

        gradingGuard.launchIfIdle {
            val candidates = candidatesFor(item.meanings, item.auxiliaryMeanings, item.readings, type)
            val outcome = evaluateAnswer(
                quiz.answerInput, type, item.meanings, item.auxiliaryMeanings, item.readings,
                closeEnoughEnabled = latestSettings.closeEnoughAnswersEnabled
            )
            when (outcome) {
                AnswerOutcome.TypeMismatch ->
                    updateQuiz { it.copy(answerTypeMismatchCount = it.answerTypeMismatchCount + 1) }
                is AnswerOutcome.Graded ->
                    gradeAnswer(item, type, outcome.isCorrect, candidates, wasCloseMatch = outcome.wasCloseMatch)
            }
        }
    }

    /** Gives up on the current question — treated the same as a wrong answer, requeued for another pass. */
    fun dontKnowAnswer() {
        val quiz = _uiState.value.phase as? LessonUiState.Phase.Quiz ?: return
        if (quiz.feedback != null) return
        val item = quiz.currentItem
        val type = quiz.currentQuestionType
        val candidates = candidatesFor(item.meanings, item.auxiliaryMeanings, item.readings, type)
        gradingGuard.launchIfIdle {
            gradeAnswer(item, type, isCorrect = false, candidates)
        }
    }

    /** Reverts the most recent incorrect answer — for a typo, not a genuine miss. Unlike
     *  ReviewViewModel.undoLastAnswer(), a correct answer here can't be undone — lesson-start
     *  submission isn't deferred to Continue the way a review grade is, so by the time feedback is
     *  showing it's already committed. The queue/progress mutation for the incorrect-answer case
     *  is shared via [undoLastIncorrectAnswer]. */
    fun undoLastAnswer() {
        val quiz = _uiState.value.phase as? LessonUiState.Phase.Quiz ?: return
        val item = quiz.currentItem
        val type = quiz.currentQuestionType
        val feedback = quiz.feedback ?: return
        if (feedback.isCorrect) return

        viewModelScope.launch {
            val didUndo = undoLastIncorrectAnswer(
                queue = quizQueue,
                progressByAssignmentId = progressByAssignmentId,
                answeredQuestions = answeredQuestions,
                item = item,
                questionType = type,
                persist = { persistCurrentState() }
            )
            if (!didUndo) return@launch
            // Restarts this question's clock so the retry's timing doesn't inherit time spent
            // before the undo.
            val questionStartedAt = questionTiming.restart()

            // undoCounter changes even though currentItem/currentQuestionType don't — this is
            // what the answer field's focus-restoring LaunchedEffect keys on, since undo doesn't
            // change either of those but still needs to refocus the field the user just tapped away
            // from.
            updateQuiz {
                it.copy(
                    feedback = null,
                    answerInput = "",
                    remainingQuizCount = quizQueue.size,
                    undoCounter = it.undoCounter + 1,
                    timing = it.timing.copy(
                        questionActiveElapsedMs = 0L,
                        questionActiveSegmentStartMs = questionStartedAt,
                        questionElapsedMs = null
                    )
                )
            }
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
        val questionElapsedMs = questionTiming.freeze()
        answeredQuestions.add(AnsweredQuestionRecord(item, type, isCorrect, questionElapsedMs))

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

        // Whether this answer was the very last one due — if so, commitGradeDurably completes the
        // session outright instead of saving a snapshot of the now-empty queue. That snapshot would
        // only ever get overwritten by advanceQuiz's own completion once the user taps Continue
        // anyway; not writing it in the first place, right when the queue empties, is simpler and
        // safer than writing it and relying on a later completion to overwrite it.
        val queueIsEmpty = quizQueue.current == null

        // Snapshotted synchronously, right after mutating quizQueue above, so the detached
        // durability write below can safely run concurrently with the next question's own
        // grading/advance — quizQueue is a plain, non-thread-safe collection, and once feedback
        // is visible the user is free to act immediately.
        val snapshot = currentPersistSnapshot()

        // startedAssignmentIds.add(...) is the idempotency guard (an item should only ever be
        // marked started once) — computed once so both the optimistic patch below and the outbox
        // enqueue afterward agree on whether this is really a first-time completion.
        val isNewlyStarted = justCompletedItem && startedAssignmentIds.add(item.assignmentId)

        // Computed synchronously against AssignmentRepository's in-memory SRS-system cache (warmed
        // once when the queue loaded) — zero DB access on this critical path, same as Review's
        // rank-change chip. Every lesson item starts the same way (locked straight to the SRS
        // system's starting stage), so unlike Review this doesn't depend on whether the answer was
        // actually correct — it only fires once, the first time the item's lesson is fully done.
        val newRankChange = if (isNewlyStarted) assignmentRepository.computeLessonStartRankChange(item.srsSystemId) else null

        updateQuiz {
            it.copy(
                feedback = AnswerFeedback(isCorrect, candidates.joinToString(", "), wasCloseMatch, candidates.size),
                remainingQuizCount = quizQueue.size,
                rankChange = newRankChange ?: it.rankChange,
                // Freezes the "time on this question" display the instant feedback appears, rather
                // than letting it keep ticking while the feedback/Continue screen is up — matches
                // the elapsedMs recorded for the slowest-answers summary above, stamped at this
                // same moment.
                timing = it.timing.copy(questionElapsedMs = questionElapsedMs, questionActiveSegmentStartMs = null)
            )
        }

        // Reads the field kept warm by the settings collector in init{} instead of
        // `settingsRepository.settings.first()` — see `latestSettings`'s doc comment.
        val settings = latestSettings
        if (type == QuestionType.READING && settings.autoplayPronunciationAudio) {
            candidates.firstOrNull()?.let { reading ->
                selectAudioFor(item.pronunciationAudios, reading, mp3Only = settings.restrictAudioToMp3)
                    ?.let(pronunciationAudioPlayer::play)
            }
        }

        commitGradeDurably(isNewlyStarted, item, snapshot, queueIsEmpty)
    }

    /** Manual play from the study card's reading row — mirrors SubjectDetailViewModel.playReading. */
    fun playReading(item: LessonItem, reading: String) {
        viewModelScope.launch {
            selectAudioFor(item.pronunciationAudios, reading, mp3Only = latestSettings.restrictAudioToMp3)
                ?.let(pronunciationAudioPlayer::play)
        }
    }

    /** Captures the current quiz queue as an immutable, ready-to-persist value — safe to hold
     *  across a suspension point even if the live quizQueue is mutated by something else
     *  afterward (see [gradeAnswer]'s deferred [commitGradeDurably] call). */
    private fun currentPersistSnapshot(): PersistedLessonSession = PersistedLessonSession(
        phase = PersistedLessonPhase.QUIZ,
        quizQueue = quizQueue.toList().map { PersistedQuestion(it.item.assignmentId, it.type.name) },
        progress = progressByAssignmentId.map { (id, p) ->
            PersistedItemProgress(id, p.meaningDone, p.readingDone, p.hadIncorrectMeaning, p.hadIncorrectReading)
        },
        totalQuizCount = totalQuizCount,
        sessionActiveElapsedMs = sessionTiming.currentElapsedMs(),
        answeredQuestions = answeredQuestions.map {
            PersistedAnsweredQuestion(it.item.assignmentId, it.type.name, it.isCorrect, it.elapsedMs)
        }
    )

    private suspend fun persistCurrentState() {
        sessionController.persist(currentPersistSnapshot())
    }

    /** Runs the post-grading durability writes (outbox enqueue, session persistence), as one queued
     *  unit via [sessionController]'s `alongside` parameter — not as a separately-awaited suspension
     *  before it, which would let a concurrent reader (e.g. a test asserting against
     *  [sessionController] right after the next emitted uiState) observe the outbox enqueue as done
     *  but the session write as not yet applied. Completes the session instead of saving [snapshot]
     *  when [queueIsEmpty] — this was the last due question, so [snapshot] is already an empty-queue
     *  shell that advanceQuiz's own completion would just overwrite once the user taps Continue; not
     *  saving it in the first place is simpler than saving it and relying on a later completion to
     *  overwrite it. */
    private suspend fun commitGradeDurably(isNewlyStarted: Boolean, item: LessonItem, snapshot: PersistedLessonSession, queueIsEmpty: Boolean) {
        val outboxWork: suspend () -> Unit = {
            if (isNewlyStarted) {
                assignmentRepository.applyOptimisticLessonStart(item.assignmentId, item.srsSystemId)
                outboxRepository.enqueueLessonStart(item.assignmentId, item.subjectId)
            }
        }
        if (queueIsEmpty) {
            sessionController.complete(alongside = outboxWork)
        } else {
            sessionController.persist(snapshot, alongside = outboxWork)
        }
    }

    fun onContinue() {
        viewModelScope.launch { advanceQuiz() }
    }

    fun toggleDetails() {
        updateQuiz { it.copy(isDetailsExpanded = !it.isDetailsExpanded) }
    }

    fun closeDetails() {
        updateQuiz { it.copy(isDetailsExpanded = false) }
    }

    /** Discards a persisted in-progress lesson session (study or quiz) and exits — a clean slate
     *  next time. Mirrors ReviewViewModel.abandonSession. */
    fun abandonSession() {
        viewModelScope.launch {
            sessionController.abandon()
            _uiState.update { it.copy(isAbandoned = true) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sessionTiming.pause()
        pronunciationAudioPlayer.stop()
    }

    /** Items learned, how many were correct without ever missing, which were missed at least once,
     *  and timing — mirrors ReviewViewModel.sessionSummary(). "Missed" here means at least one wrong
     *  attempt during the quiz, not a real SRS miss — every lesson item is requeued until correct. */
    private fun sessionSummary(): QuizSessionSummary<LessonItem> =
        summarizeQuizSession(progressByAssignmentId.values, answeredQuestions, sessionTiming.currentElapsedMs())

    /** Snapshots a just-completed session's summary so it can be revisited later from the
     *  dashboard, after this ViewModel (and its otherwise-ephemeral session-complete state) is
     *  gone. Mirrors ReviewViewModel.persistLastSessionSummary(). */
    private fun persistLastSessionSummary(summary: QuizSessionSummary<LessonItem>) {
        applicationScope.launch {
            lastSessionSummaryRepository.save(
                LastSessionSummary(
                    kind = LastSessionKind.LESSON,
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

    private suspend fun advanceQuiz() {
        val next = quizQueue.current
        if (next == null) {
            sessionController.complete()
            outboxRepository.requestSyncNow()
            val summary = sessionSummary()
            persistLastSessionSummary(summary)
            _uiState.update { it.copy(phase = summary.toCompletePhase()) }
            return
        }
        val questionStartedAt = questionTiming.restart()
        updateQuiz {
            it.copy(
                currentItem = next.item,
                currentQuestionType = next.type,
                answerInput = "",
                feedback = null,
                rankChange = null,
                isDetailsExpanded = false,
                remainingQuizCount = quizQueue.size,
                timing = it.timing.copy(
                    questionActiveElapsedMs = 0L,
                    questionActiveSegmentStartMs = questionStartedAt,
                    questionElapsedMs = null
                )
            )
        }
    }
}
