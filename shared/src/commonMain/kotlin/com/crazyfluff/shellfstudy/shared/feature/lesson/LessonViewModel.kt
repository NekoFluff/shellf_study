package com.crazyfluff.shellfstudy.shared.feature.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.audio.playMatchingReading
import com.crazyfluff.shellfstudy.shared.audio.selectAudioFor
import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.isAuthError
import com.crazyfluff.shellfstudy.shared.data.AppSettings
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.AssignmentStatsRepository
import com.crazyfluff.shellfstudy.shared.data.DEFAULT_LESSON_BATCH_SIZE
import com.crazyfluff.shellfstudy.shared.data.LastSessionKind
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.PersistedLessonPhase
import com.crazyfluff.shellfstudy.shared.data.PersistedLessonSession
import com.crazyfluff.shellfstudy.shared.data.PitchAccentRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.SubjectRepository
import com.crazyfluff.shellfstudy.shared.data.model.LessonItem
import com.crazyfluff.shellfstudy.shared.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.shared.data.StrokeOrderRepository
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.AnswerReadingHint
import com.crazyfluff.shellfstudy.shared.designsystem.strokeorder.StrokeOrderUiState
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentUiState
import com.crazyfluff.shellfstudy.shared.lifecycle.AppForegroundTracker
import com.crazyfluff.shellfstudy.shared.network.SubjectType
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
import com.crazyfluff.shellfstudy.shared.quiz.QuizGrade
import com.crazyfluff.shellfstudy.shared.quiz.QuizQuestionState
import com.crazyfluff.shellfstudy.shared.quiz.QuizSessionState
import com.crazyfluff.shellfstudy.shared.quiz.QuizSessionViewModel
import com.crazyfluff.shellfstudy.shared.session.LessonSessionController
import kotlin.time.Clock
import com.crazyfluff.shellfstudy.shared.sync.SyncOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LessonUiState(
    val phase: Phase = Phase.Loading,
    // Deliberately not folded into Phase — leaving the screen is a one-shot navigation signal, not a
    // rendering mode. The screen keeps rendering whatever phase was showing for one more frame while
    // a LaunchedEffect(exit) fires the actual back-navigation. One sealed value rather than two
    // booleans because the two ways out are mutually exclusive and mean opposite things to the
    // dashboard: abandoning drops the session, parking keeps it resumable.
    val exit: ExitRequest = ExitRequest.None,
    /** The pitch-accent knowledge for every item currently in play (this batch's study cards and its
     *  quiz), keyed by subject id. Hoisted to the top level rather than carried per-phase because the
     *  study card and the quiz hint are two views of the *same* live observation off the bundled
     *  dictionary's Room flow, the single source of truth. A subject absent from the map has not been
     *  looked up yet this session; [PitchAccentUiState.Unavailable] means it was looked up and has no
     *  documented pitch accent. */
    val pitchAccentsBySubjectId: Map<Long, PitchAccentUiState> = emptyMap(),
    /** Related-subject summaries for every item currently in play, keyed by subject id — same shape
     *  and same reason as [pitchAccentsBySubjectId]: one live observation off [SubjectRepository]'s
     *  Room flow, rather than a per-batch snapshot fetched once at study time. A subject absent from
     *  the map has no cached summary (yet); [RelatedSubjectsSection]/[toRelatedSubjectsUiState] is
     *  what turns "absent" into a "not cached yet" caption rather than silence. */
    val relatedSubjectsById: Map<Long, SubjectSummary> = emptyMap()
) : QuizSessionState<LessonUiState, LessonItem> {

    override val question: QuizQuestionState<LessonItem>? get() = (phase as? Phase.Quiz)?.question

    override fun withQuestion(question: QuizQuestionState<LessonItem>): LessonUiState =
        (phase as? Phase.Quiz)?.let { copy(phase = it.copy(question = question)) } ?: this

    /** Why the learner is leaving the lesson screen, if they are. */
    sealed interface ExitRequest {
        data object None : ExitRequest

        /** The session was discarded — there is nothing left for the dashboard to resume. */
        data object Abandoned : ExitRequest

        /** "Finish for now": the session is kept exactly where it was left, so the dashboard offers to
         *  resume it at the next batch. */
        data object Parked : ExitRequest
    }

    sealed interface Phase {
        data object Loading : Phase
        data class Error(val message: String) : Phase
        data object NoLessonsAvailable : Phase

        data class Select(
            val availableLessons: List<LessonItem> = emptyList(),
            val selectedAssignmentIds: Set<Long> = emptySet(),
            /** The batch size a session started from here gets sliced into. */
            val batchSize: Int = DEFAULT_LESSON_BATCH_SIZE,
            /** How [availableLessons] is ordered. Reset to [LessonSort.DEFAULT] on every fresh fetch —
             *  it's a way of browsing the queue, not a stored preference. */
            val sort: LessonSort = LessonSort.DEFAULT
        ) : Phase {
            /** The types on offer, in [SubjectType]'s own order (radicals, kanji, vocabulary) so the
             *  picker's chips never shuffle around when the sort changes. */
            val availableTypes: List<SubjectType>
                get() = SubjectType.entries.filter { type -> availableLessons.any { it.subjectType == type } }

            fun countOfType(type: SubjectType): Int = availableLessons.count { it.subjectType == type }

            fun selectedCountOfType(type: SubjectType): Int =
                availableLessons.count { it.subjectType == type && it.assignmentId in selectedAssignmentIds }

            /** Whether every lesson of [type] is selected — what fills in that type's chip. A type the
             *  queue has none of is never "fully selected"; partial selections read as unselected. */
            fun isTypeFullySelected(type: SubjectType): Boolean =
                countOfType(type) > 0 && selectedCountOfType(type) == countOfType(type)
        }

        data class Study(
            /** Just the current batch's cards, not every item the session committed to — [batchIndex]
             *  and [batchCount] are what say where that batch sits in the plan. */
            val studyItems: List<LessonItem> = emptyList(),
            val studyIndex: Int = 0,
            val batchIndex: Int = 0,
            val batchCount: Int = 1,
            val strokeOrderBySubjectId: Map<Long, StrokeOrderUiState> = emptyMap()
        ) : Phase

        data class Quiz(
            val question: QuizQuestionState<LessonItem>,
            val batchIndex: Int = 0,
            val batchCount: Int = 1,
            val totalQuizCount: Int = 0,
            val remainingQuizCount: Int = 0
        ) : Phase

        /** The end of a batch — where a session pauses instead of running every selected item's
         *  flashcards before anything is quizzed. [next] is modelled as one sealed value rather than
         *  two mutually-exclusive nullable fields, since exactly one of them ever applies. */
        data class BatchComplete(
            /** 0-based index of the batch that just finished. */
            val batchIndex: Int,
            val batchCount: Int,
            val itemsLearned: Int,
            val itemsCorrectFirstTry: Int,
            /** Only this batch's misses — the per-batch feedback that makes a checkpoint worth
             *  showing at all. */
            val missedItems: List<LessonItem>,
            /** Items left in the whole session, the next batch included. A checkpoint only exists
             *  while there is one, so this is never zero — the final batch goes straight to the
             *  summary instead of stopping here. */
            val remainingSessionItems: Int
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


@OptIn(ExperimentalCoroutinesApi::class)
class LessonViewModel(
    private val assignmentRepository: AssignmentRepository,
    private val assignmentStatsRepository: AssignmentStatsRepository,
    private val statsRepository: StatsRepository,
    private val outboxRepository: OutboxRepository,
    private val sessionController: LessonSessionController,
    private val lastSessionSummaryRepository: LastSessionSummaryRepository,
    private val pitchAccentRepository: PitchAccentRepository,
    private val settingsRepository: SettingsRepository,
    private val subjectRepository: SubjectRepository,
    private val strokeOrderRepository: StrokeOrderRepository,
    override val pronunciationAudioPlayer: PronunciationAudioPlayer,
    private val appForegroundTracker: AppForegroundTracker,
    private val applicationScope: CoroutineScope,
    private val syncOrchestrator: SyncOrchestrator
) : QuizSessionViewModel<LessonItem, LessonUiState>(), LessonActions {

    override val _uiState = MutableStateFlow(LessonUiState())
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    /** The session committed to at "Start session" — empty until then. Every change replaces it
     *  whole, so a snapshot taken for persisting can never be changed underneath the write. */
    private var session = LessonSession()

    /** The picker's inputs, kept so a sort change can re-order the queue without another fetch. */
    private var picker: LessonPicker? = null

    /** The items whose pitch accents the screen can currently show — either this batch's study cards
     *  or its quiz's items. Held as a separate
     *  flow so the observation follows the session's phases: flatMapLatest swaps the whole set of
     *  per-item Room flows when the learner moves from one batch to the next, instead of holding one
     *  flow per item of the entire session open for its duration. What it produces lands in
     *  [LessonUiState.pitchAccentsBySubjectId] — see the collector in init. */
    private val pitchAccentItems = MutableStateFlow<List<LessonItem>>(emptyList())

    // Tracks only the time the session was actively being worked through — see QuizSessionTiming.
    // Pause skips re-persisting outside the QUIZ phase — currentPersistSnapshot() always writes
    // phase = QUIZ, and in STUDY phase the correct snapshot is already kept current by
    // persistStudySnapshot on every card change; overwriting it here with a queue-less QUIZ record
    // would make resumeQuizPhase() misread it as "session complete". The foreground tracker calls
    // resume() unconditionally on app-foreground, so a segment can be running even while in STUDY
    // phase — without this guard, a Home press in STUDY phase would write the corrupt snapshot.
    // Writing the timing fields onto a non-Quiz phase is additionally impossible by construction now —
    // updateQuizTiming() silently no-ops when phase isn't Quiz, since Phase.Study has no such
    // properties to write into. A completed/abandoned/empty-queue session is handled structurally
    // by sessionController.persist() itself (a no-op once the session isn't ACTIVE), not by a flag
    // check here — see QuizSessionController. Runs on applicationScope rather than viewModelScope so
    // this flush actually executes when triggered from onCleared() (viewModelScope is cancelled just
    // before onCleared() runs, so a viewModelScope.launch here would silently never execute).
    override val sessionTiming = QuizSessionTiming(
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
    override var latestSettings = AppSettings()

    init {
        loadOrResume()
        viewModelScope.launch {
            // Only kept warm for the ViewModel's own use (see latestSettings): the display flags it
            // used to mirror into the UI state are provided app-wide by LocalDisplaySettings instead,
            // so a settings change no longer re-emits a whole LessonUiState.
            settingsRepository.settings.collect { latestSettings = it }
        }
        // The study card and the quiz hint both read pitch accents from
        // LessonUiState.pitchAccentsBySubjectId, which this collector keeps live off the repository's
        // own Room flows — the single source of truth. A scrape from anywhere (the detail sheet's
        // check, the background worker, this screen's own check) therefore reaches both surfaces with
        // no refetch and no propagation code. Follows the items in play rather than the whole session
        // plan: flatMapLatest cancels the previous batch's queries when the learner moves on.
        viewModelScope.launch {
            pitchAccentItems
                .flatMapLatest { items ->
                    val words = items
                        .filter { isPitchAccentEligible(it.subjectType) }
                        .mapNotNull { item -> item.characters?.let { characters -> item.subjectId to characters } }
                    if (words.isEmpty()) {
                        flowOf(emptyMap())
                    } else {
                        // One flow per word in play; combine re-emits whenever any of them is
                        // invalidated by a cache write.
                        combine(words.map { (subjectId, characters) ->
                            pitchAccentRepository.observePitchAccents(characters).map { subjectId to it }
                        }) { accents -> accents.toMap() }
                    }
                }
                .collect { accents -> _uiState.update { it.copy(pitchAccentsBySubjectId = accents) } }
        }
        // Same shape as the pitch-accent collector above, off the same items-in-play stream: the
        // study card and the quiz hint read one live observation of SubjectRepository's Room flow
        // instead of a per-batch snapshot fetched once when the batch was entered, so a write from
        // any source (this session's own lookups, another screen's) reaches both in place.
        viewModelScope.launch {
            pitchAccentItems
                .flatMapLatest { items ->
                    val relatedIds = items
                        .flatMap { it.componentSubjectIds + it.amalgamationSubjectIds + it.visuallySimilarSubjectIds }
                        .distinct()
                    if (relatedIds.isEmpty()) {
                        flowOf(emptyMap())
                    } else {
                        subjectRepository.observeSubjectSummaries(relatedIds).map { it.associateBy { s -> s.subjectId } }
                    }
                }
                .collect { related -> _uiState.update { it.copy(relatedSubjectsById = related) } }
        }
        // The initial value is handled by beginBatchQuiz/resumeQuizPhase/sessionTiming.resume() below
        // instead — see QuizSessionTiming.wireForegroundTracking's doc comment. The gate keeps a batch
        // checkpoint (or the summary) from restarting the session clock just because the learner came
        // back to the app while reading it.
        sessionTiming.wireForegroundTracking(viewModelScope, appForegroundTracker) { isSessionBeingWorked() }
        questionTiming.wireForegroundTracking(viewModelScope, appForegroundTracker)
    }

    /** Whether the learner is actually working through material right now — Study flashcards or a quiz
     *  question. False at a batch checkpoint, on the summary, and everywhere before a session is
     *  committed to, so none of that time lands in the session total. */
    private fun isSessionBeingWorked(): Boolean = when (_uiState.value.phase) {
        is LessonUiState.Phase.Study, is LessonUiState.Phase.Quiz -> true
        else -> false
    }

    /** Explicit fresh fetch — bound to the error screen's retry action, so it always discards any
     *  persisted quiz-in-progress rather than resuming a session that may be what's broken. */
    override fun load() {
        viewModelScope.launch {
            _uiState.update { LessonUiState() }
            assignmentRepository.warmSrsSystemCache()
            sessionController.complete()
            clearSessionState()
            fetchFreshQueue()
        }
    }

    /** Resumes a persisted in-progress session if one exists, otherwise fetches a fresh queue. */
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

    /** Drops every in-memory trace of a session — the plan included, so a fresh fetch can't resume or
     *  re-persist the session it replaced. */
    private fun clearSessionState() {
        session = LessonSession()
        pitchAccentItems.value = emptyList()
    }

    private suspend fun resumeFromPersisted(persisted: PersistedLessonSession) {
        val plan = persisted.migratedFromLegacyShape()
        // Resolve exactly the assignments this persisted session references, by id — not via
        // observeLessonQueue()'s due filter. An item's "started" transition happens the moment it
        // finishes its quiz questions (applyOptimisticLessonStart), so by the time the user pauses and
        // resumes it may no longer be "due for lesson" even though it's still part of this session's
        // progress tally.
        val resolved = assignmentRepository.getLessonItems(plan.sessionAssignmentIds).associateBy { it.assignmentId }

        // The cache backing this persisted session is gone (e.g. app storage was cleared), or some of
        // the plan's items no longer resolve — fall back to a fresh fetch rather than resume a session
        // with holes in it.
        if (plan.sessionAssignmentIds.any { it !in resolved }) {
            sessionController.complete()
            clearSessionState()
            fetchFreshQueue()
            return
        }

        session = LessonSession(
            plan = plan.sessionAssignmentIds,
            batchSize = LessonSessionPlanner.normalizeBatchSize(plan.batchSize),
            itemsById = resolved,
            batchIndex = plan.batchIndex
        )
        when (plan.phase) {
            PersistedLessonPhase.STUDY -> resumeStudyPhase(plan)
            PersistedLessonPhase.QUIZ -> resumeQuizPhase(plan)
            PersistedLessonPhase.CHECKPOINT -> resumeCheckpoint(plan)
        }
    }

    /** Resumes a session left mid-flashcard-study — after "Start session" but before that batch's last
     *  card hands off to its quiz. Reconstructs the same batch, in the same order, and jumps back to
     *  the card the user was on, rather than forcing lesson re-selection and restudying from the first
     *  card, the same way [resumeQuizPhase] avoids re-fetching a fresh quiz queue. */
    private suspend fun resumeStudyPhase(persisted: PersistedLessonSession) {
        val items = session.batchItems(persisted.batchIndex)
        if (items.isEmpty()) {
            sessionController.complete()
            clearSessionState()
            fetchFreshQueue()
            return
        }

        val strokeOrders = fetchStrokeOrders(items)
        pitchAccentItems.value = items
        sessionTiming.elapsedMs = persisted.sessionActiveElapsedMs
        sessionController.begin()
        _uiState.update {
            it.copy(
                phase = LessonUiState.Phase.Study(
                    studyItems = items,
                    studyIndex = persisted.studyIndex.coerceIn(0, items.lastIndex),
                    batchIndex = persisted.batchIndex,
                    batchCount = session.batchCount,
                    strokeOrderBySubjectId = strokeOrders
                )
            )
        }
        sessionTiming.resume()
    }

    private suspend fun resumeQuizPhase(persisted: PersistedLessonSession) {
        // itemsById was resolved from the whole plan in resumeFromPersisted, so an entry can only fail
        // to rebuild if the snapshot references an assignment outside its own plan, or carries a
        // question type this build no longer knows — genuinely corrupt in both cases, rather than
        // merely "no longer due for lesson".
        val quiz = restoredQuiz(persisted, withQueue = true)
        if (quiz == null) {
            sessionController.complete()
            clearSessionState()
            fetchFreshQueue()
            return
        }
        session = session.copy(quiz = quiz)
        sessionTiming.elapsedMs = persisted.sessionActiveElapsedMs

        // resumeQuizPhase skips Phase.Study entirely (a mid-quiz resume), so nothing has told the
        // live observation which items are in play — hand it the queue's own items, which is also
        // what makes a resumed session's hint pick up cache writes exactly like a normal one.
        pitchAccentItems.value = (persisted.quizQueue.map { it.assignmentId } + persisted.progress.map { it.assignmentId })
            .distinct()
            .mapNotNull { session.itemsById[it] }

        // Restores the session's accumulated active time rather than restarting the clock — this is
        // deliberately *not* wall-clock time since the session began; time spent away (backgrounded, at
        // a checkpoint, or navigated off and back) must not count. sessionTiming.resume() below then
        // starts a fresh viewing segment on top of that restored base, so the clock resumes right where
        // it left off.
        val questionStartedAt = questionTiming.restart()
        // LessonSessionRepository.load() — reached here via sessionController.load() — guarantees a
        // non-empty quiz queue for a QUIZ-phase snapshot (see its resumability check), but this is
        // reached from init/loadOrResume(), so a violated invariant here (a future repository bug, a
        // manual DB edit, a partial migration) must degrade the same way every other corrupt-snapshot
        // case in this function does, rather than crash the ViewModel on app launch.
        val next = quiz.current
        if (next == null) {
            sessionController.complete()
            clearSessionState()
            fetchFreshQueue()
            return
        }
        sessionController.begin()
        _uiState.update {
            it.copy(
                phase = LessonUiState.Phase.Quiz(
                    question = QuizQuestionState(
                        item = next.item,
                        type = next.type,
                        timing = QuizTimingUiState(
                            sessionActiveElapsedMs = sessionTiming.elapsedMs,
                            sessionActiveSegmentStartMs = sessionTiming.segmentStartMs,
                            questionActiveSegmentStartMs = questionStartedAt
                        )
                    ),
                    batchIndex = session.batchIndex,
                    batchCount = session.batchCount,
                    totalQuizCount = quiz.totalQuestions,
                    remainingQuizCount = quiz.remainingQuestions
                )
            )
        }
        sessionTiming.resume()
    }

    /** Resumes a session parked at a batch checkpoint. An index at or past the end of the plan means
     *  every batch is done — reachable from a snapshot written before checkpoints stopped being saved
     *  after the final batch — so it resumes into the summary instead. */
    private suspend fun resumeCheckpoint(persisted: PersistedLessonSession) {
        sessionController.begin()
        // A checkpoint's progress and answers are what the next batch's quiz, and in the end the
        // summary, add to. Its queue is empty by definition, so nothing there can fail to restore.
        session = session.copy(quiz = restoredQuiz(persisted, withQueue = false) ?: QuizSession())
        sessionTiming.elapsedMs = persisted.sessionActiveElapsedMs
        if (persisted.batchIndex < session.batchCount) {
            enterStudyPhase(persisted.batchIndex)
            return
        }
        finishSession()
    }

    /** The session-wide quiz state a snapshot recorded, against the plan's items — null when a queued
     *  question can't be rebuilt. */
    private fun restoredQuiz(persisted: PersistedLessonSession, withQueue: Boolean): QuizSession<LessonItem>? =
        QuizSession.restore(
            itemsById = session.itemsById,
            inFlight = if (withQueue) persisted.quizQueue else emptyList(),
            progress = persisted.progress,
            answered = persisted.answeredQuestions,
            totalQuestions = persisted.totalQuizCount
        )

    private suspend fun fetchFreshQueue() {
        clearSessionState()

        val result = syncOrchestrator.syncQueue()
        // Again after the sync, which may have been the first to bring SRS systems in.
        assignmentRepository.warmSrsSystemCache()
        when (result) {
            is ApiResult.Error -> {
                // Auth errors require user action (re-login) — surface them explicitly. Network
                // errors auto-fall back to cached data so the user can study without connectivity,
                // consistent with the dashboard's own offline behavior.
                if (result.isAuthError) {
                    _uiState.update { it.copy(phase = LessonUiState.Phase.Error(result.message)) }
                } else {
                    buildLessonSelectionFromCache()
                }
            }
            is ApiResult.Success -> buildLessonSelectionFromCache()
        }
    }

    /** Builds the lesson-selection phase straight from Room, without attempting a network refresh
     *  first — shared by [fetchFreshQueue]'s success branch and [studyOffline], which bypasses the
     *  refresh entirely (bound to the error screen's "Study offline" action, for when the refresh
     *  itself is what failed but a previously-cached queue is still available). */
    private suspend fun buildLessonSelectionFromCache() {
        val currentLevel = statsRepository.observeCurrentLevel().first() ?: 0
        val lessonsToday = assignmentStatsRepository.observeLessonsCompletedToday().first()
        val settings = settingsRepository.settings.first()
        val dailyGoal = settings.dailyLessonGoal
        val batchSize = LessonSessionPlanner.normalizeBatchSize(settings.lessonBatchSize)
        // Retained so a later sort change re-orders the same queue instead of re-reading Room.
        val picker = LessonPicker(
            queue = assignmentRepository.observeLessonQueue().first(),
            levelUpProgress = assignmentStatsRepository.observeLevelUpProgress(currentLevel).first(),
            isStrained = lessonsToday >= dailyGoal
        )
        this.picker = picker
        val items = picker.sorted(LessonSort.DEFAULT)
        if (items.isEmpty()) {
            _uiState.update { it.copy(phase = LessonUiState.Phase.NoLessonsAvailable) }
            return
        }
        // One batch by default, shrunk to whatever today's goal still allows — see
        // LessonSessionPlanner.defaultSelectionSize.
        val defaultSelectionSize = LessonSessionPlanner.defaultSelectionSize(
            availableCount = items.size,
            batchSize = batchSize,
            remainingDailyGoal = dailyGoal - lessonsToday
        )
        _uiState.update {
            it.copy(
                phase = LessonUiState.Phase.Select(
                    availableLessons = items,
                    selectedAssignmentIds = items.take(defaultSelectionSize).map { it.assignmentId }.toSet(),
                    batchSize = batchSize
                )
            )
        }
    }

    /** Bound to the error screen's "Study offline" action — builds the lesson queue from whatever
     *  was cached as of the last successful sync instead of retrying the network refresh that just
     *  failed in [fetchFreshQueue]. */
    override fun studyOffline() {
        viewModelScope.launch {
            clearSessionState()
            buildLessonSelectionFromCache()
        }
    }

    private inline fun updateSelect(transform: (LessonUiState.Phase.Select) -> LessonUiState.Phase.Select) {
        _uiState.update { state ->
            val select = state.phase as? LessonUiState.Phase.Select ?: return@update state
            state.copy(phase = transform(select))
        }
    }

    override fun toggleLessonSelection(assignmentId: Long) {
        updateSelect { select ->
            val selected = select.selectedAssignmentIds.toMutableSet()
            if (!selected.add(assignmentId)) selected.remove(assignmentId)
            select.copy(selectedAssignmentIds = selected)
        }
    }

    override fun selectFirst(count: Int) {
        updateSelect { select -> select.copy(selectedAssignmentIds = select.availableLessons.take(count).map { it.assignmentId }.toSet()) }
    }

    /** Selects everything on offer. */
    override fun selectAll() {
        updateSelect { select -> select.copy(selectedAssignmentIds = select.availableLessons.map { it.assignmentId }.toSet()) }
    }

    override fun selectNone() {
        updateSelect { select -> select.copy(selectedAssignmentIds = emptySet()) }
    }

    /** Toggles a whole subject type at once — "select all kanji", or clear them again on a second
     *  tap. A partially selected type completes rather than clearing, so the first tap always lands
     *  on "all of them" and only a fully selected type empties. No-op for a type the queue has none
     *  of. */
    override fun toggleLessonTypeSelection(type: SubjectType) {
        updateSelect { select ->
            val assignmentIds = select.availableLessons
                .filter { it.subjectType == type }
                .map { it.assignmentId }
                .toSet()
            if (assignmentIds.isEmpty()) return@updateSelect select
            val allSelected = assignmentIds.all { it in select.selectedAssignmentIds }
            val selected = if (allSelected) {
                select.selectedAssignmentIds - assignmentIds
            } else {
                select.selectedAssignmentIds + assignmentIds
            }
            select.copy(selectedAssignmentIds = selected)
        }
    }

    /** Re-orders the picker's queue. Selection is untouched — it's a set of assignment ids, so the
     *  same lessons stay selected and simply sit in a different order (which is what the slider's
     *  "first N" and the session's batch slicing then follow). */
    override fun setLessonSort(sort: LessonSort) {
        updateSelect { select ->
            val picker = picker ?: return@updateSelect select
            if (select.sort == sort) return@updateSelect select
            select.copy(availableLessons = picker.sorted(sort), sort = sort)
        }
    }

    /** Commits to a session: the selection becomes a frozen plan, sliced into batches, and the first
     *  batch's flashcards open. */
    override fun startSelectedLessons() {
        val select = _uiState.value.phase as? LessonUiState.Phase.Select ?: return
        val selected = select.availableLessons.filter { it.assignmentId in select.selectedAssignmentIds }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            clearSessionState()
            session = LessonSession(
                plan = selected.map { it.assignmentId },
                batchSize = LessonSessionPlanner.normalizeBatchSize(select.batchSize),
                itemsById = selected.associateBy { it.assignmentId }
            )
            sessionController.begin()
            enterStudyPhase(0)
        }
    }

    /** Opens batch [index]'s flashcards. Only this batch's stroke-order extras are resolved here;
     *  pitch accents and related subjects are watched live instead (see the collectors in init), per
     *  batch because a 40-item session shouldn't hold a Room query open for every item it will ever
     *  show. */
    private suspend fun enterStudyPhase(index: Int) {
        val items = session.batchItems(index)
        if (items.isEmpty()) {
            // Nothing left to study: the plan is empty, or this batch's items are gone from the cache.
            // A fresh queue is a better outcome here than an empty summary.
            sessionController.complete()
            clearSessionState()
            fetchFreshQueue()
            return
        }

        val strokeOrders = fetchStrokeOrders(items)
        // Replaces rather than merges: the observation follows one batch at a time, and the cleanup
        // pass (which reaches back across batches) re-points it at the items it asks about. Also
        // drives the related-subjects collector in init{} off the same items-in-play stream.
        pitchAccentItems.value = items
        session = session.copy(batchIndex = index)
        sessionTiming.resume()
        _uiState.update {
            it.copy(
                phase = LessonUiState.Phase.Study(
                    studyItems = items,
                    studyIndex = 0,
                    batchIndex = index,
                    batchCount = session.batchCount,
                    strokeOrderBySubjectId = strokeOrders
                )
            )
        }
        persistStudySnapshot(0)
    }

    /** Stroke data is keyed purely by character (same lookup [SubjectDetailViewModel] uses), so only
     *  single-glyph items — kanji, and any radical with a real Unicode glyph — resolve to anything
     *  other than [StrokeOrderUiState.Unavailable]. Fanned out in parallel for the same reason
     *  [fetchRelatedSubjects] is: a large batch shouldn't serialize dozens of lookups. */
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

    override fun onStudyCardSwiped(index: Int) {
        val study = _uiState.value.phase as? LessonUiState.Phase.Study ?: return
        if (index !in study.studyItems.indices) return
        updateStudy { it.copy(studyIndex = index) }
        viewModelScope.launch { persistStudySnapshot(index) }
    }

    override fun nextStudyCard() {
        val study = _uiState.value.phase as? LessonUiState.Phase.Study ?: return
        val nextIndex = study.studyIndex + 1
        if (nextIndex >= study.studyItems.size) {
            viewModelScope.launch { beginBatchQuiz(study.studyItems) }
        } else {
            updateStudy { it.copy(studyIndex = nextIndex) }
            viewModelScope.launch { persistStudySnapshot(nextIndex) }
        }
    }

    override fun previousStudyCard() {
        val study = _uiState.value.phase as? LessonUiState.Phase.Study ?: return
        if (study.studyIndex == 0) return
        val previousIndex = study.studyIndex - 1
        updateStudy { it.copy(studyIndex = previousIndex) }
        viewModelScope.launch { persistStudySnapshot(previousIndex) }
    }

    /** Persists just enough to resume mid-flashcard-study: which batch, and which card of it the
     *  learner is on. The batch's items themselves are derived from the plan (see
     *  [LessonSessionPlanner]), so nothing else needs writing. Called on every card change rather than
     *  only at study's start, so a resume lands on the exact card left off on, not card one. */
    private suspend fun persistStudySnapshot(index: Int) {
        sessionController.persist(session.studySnapshot(index, sessionTiming.currentElapsedMs()))
    }

    /** Builds and starts the current batch's quiz. The queue holds only this batch's items, so the
     *  shuffle that follows interleaves a handful of just-studied items rather than every item the
     *  learner selected — which is the whole point of quizzing per batch. */
    private suspend fun beginBatchQuiz(batch: List<LessonItem>) {
        sessionController.begin()
        // A new pass over this batch's questions. Progress for its items is added, not replaced: a
        // resumed session's progress for this batch is already loaded, and the session-wide tallies
        // deliberately survive across batches so the summary at the end still covers the whole session.
        val quiz = session.quiz.withQuestionsFor(batch).withProgressFor(batch)
        session = session.copy(quiz = quiz)

        // The quiz asks about exactly the batch that was just studied, so the live observation
        // stays pointed at the same items — no update needed when it already is (StateFlow conflates
        // an equal list), and a re-point when a resumed session reaches its quiz directly.
        pitchAccentItems.value = batch
        val next = quiz.current
        if (next == null) {
            // Nothing to ask — every item in this batch was already fully learned before quizzing began
            // (only reachable from a resumed snapshot). Move on as if the batch had been quizzed, rather
            // than ever constructing a Quiz phase with no question.
            enterCheckpoint(nextBatchIndex = session.batchIndex + 1)
            return
        }

        // Not restarted: the session clock spans the whole session, study passes included. Only the
        // per-question clock starts fresh here.
        sessionTiming.resume()
        val questionStartedAt = questionTiming.restart()
        _uiState.update {
            it.copy(
                phase = LessonUiState.Phase.Quiz(
                    question = QuizQuestionState(
                        item = next.item,
                        type = next.type,
                        timing = QuizTimingUiState(
                            sessionActiveElapsedMs = sessionTiming.elapsedMs,
                            sessionActiveSegmentStartMs = sessionTiming.segmentStartMs,
                            questionActiveSegmentStartMs = questionStartedAt
                        )
                    ),
                    batchIndex = session.batchIndex,
                    batchCount = session.batchCount,
                    totalQuizCount = quiz.totalQuestions,
                    remainingQuizCount = quiz.totalQuestions
                )
            )
        }
        persistCurrentState()
    }



    /** The autoplay audio and reading hint for a just-graded (and, if gated, now revealed) reading
     *  question — held back while a wrong answer's text is still gated behind "require tap to
     *  reveal answer" (see [gradeAnswer]/[revealAnswer]), so a learner can't hear or see the correct
     *  reading before choosing to look at the answer. The pitch patterns themselves are gated
     *  separately at render time (QuizQuestionContent reads them live off
     *  [LessonUiState.pitchAccentsBySubjectId], which isn't scoped to grading/reveal at all). */
    override fun publishReadingRevealEffects(item: LessonItem, type: QuestionType, candidates: List<String>, settings: AppSettings) {
        // isPitchAccentEligible (matching the live collection's own filter) is enforced explicitly
        // here too — a kanji/radical item's map entry would simply be absent, but answerReading itself
        // has no such natural gate, so without this check it would still surface the reading (with no
        // pitch accent alongside it) for a kanji reading question, which the shared vocabulary-only
        // scoping rule says it shouldn't.
        val answerReading = if (type == QuestionType.READING && settings.showAnswerReadingPitchAccent && isPitchAccentEligible(item.subjectType)) {
            item.readings.firstOrNull()
        } else {
            null
        }
        // Selected here, where the item and the settings are already in hand, so the hint's row only
        // has to render whatever clip this produced — null when none survives the mp3-only filter.
        // The pitch patterns are *not* copied here: the hint reads them live off
        // LessonUiState.pitchAccentsBySubjectId, so a write from any source reaches it in place.
        val answerReadingAudio = answerReading?.let { reading ->
            selectAudioFor(item.pronunciationAudios, reading, mp3Only = settings.restrictAudioToMp3)
        }
        updateGrade {
            it.copy(answerHint = answerReading?.let { reading -> AnswerReadingHint(reading = reading, audio = answerReadingAudio) })
        }

        if (type == QuestionType.READING && settings.autoplayPronunciationAudio) {
            candidates.firstOrNull()?.let { reading ->
                pronunciationAudioPlayer.playMatchingReading(item.pronunciationAudios, reading, mp3Only = settings.restrictAudioToMp3)
            }
        }
    }

    /** Reverts the most recent incorrect answer — for a typo, not a genuine miss. Unlike
     *  ReviewViewModel.undoLastAnswer(), a correct answer here can't be undone — lesson-start
     *  submission isn't deferred to Continue the way a review grade is, so by the time feedback is
     *  showing it's already committed. The queue/progress mutation for the incorrect-answer case
     *  is shared via [undoLastIncorrectAnswer]. */
    override fun undoLastAnswer() {
        val feedback = question?.feedback ?: return
        if (feedback.isCorrect) return

        viewModelScope.launch {
            val undone = session.quiz.undoLastGrade() ?: return@launch
            session = session.copy(quiz = undone)
            persistCurrentState()
            // Restarts this question's clock so the retry's timing doesn't inherit time spent
            // before the undo.
            val questionStartedAt = questionTiming.restart()

            // Back to asking the same question: the grade and its hint go as a whole. undoCounter
            // changes even though the item and type don't; it is what the answer field's
            // focus-restoring effect keys on.
            updateQuizPhase { quiz ->
                quiz.copy(
                    remainingQuizCount = undone.remainingQuestions,
                    question = quiz.question.retried(questionStartedAt)
                )
            }
        }
    }

    override suspend fun gradeAnswer(
        item: LessonItem,
        type: QuestionType,
        isCorrect: Boolean,
        candidates: List<String>,
        wasCloseMatch: Boolean,
        isGiveUp: Boolean
    ) {
        val questionElapsedMs = questionTiming.freeze()
        val quiz = session.quiz.grade(isCorrect = isCorrect, elapsedMs = questionElapsedMs)
        val graded = quiz.lastGraded ?: return
        // An item's lesson is done once every question type it has is answered correctly — and it
        // is marked started only the first time, which both the rank-change chip and the outbox
        // enqueue below agree on.
        val isNewlyStarted = graded.completedItem && item.assignmentId !in session.startedAssignmentIds
        session = session.copy(
            quiz = quiz,
            startedAssignmentIds = if (isNewlyStarted) session.startedAssignmentIds + item.assignmentId else session.startedAssignmentIds
        )

        // Whether this answer was the very last one due in this pass — either the session is over
        // outright, or what comes next is a batch checkpoint rather than another question. See
        // commitGradeDurably for why the two cases persist differently.
        val queueIsEmpty = quiz.current == null
        val snapshot = session.quizSnapshot(sessionTiming.currentElapsedMs())

        // Computed synchronously against AssignmentRepository's in-memory SRS-system cache (warmed
        // once when the queue loaded) — zero DB access on this critical path, same as Review's
        // rank-change chip. Every lesson item starts the same way (locked straight to the SRS
        // system's starting stage), so unlike Review this doesn't depend on whether the answer was
        // actually correct — it only fires once, the first time the item's lesson is fully done.
        val newRankChange = if (isNewlyStarted) assignmentRepository.computeLessonStartRankChange(item.srsSystemId) else null

        // Reads the field kept warm by the settings collector in init{} instead of
        // `settingsRepository.settings.first()` — see `latestSettings`'s doc comment.
        val settings = latestSettings
        // Whether this grade is visible right away, or gated behind an explicit revealAnswer() tap —
        // see QuizGrade.answerRevealed.
        val revealedNow = isCorrect || isGiveUp || !requiresTapToRevealAnswer(settings, type)

        val answerGrade = QuizGrade.of(
            isCorrect, candidates, wasCloseMatch, answerRevealed = revealedNow, rankChange = newRankChange
        )
        updateQuizPhase { phase ->
            phase.copy(
                remainingQuizCount = quiz.remainingQuestions,
                question = phase.question.graded(answerGrade, elapsedMs = questionElapsedMs)
            )
        }

        // Withheld entirely while gated — a wrong reading answer's audio/hint wait for
        // revealAnswer() to trigger them instead, same as its answer text.
        if (revealedNow) {
            publishReadingRevealEffects(item, type, candidates, settings)
        }

        commitGradeDurably(isNewlyStarted, item, snapshot, queueIsEmpty)
    }

    private suspend fun persistCurrentState() {
        sessionController.persist(session.quizSnapshot(sessionTiming.currentElapsedMs()))
    }

    /** True when the batch that just ended was the session's last, i.e. [advanceQuiz] will go straight
     *  to the summary. Lets [commitGradeDurably] complete the session outright instead of saving a
     *  checkpoint snapshot that the very next Continue tap would replace. */
    private fun isSessionOverAfterCurrentPass(queueIsEmpty: Boolean): Boolean =
        queueIsEmpty && session.isOnLastBatch

    /** Runs the post-grading durability writes (outbox enqueue, session persistence), as one queued
     *  unit via [sessionController]'s `alongside` parameter — not as a separately-awaited suspension
     *  before it, which would let a concurrent reader (e.g. a test asserting against
     *  [sessionController] right after the next emitted uiState) observe the outbox enqueue as done
     *  but the session write as not yet applied. */
    private suspend fun commitGradeDurably(isNewlyStarted: Boolean, item: LessonItem, snapshot: PersistedLessonSession, queueIsEmpty: Boolean) {
        val outboxWork: suspend () -> Unit = {
            if (isNewlyStarted) {
                assignmentRepository.applyOptimisticLessonStart(item.assignmentId, item.srsSystemId)
                outboxRepository.enqueueLessonStart(item.assignmentId, item.subjectId)
            }
        }
        when {
            // The session is over: complete now, exactly as a single-batch session always did — the
            // Continue tap that follows only has to render the summary.
            isSessionOverAfterCurrentPass(queueIsEmpty) -> sessionController.complete(alongside = outboxWork)
            // A batch just ended (or the session's last batch, with misses left to offer): record the
            // checkpoint, so a crash on the feedback screen resumes at the checkpoint instead of
            // re-asking questions that were already answered.
            queueIsEmpty -> sessionController.persist(snapshot, alongside = outboxWork)
            else -> sessionController.persist(snapshot, alongside = outboxWork)
        }
    }

    private inline fun updateQuizPhase(transform: (LessonUiState.Phase.Quiz) -> LessonUiState.Phase.Quiz) {
        _uiState.update { state ->
            val quiz = state.phase as? LessonUiState.Phase.Quiz ?: return@update state
            state.copy(phase = transform(quiz))
        }
    }

    override fun onContinue() {
        viewModelScope.launch { advanceQuiz() }
    }

    /** Walks from a just-finished batch into the next one — the checkpoint's primary action. */
    override fun continueSession() {
        val checkpoint = _uiState.value.phase as? LessonUiState.Phase.BatchComplete ?: return
        viewModelScope.launch { enterStudyPhase(checkpoint.batchIndex + 1) }
    }

    /** "Finish for now": keeps the session where it is instead of abandoning it. Nothing needs writing
     *  here — [enterCheckpoint] already persisted the checkpoint — so this only asks the screen to
     *  navigate back, where the dashboard will offer to resume at the next batch. */
    override fun finishForNow() {
        if (_uiState.value.phase !is LessonUiState.Phase.BatchComplete) return
        _uiState.update { it.copy(exit = LessonUiState.ExitRequest.Parked) }
    }

    override fun abandonSession() {
        viewModelScope.launch {
            sessionController.abandon()
            clearSessionState()
            _uiState.update { it.copy(exit = LessonUiState.ExitRequest.Abandoned) }
        }
    }

    /** Items learned, how many were correct without ever missing, which were missed at least once,
     *  and timing — mirrors ReviewViewModel.sessionSummary(). "Missed" here means at least one wrong
     *  attempt during the quiz, not a real SRS miss — every lesson item is requeued until correct.
     *  Spans every batch of the session. */
    private fun sessionSummary(): QuizSessionSummary<LessonItem> =
        session.quiz.summary(sessionTiming.currentElapsedMs())

    /** Snapshots a just-completed session's summary so it can be revisited later from the
     *  dashboard, after this ViewModel (and its otherwise-ephemeral session-complete state) is
     *  gone. Mirrors ReviewViewModel.persistLastSessionSummary(). */
    private fun persistLastSessionSummary(summary: QuizSessionSummary<LessonItem>) {
        applicationScope.launch {
            lastSessionSummaryRepository.save(
                summary.toLastSessionSummary(
                    kind = LastSessionKind.LESSON,
                    completedAtMillis = Clock.System.now().toEpochMilliseconds()
                )
            )
        }
    }

    /** Ends the session and shows its summary — terminal: the persisted snapshot is cleared before the
     *  summary renders, so nothing can save a session the learner has already finished. */
    private suspend fun finishSession() {
        sessionTiming.freeze()
        sessionController.complete()
        outboxRepository.requestSyncNow()
        val summary = sessionSummary()
        persistLastSessionSummary(summary)
        // The summary has no pitch accents to show, so the live observation that belonged to the
        // pass just finished is done with.
        pitchAccentItems.value = emptyList()
        _uiState.update {
            it.copy(
                phase = summary.toCompletePhase(),
                pitchAccentsBySubjectId = emptyMap()
            )
        }
    }

    /** Shows the checkpoint at the end of a pass over batch [nextBatchIndex] - 1.
     *
     *  Stops the session clock first: deciding whether to continue isn't studying, and letting the
     *  interstitial bill the session clock would inflate both its total and its per-item average.
     *  When there's no next batch and nothing missed, this is the same "session is over" case
     *  [commitGradeDurably] already completed at grading time — covered here for the paths that reach a
     *  checkpoint without grading a last answer. */
    private suspend fun enterCheckpoint(nextBatchIndex: Int) {
        sessionTiming.freeze()

        val completedBatchIndex = nextBatchIndex - 1
        val completedProgress = session.batches.getOrNull(completedBatchIndex).orEmpty()
            .mapNotNull { session.quiz.progress[it] }
        val missedInBatch = completedProgress
            .filter { it.hadIncorrectMeaning || it.hadIncorrectReading }
            .map { it.item }
        if (nextBatchIndex >= session.batchCount) {
            finishSession()
            return
        }

        _uiState.update {
            it.copy(
                // Nothing pitch-related is on screen at a checkpoint any more, so drop the
                // observation's items that belonged to the pass just finished.
                pitchAccentsBySubjectId = emptyMap(),
                phase = LessonUiState.Phase.BatchComplete(
                    batchIndex = completedBatchIndex,
                    batchCount = session.batchCount,
                    itemsLearned = completedProgress.size,
                    itemsCorrectFirstTry = completedProgress.count { p -> !p.hadIncorrectMeaning && !p.hadIncorrectReading },
                    missedItems = missedInBatch,
                    remainingSessionItems = session.batches.drop(nextBatchIndex).sumOf { it.size }
                )
            )
        }
        pitchAccentItems.value = emptyList()
        sessionController.persist(session.checkpointSnapshot(sessionTiming.currentElapsedMs()))
    }

    private suspend fun advanceQuiz() {
        val next = session.quiz.current
        if (next != null) {
            val questionStartedAt = questionTiming.restart()
            // A new question, whole: nothing about the last one — its grade, hint, typed answer or
            // refused-script count — carries over. Only the session clock does.
            updateQuizPhase { quiz ->
                quiz.copy(
                    remainingQuizCount = session.quiz.remainingQuestions,
                    question = QuizQuestionState(
                        item = next.item,
                        type = next.type,
                        sequence = quiz.question.sequence + 1,
                        timing = quiz.question.timing.copy(
                            questionActiveElapsedMs = 0L,
                            questionActiveSegmentStartMs = questionStartedAt,
                            questionElapsedMs = null
                        )
                    )
                )
            }
            return
        }

        // The pass is over, and its durability write already happened at grading time (see
        // commitGradeDurably), so this only has to decide where the learner goes next: the next batch's
        // checkpoint, or the summary when that was the last batch.
        enterCheckpoint(nextBatchIndex = session.batchIndex + 1)
    }
}
