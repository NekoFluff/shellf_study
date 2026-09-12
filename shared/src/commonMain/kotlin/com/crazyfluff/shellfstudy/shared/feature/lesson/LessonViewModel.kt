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
import com.crazyfluff.shellfstudy.shared.data.DEFAULT_LESSON_BATCH_SIZE
import com.crazyfluff.shellfstudy.shared.data.LastSessionKind
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummary
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.PersistedAnsweredQuestion
import com.crazyfluff.shellfstudy.shared.data.PersistedItemProgress
import com.crazyfluff.shellfstudy.shared.data.PersistedLessonPhase
import com.crazyfluff.shellfstudy.shared.data.PersistedQuestion
import com.crazyfluff.shellfstudy.shared.data.PersistedQuizRound
import com.crazyfluff.shellfstudy.shared.data.PersistedLessonSession
import com.crazyfluff.shellfstudy.shared.data.PitchAccentRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.SubjectRepository
import com.crazyfluff.shellfstudy.shared.data.model.LessonItem
import com.crazyfluff.shellfstudy.shared.data.model.LevelUpProgress
import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import com.crazyfluff.shellfstudy.shared.data.model.RankChange
import com.crazyfluff.shellfstudy.shared.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.shared.data.StrokeOrderRepository
import com.crazyfluff.shellfstudy.shared.designsystem.strokeorder.StrokeOrderUiState
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentUiState
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
import com.crazyfluff.shellfstudy.shared.quiz.isPitchAccentEligible
import com.crazyfluff.shellfstudy.shared.quiz.questionTypesFor
import com.crazyfluff.shellfstudy.shared.quiz.summarizeQuizSession
import com.crazyfluff.shellfstudy.shared.quiz.toSessionAnswerRow
import com.crazyfluff.shellfstudy.shared.quiz.toSessionMissedItemRow
import com.crazyfluff.shellfstudy.shared.quiz.undoLastIncorrectAnswer
import com.crazyfluff.shellfstudy.shared.session.LessonSessionController
import kotlin.time.Clock
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

/** Which pass over the current queue a [LessonUiState.Phase.Quiz] is showing — the batch's own quiz
 *  over material just studied, or the optional extra pass over everything missed during the session
 *  (see [LessonUiState.Phase.BatchComplete.NextStep.PracticeMissed]). Only a cleanup pass can ask
 *  about an item whose lesson has already been committed. */
enum class QuizRound { LESSON, CLEANUP }

data class LessonUiState(
    val phase: Phase = Phase.Loading,
    val settings: DisplaySettings = DisplaySettings(),
    // Deliberately not folded into Phase — leaving the screen is a one-shot navigation signal, not a
    // rendering mode. The screen keeps rendering whatever phase was showing for one more frame while
    // a LaunchedEffect(exit) fires the actual back-navigation. One sealed value rather than two
    // booleans because the two ways out are mutually exclusive and mean opposite things to the
    // dashboard: abandoning drops the session, parking keeps it resumable.
    val exit: ExitRequest = ExitRequest.None,
    /** The pitch-accent knowledge for every item currently in play (this batch's study cards and its
     *  quiz), keyed by subject id. Hoisted to the top level rather than carried per-phase because the
     *  study card and the quiz hint are two views of the *same* live observation — the repository's
     *  Room flow is the single source of truth, so a scrape from any writer (the detail sheet, the
     *  background worker, this screen's own check) reaches both without a refetch or any propagation
     *  code. A subject absent from the map is "not checked yet". */
    val pitchAccentsBySubjectId: Map<Long, PitchAccentUiState> = emptyMap(),
    /** True while [LessonViewModel.checkPitchAccent]'s on-demand scrape is in flight, so the quiz
     *  hint's "not checked yet" caption can show progress instead of looking like a dead tap. */
    val isCheckingPitchAccent: Boolean = false,
    /** True when the last on-demand check failed. A failed scrape leaves the word classified as
     *  still-unknown (see [PitchAccentRepository.scrapeAndCache]), i.e. indistinguishable from an
     *  untried one, so the UI has to say the check failed itself. Cleared when a new check starts and
     *  when the current question changes. */
    val pitchAccentCheckFailed: Boolean = false
) {
    /** Why the learner is leaving the lesson screen, if they are. */
    sealed interface ExitRequest {
        data object None : ExitRequest

        /** The session was discarded — there is nothing left for the dashboard to resume. */
        data object Abandoned : ExitRequest

        /** "Finish for now": the session is kept exactly where it was left, so the dashboard offers to
         *  resume it at the next batch. */
        data object Parked : ExitRequest
    }

    /** Settings-derived display flags — hoisted here rather than duplicated into every [Phase]
     *  variant, since they apply uniformly regardless of phase (never null/absent in one phase and
     *  required in another) and are only ever read, never used to decide which phase to render. */
    data class DisplaySettings(
        val showPitchAccent: Boolean = true,
        val showSubjectTypeLabel: Boolean = false,
        val showTotalTimer: Boolean = false,
        val showQuestionTimer: Boolean = false,
        val useJapaneseKeyboard: Boolean = false,
        val showAnswerReadingPitchAccent: Boolean = false,
        // Carried into the UI so reading rows can select their own clip the same way playback
        // autoplay does, instead of the screen having to know which of a word's clips are eligible.
        val restrictAudioToMp3: Boolean = false,
        val hideContextSentenceTranslations: Boolean = true
    )

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
            val relatedSubjectsById: Map<Long, SubjectSummary> = emptyMap(),
            val strokeOrderBySubjectId: Map<Long, StrokeOrderUiState> = emptyMap()
        ) : Phase {
            val isLastCardInBatch: Boolean get() = studyIndex == studyItems.lastIndex
        }

        data class Quiz(
            // Non-nullable by construction — a next-question decision always branches into either a
            // Quiz with a real item, or on to a checkpoint/Complete; there's no way to construct a
            // Quiz value with nothing to show.
            val currentItem: LessonItem,
            val currentQuestionType: QuestionType,
            val round: QuizRound = QuizRound.LESSON,
            val batchIndex: Int = 0,
            val batchCount: Int = 1,
            val answerInput: String = "",
            val feedback: AnswerFeedback? = null,
            val rankChange: RankChange? = null,
            val undoCounter: Int = 0,
            val isDetailsExpanded: Boolean = false,
            val answerTypeMismatchCount: Int = 0,
            val totalQuizCount: Int = 0,
            val remainingQuizCount: Int = 0,
            val timing: QuizTimingUiState = QuizTimingUiState(),
            val answerReading: String? = null,
            val answerReadingAudio: PronunciationAudio? = null
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
            val next: NextStep
        ) : Phase {
            sealed interface NextStep {
                /** More of the plan left to study. */
                data class StudyBatch(
                    val batchIndex: Int,
                    /** Items left in the whole session, the next batch included. */
                    val remainingSessionItems: Int
                ) : NextStep

                /** Every batch is studied and quizzed; all that's left is one optional extra pass over
                 *  what was missed along the way. */
                data class PracticeMissed(val itemCount: Int) : NextStep
            }
        }

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

/** Which halves of an item have been missed so far — what the end-of-session cleanup pass asks about,
 *  as a closed mapping rather than a pair of booleans repeated at the call site. */
private fun LessonItemProgress.missedQuestionTypes(): List<QuestionType> = buildList {
    if (hadIncorrectMeaning) add(QuestionType.MEANING)
    if (hadIncorrectReading) add(QuestionType.READING)
}

@OptIn(ExperimentalCoroutinesApi::class)
class LessonViewModel(
    private val assignmentRepository: AssignmentRepository,
    private val statsRepository: StatsRepository,
    private val outboxRepository: OutboxRepository,
    private val sessionController: LessonSessionController,
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

    // The frozen plan for the session in progress: the ordered assignment ids the learner committed to
    // at "Start session", sliced into batches by batchSize (see LessonSessionPlanner). Held as ids
    // rather than LessonItems so it persists and restores verbatim, and resolved on demand through
    // itemsById — by id rather than through observeLessonQueue()'s due filter, because an item leaves
    // that filter the moment its lesson completes, even though it stays part of this session's progress
    // tally (and of the cleanup pass, which may reach back to any batch).
    private var planAssignmentIds: List<Long> = emptyList()
    private var batchSize: Int = DEFAULT_LESSON_BATCH_SIZE
    private var currentBatchIndex = 0
    private var itemsById: Map<Long, LessonItem> = emptyMap()
    private var currentRound = QuizRound.LESSON

    // The picker's inputs, kept as plain fields so a sort change can re-order the queue without
    // another fetch: what Room handed back, plus the level-up context LessonPrioritizer needs. Only
    // ever read while a Select phase is showing, and always overwritten together by
    // buildLessonSelectionFromCache.
    private var lessonQueue: List<LessonItem> = emptyList()
    private var currentLevelUpProgress = LevelUpProgress(kanjiGuruedOrHigher = 0, kanjiTotal = 0)
    private var isStrained = false

    // The session's batches, still as ids — see LessonSessionPlanner for why the plan is persisted as
    // ids plus a batch size rather than as explicit boundaries.
    private val sessionBatches: List<List<Long>> get() = LessonSessionPlanner.batches(planAssignmentIds, batchSize)

    private val batchCount: Int get() = sessionBatches.size

    /** The resolvable items of one batch, in plan order. Ids the cache no longer has are dropped —
     *  [resumeFromPersisted] rejects a session with holes in it up front, so in practice this only
     *  filters an item deleted between batches. */
    private fun batchItems(index: Int): List<LessonItem> =
        sessionBatches.getOrNull(index).orEmpty().mapNotNull { itemsById[it] }

    private val quizQueue = QuizQueue<LessonItem>()
    private val startedAssignmentIds = mutableSetOf<Long>()
    private var totalQuizCount = 0

    private val gradingGuard = QuizGradingGuard(viewModelScope)

    private val progressByAssignmentId = mutableMapOf<Long, LessonItemProgress>()
    /** The items whose pitch accents the screen can currently show — this batch's study cards, or
     *  the quiz's own items (the current batch's, or the cleanup pass's misses). Held as a separate
     *  flow so the observation follows the session's phases: flatMapLatest swaps the whole set of
     *  per-item Room flows when the learner moves from one batch to the next, instead of holding one
     *  flow per item of the entire session open for its duration. What it produces lands in
     *  [LessonUiState.pitchAccentsBySubjectId] — see the collector in init. */
    private val pitchAccentItems = MutableStateFlow<List<LessonItem>>(emptyList())
    // Individual per-answer records, used for the "slowest answers" summary — persisted and
    // restored across a resume just like progressByAssignmentId (see resumeQuizPhase), so the
    // summary reflects the whole session, not just the segment since the most recent resume.
    private val answeredQuestions = mutableListOf<AnsweredQuestionRecord<LessonItem>>()

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
                            useJapaneseKeyboard = settings.useJapaneseKeyboard,
                            showAnswerReadingPitchAccent = settings.showAnswerReadingPitchAccent,
                            restrictAudioToMp3 = settings.restrictAudioToMp3,
                            hideContextSentenceTranslations = settings.hideContextSentenceTranslations
                        )
                    )
                }
            }
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
        planAssignmentIds = emptyList()
        itemsById = emptyMap()
        currentBatchIndex = 0
        currentRound = QuizRound.LESSON
        quizQueue.clear()
        startedAssignmentIds.clear()
        progressByAssignmentId.clear()
        answeredQuestions.clear()
        pitchAccentItems.value = emptyList()
        totalQuizCount = 0
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

        planAssignmentIds = plan.sessionAssignmentIds
        batchSize = LessonSessionPlanner.normalizeBatchSize(plan.batchSize)
        itemsById = resolved
        currentBatchIndex = plan.batchIndex
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
        val items = batchItems(persisted.batchIndex)
        if (items.isEmpty()) {
            sessionController.complete()
            clearSessionState()
            fetchFreshQueue()
            return
        }

        val (relatedSubjects, strokeOrders) = coroutineScope {
            val relatedSubjectsDeferred = async { fetchRelatedSubjects(items) }
            val strokeOrdersDeferred = async { fetchStrokeOrders(items) }
            relatedSubjectsDeferred.await() to strokeOrdersDeferred.await()
        }
        pitchAccentItems.value = items
        currentRound = QuizRound.LESSON
        sessionTiming.elapsedMs = persisted.sessionActiveElapsedMs
        sessionController.begin()
        _uiState.update {
            it.copy(
                phase = LessonUiState.Phase.Study(
                    studyItems = items,
                    studyIndex = persisted.studyIndex.coerceIn(0, items.lastIndex),
                    batchIndex = persisted.batchIndex,
                    batchCount = batchCount,
                    relatedSubjectsById = relatedSubjects,
                    strokeOrderBySubjectId = strokeOrders
                )
            )
        }
        sessionTiming.resume()
    }

    private suspend fun resumeQuizPhase(persisted: PersistedLessonSession) {
        // itemsById was resolved from the whole plan in resumeFromPersisted, so a queue entry can only
        // miss if the snapshot references an assignment outside its own plan — genuinely corrupt,
        // rather than merely "no longer due for lesson".
        if (persisted.quizQueue.any { it.assignmentId !in itemsById }) {
            sessionController.complete()
            clearSessionState()
            fetchFreshQueue()
            return
        }

        // resumeQuizPhase skips Phase.Study entirely (a mid-quiz resume), so nothing has told the
        // live observation which items are in play — hand it the queue's own items, which is also
        // what makes a resumed session's hint pick up cache writes exactly like a normal one.
        pitchAccentItems.value = (persisted.quizQueue.map { it.assignmentId } + persisted.progress.map { it.assignmentId })
            .distinct()
            .mapNotNull { itemsById[it] }

        quizQueue.restore(
            persisted.quizQueue.map { entry ->
                PendingQuestion(itemsById.getValue(entry.assignmentId), QuestionType.valueOf(entry.questionType))
            }
        )
        startedAssignmentIds.clear()
        restoreProgressAndAnswers(persisted)
        currentRound = when (persisted.quizRound) {
            PersistedQuizRound.LESSON -> QuizRound.LESSON
            PersistedQuizRound.CLEANUP -> QuizRound.CLEANUP
        }
        // Restores the session's accumulated active time rather than restarting the clock — this is
        // deliberately *not* wall-clock time since the session began; time spent away (backgrounded, at
        // a checkpoint, or navigated off and back) must not count. sessionTiming.resume() below then
        // starts a fresh viewing segment on top of that restored base, so the clock resumes right where
        // it left off.
        val questionStartedAt = questionTiming.restart()
        // LessonSessionRepository.load() — reached here via sessionController.load() — guarantees a
        // non-empty quizQueue for a QUIZ-phase snapshot (see its resumability check).
        val next = requireNotNull(quizQueue.current) { "resumeQuizPhase: persisted QUIZ session had an empty queue despite the repository's resumability check" }
        sessionController.begin()
        _uiState.update {
            it.copy(
                phase = LessonUiState.Phase.Quiz(
                    currentItem = next.item,
                    currentQuestionType = next.type,
                    round = currentRound,
                    batchIndex = currentBatchIndex,
                    batchCount = batchCount,
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
        sessionTiming.resume()
    }

    /** Resumes a session parked at a batch checkpoint. A checkpoint at the end of the plan means every
     *  batch is done, so it resumes into the summary rather than re-offering the cleanup pass — that
     *  offer is a convenience, not something worth stranding a finished session behind. */
    private suspend fun resumeCheckpoint(persisted: PersistedLessonSession) {
        sessionController.begin()
        if (persisted.batchIndex < batchCount) {
            enterStudyPhase(persisted.batchIndex)
            return
        }
        restoreProgressAndAnswers(persisted)
        finishSession()
    }

    /** Rebuilds the session-wide tallies a resume needs in order to summarize (or keep grading) the
     *  whole session, from a checkpoint or quiz snapshot. */
    private fun restoreProgressAndAnswers(persisted: PersistedLessonSession) {
        progressByAssignmentId.clear()
        persisted.progress.forEach { p ->
            // itemsById was resolved by id in resumeFromPersisted, so this only misses for the same
            // genuinely-unrecoverable case handled there — not merely "no longer due for lesson".
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
        totalQuizCount = persisted.totalQuizCount
        sessionTiming.elapsedMs = persisted.sessionActiveElapsedMs
    }

    private suspend fun fetchFreshQueue() {
        clearSessionState()

        when (val result = assignmentRepository.refreshLessonQueue()) {
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
        val lessonsToday = assignmentRepository.observeLessonsCompletedToday().first()
        val settings = settingsRepository.settings.first()
        val dailyGoal = settings.dailyLessonGoal
        val batchSize = LessonSessionPlanner.normalizeBatchSize(settings.lessonBatchSize)
        // Retained so a later sort change re-orders the same queue instead of re-reading Room.
        lessonQueue = assignmentRepository.observeLessonQueue().first()
        currentLevelUpProgress = assignmentRepository.observeLevelUpProgress(currentLevel).first()
        isStrained = lessonsToday >= dailyGoal
        val items = LessonPrioritizer.prioritize(
            items = lessonQueue,
            levelUpProgress = currentLevelUpProgress,
            isStrained = isStrained,
            sort = LessonSort.DEFAULT
        )
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
    fun studyOffline() {
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

    /** Selects everything on offer. */
    fun selectAll() {
        updateSelect { select -> select.copy(selectedAssignmentIds = select.availableLessons.map { it.assignmentId }.toSet()) }
    }

    fun selectNone() {
        updateSelect { select -> select.copy(selectedAssignmentIds = emptySet()) }
    }

    /** Toggles a whole subject type at once — "select all kanji", or clear them again on a second
     *  tap. A partially selected type completes rather than clearing, so the first tap always lands
     *  on "all of them" and only a fully selected type empties. No-op for a type the queue has none
     *  of. */
    fun toggleLessonTypeSelection(type: SubjectType) {
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
    fun setLessonSort(sort: LessonSort) {
        updateSelect { select ->
            if (select.sort == sort) return@updateSelect select
            select.copy(
                availableLessons = LessonPrioritizer.prioritize(
                    items = lessonQueue,
                    levelUpProgress = currentLevelUpProgress,
                    isStrained = isStrained,
                    sort = sort
                ),
                sort = sort
            )
        }
    }

    /** Commits to a session: the selection becomes a frozen plan, sliced into batches, and the first
     *  batch's flashcards open. */
    fun startSelectedLessons() {
        val select = _uiState.value.phase as? LessonUiState.Phase.Select ?: return
        val selected = select.availableLessons.filter { it.assignmentId in select.selectedAssignmentIds }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            clearSessionState()
            planAssignmentIds = selected.map { it.assignmentId }
            batchSize = LessonSessionPlanner.normalizeBatchSize(select.batchSize)
            itemsById = selected.associateBy { it.assignmentId }
            sessionController.begin()
            enterStudyPhase(0)
        }
    }

    /** Opens batch [index]'s flashcards. Only this batch's related-subject and stroke-order extras
     *  are resolved here; pitch accents are watched live instead (see the collector in init), per
     *  batch because a 40-item session shouldn't hold a Room query open for every item it will ever
     *  show. */
    private suspend fun enterStudyPhase(index: Int) {
        val items = batchItems(index)
        if (items.isEmpty()) {
            // Nothing left to study: the plan is empty, or this batch's items are gone from the cache.
            // A fresh queue is a better outcome here than an empty summary.
            sessionController.complete()
            clearSessionState()
            fetchFreshQueue()
            return
        }

        val (relatedSubjects, strokeOrders) = coroutineScope {
            val relatedSubjectsDeferred = async { fetchRelatedSubjects(items) }
            val strokeOrdersDeferred = async { fetchStrokeOrders(items) }
            relatedSubjectsDeferred.await() to strokeOrdersDeferred.await()
        }
        // Replaces rather than merges: the observation follows one batch at a time, and the cleanup
        // pass (which reaches back across batches) re-points it at the items it asks about.
        pitchAccentItems.value = items
        currentBatchIndex = index
        currentRound = QuizRound.LESSON
        sessionTiming.resume()
        _uiState.update {
            it.copy(
                phase = LessonUiState.Phase.Study(
                    studyItems = items,
                    studyIndex = 0,
                    batchIndex = index,
                    batchCount = batchCount,
                    relatedSubjectsById = relatedSubjects,
                    strokeOrderBySubjectId = strokeOrders
                )
            )
        }
        persistStudySnapshot(0)
    }

    /** One batch lookup for every related subject (radicals/kanji/visually-similar/used-in) across
     *  the whole study set, so the glyphs the detail view shows for these can render on the study
     *  card too without a per-tile network/DB round trip. */
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

    fun onStudyCardSwiped(index: Int) {
        val study = _uiState.value.phase as? LessonUiState.Phase.Study ?: return
        if (index !in study.studyItems.indices) return
        updateStudy { it.copy(studyIndex = index) }
        viewModelScope.launch { persistStudySnapshot(index) }
    }

    fun nextStudyCard() {
        val study = _uiState.value.phase as? LessonUiState.Phase.Study ?: return
        val nextIndex = study.studyIndex + 1
        if (nextIndex >= study.studyItems.size) {
            viewModelScope.launch { beginBatchQuiz(study.studyItems) }
        } else {
            updateStudy { it.copy(studyIndex = nextIndex) }
            viewModelScope.launch { persistStudySnapshot(nextIndex) }
        }
    }

    fun previousStudyCard() {
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
        sessionController.persist(
            PersistedLessonSession(
                phase = PersistedLessonPhase.STUDY,
                sessionAssignmentIds = planAssignmentIds,
                batchSize = batchSize,
                batchIndex = currentBatchIndex,
                studyIndex = index,
                sessionActiveElapsedMs = sessionTiming.currentElapsedMs()
            )
        )
    }

    /** Builds and starts the current batch's quiz. The queue holds only this batch's items, so the
     *  shuffle that follows interleaves a handful of just-studied items rather than every item the
     *  learner selected — which is the whole point of quizzing per batch. */
    private suspend fun beginBatchQuiz(batch: List<LessonItem>) {
        sessionController.begin()
        currentRound = QuizRound.LESSON
        quizQueue.build(batch, typesFor = { item -> questionTypesFor(item.subjectType) })
        totalQuizCount = quizQueue.size

        // getOrPut, not assignment: a resumed session's progress for this batch is already loaded, and
        // the session-wide tallies deliberately survive across batches so the summary at the end still
        // covers the whole session.
        batch.forEach { item -> progressByAssignmentId.getOrPut(item.assignmentId) { LessonItemProgress(item) } }

        // The quiz asks about exactly the batch that was just studied, so the live observation
        // stays pointed at the same items — no update needed when it already is (StateFlow conflates
        // an equal list), and a re-point when a resumed session reaches its quiz directly.
        pitchAccentItems.value = batch
        val next = quizQueue.current
        if (next == null) {
            // Nothing to ask — every item in this batch was already fully learned before quizzing began
            // (only reachable from a resumed snapshot). Move on as if the batch had been quizzed, rather
            // than ever constructing a Quiz phase with no question.
            enterCheckpoint(nextBatchIndex = currentBatchIndex + 1)
            return
        }

        // Not restarted: the session clock spans the whole session, study passes included. Only the
        // per-question clock starts fresh here.
        sessionTiming.resume()
        val questionStartedAt = questionTiming.restart()
        _uiState.update {
            it.copy(
                isCheckingPitchAccent = false,
                pitchAccentCheckFailed = false,
                phase = LessonUiState.Phase.Quiz(
                    currentItem = next.item,
                    currentQuestionType = next.type,
                    round = QuizRound.LESSON,
                    batchIndex = currentBatchIndex,
                    batchCount = batchCount,
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
                    // The hint goes away with the answer. The check flags deliberately don't: a failed
                    // check was a fact about the *word*, which hasn't changed — same treatment
                    // SubjectDetailViewModel gives it across an unrelated uiState change.
                    answerReading = null,
                    answerReadingAudio = null,
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

        // Whether this answer was the very last one due in this pass — either the session is over
        // outright, or what comes next is a batch checkpoint rather than another question. See
        // commitGradeDurably for why the two cases persist differently.
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

        // Reads the field kept warm by the settings collector in init{} instead of
        // `settingsRepository.settings.first()` — see `latestSettings`'s doc comment.
        val settings = latestSettings
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
        // LessonUiState.pitchAccentsBySubjectId, so a scrape from any writer reaches it in place.
        val answerReadingAudio = answerReading?.let { reading ->
            selectAudioFor(item.pronunciationAudios, reading, mp3Only = settings.restrictAudioToMp3)
        }

        updateQuiz {
            it.copy(
                feedback = AnswerFeedback(isCorrect, candidates.joinToString(", "), wasCloseMatch, candidates.size),
                remainingQuizCount = quizQueue.size,
                rankChange = newRankChange ?: it.rankChange,
                // Freezes the "time on this question" display the instant feedback appears, rather
                // than letting it keep ticking while the feedback/Continue screen is up — matches
                // the elapsedMs recorded for the slowest-answers summary above, stamped at this
                // same moment.
                timing = it.timing.copy(questionElapsedMs = questionElapsedMs, questionActiveSegmentStartMs = null),
                answerReading = answerReading,
                answerReadingAudio = answerReadingAudio
            )
        }

        if (type == QuestionType.READING && settings.autoplayPronunciationAudio) {
            candidates.firstOrNull()?.let { reading ->
                pronunciationAudioPlayer.playMatchingReading(item.pronunciationAudios, reading, mp3Only = settings.restrictAudioToMp3)
            }
        }

        commitGradeDurably(isNewlyStarted, item, snapshot, queueIsEmpty)
    }

    /** Captures the current quiz queue as an immutable, ready-to-persist value — safe to hold
     *  across a suspension point even if the live quizQueue is mutated by something else
     *  afterward (see [gradeAnswer]'s deferred [commitGradeDurably] call). */
    private fun currentPersistSnapshot(): PersistedLessonSession = PersistedLessonSession(
        phase = PersistedLessonPhase.QUIZ,
        sessionAssignmentIds = planAssignmentIds,
        batchSize = batchSize,
        batchIndex = currentBatchIndex,
        quizRound = when (currentRound) {
            QuizRound.LESSON -> PersistedQuizRound.LESSON
            QuizRound.CLEANUP -> PersistedQuizRound.CLEANUP
        },
        quizQueue = quizQueue.toList().map { PersistedQuestion(it.item.assignmentId, it.type.name) },
        progress = persistedProgress(),
        totalQuizCount = totalQuizCount,
        sessionActiveElapsedMs = sessionTiming.currentElapsedMs(),
        answeredQuestions = answeredQuestions.map {
            PersistedAnsweredQuestion(it.item.assignmentId, it.type.name, it.isCorrect, it.elapsedMs)
        }
    )

    /** The checkpoint at the end of a pass: every batch up to [currentBatchIndex] is done, so the
     *  snapshot records the *next* batch as where a resume belongs (see
     *  [PersistedLessonSession.batchIndex]). A cleanup pass is the session's last pass, so it points
     *  past the end of the plan — resuming a session parked there goes straight to the summary. */
    private fun checkpointSnapshot(): PersistedLessonSession = PersistedLessonSession(
        phase = PersistedLessonPhase.CHECKPOINT,
        sessionAssignmentIds = planAssignmentIds,
        batchSize = batchSize,
        batchIndex = if (currentRound == QuizRound.CLEANUP) batchCount else currentBatchIndex + 1,
        progress = persistedProgress(),
        totalQuizCount = totalQuizCount,
        sessionActiveElapsedMs = sessionTiming.currentElapsedMs(),
        answeredQuestions = answeredQuestions.map {
            PersistedAnsweredQuestion(it.item.assignmentId, it.type.name, it.isCorrect, it.elapsedMs)
        }
    )

    private fun persistedProgress(): List<PersistedItemProgress> = progressByAssignmentId.map { (id, p) ->
        PersistedItemProgress(id, p.meaningDone, p.readingDone, p.hadIncorrectMeaning, p.hadIncorrectReading)
    }

    /** Persists the in-progress quiz. A Quiz phase whose queue is empty has, by definition, just
     *  finished its pass — its last answer graded, feedback on screen, checkpoint next — so it is
     *  snapshotted as a checkpoint rather than as a quiz with nothing left to answer, which
     *  LessonSessionRepository would rightly treat as a corrupted leftover and clear. Backgrounding on
     *  that exact screen, or navigating away from it, is how a parked session would otherwise lose its
     *  place. */
    private suspend fun persistCurrentState() {
        val snapshot = if (quizQueue.isEmpty) checkpointSnapshot() else currentPersistSnapshot()
        sessionController.persist(snapshot)
    }

    /** True when the pass that just ended was the session's last one and there is nothing left to
     *  offer — i.e. [advanceQuiz] will go straight to the summary. Lets [commitGradeDurably] complete
     *  the session outright instead of saving a checkpoint snapshot that the very next Continue tap
     *  would replace. */
    private fun isSessionOverAfterCurrentPass(queueIsEmpty: Boolean): Boolean {
        if (!queueIsEmpty) return false
        if (currentRound == QuizRound.CLEANUP) return true
        return currentBatchIndex + 1 >= batchCount && sessionMissedItems().isEmpty()
    }

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
            queueIsEmpty -> sessionController.persist(checkpointSnapshot(), alongside = outboxWork)
            else -> sessionController.persist(snapshot, alongside = outboxWork)
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

    /** Fetches [item]'s pitch accent now, for a word the background scrape hasn't reached — the same
     *  on-demand check the subject detail sheet offers, mirrored onto the quiz hint.
     *
     *  The hint already observes the cache (see the collector in init), so this only has to cause the
     *  write: nothing is copied back into [LessonUiState.pitchAccentsBySubjectId] here, the new value
     *  arrives through the flow. A fetch takes real network time, so the tap is acknowledged
     *  immediately by flipping [LessonUiState.isCheckingPitchAccent] (the caption becomes a progress
     *  row) and a fetch that fails — a word weblio doesn't answer for, a network error — is reported
     *  through [LessonUiState.pitchAccentCheckFailed]: the word stays "not checked yet" either way, so
     *  without that flag a failed check would look exactly like no check at all. A second tap while
     *  one is already running is ignored.
     *
     *  No-ops for an item with no [LessonItem.characters] — there is nothing to look up, and an empty
     *  query would only cache a meaningless row. */
    fun checkPitchAccent(item: LessonItem) {
        val characters = item.characters ?: return
        if (_uiState.value.isCheckingPitchAccent) return
        _uiState.update { it.copy(isCheckingPitchAccent = true, pitchAccentCheckFailed = false) }
        viewModelScope.launch {
            val scraped = pitchAccentRepository.scrapeAndCache(characters, Clock.System.now().toEpochMilliseconds())
            // Only publish for the subject that started the check: advancing mid-fetch resets the
            // flags, and this completion must not raise them against whatever question is current by
            // then.
            _uiState.update {
                val quiz = it.phase as? LessonUiState.Phase.Quiz
                if (quiz?.currentItem?.subjectId != item.subjectId) {
                    it
                } else {
                    it.copy(isCheckingPitchAccent = false, pitchAccentCheckFailed = !scraped)
                }
            }
        }
    }

    /** Walks from a just-finished batch into the next one — the checkpoint's primary action. */
    fun continueSession() {
        val checkpoint = _uiState.value.phase as? LessonUiState.Phase.BatchComplete ?: return
        val next = checkpoint.next as? LessonUiState.Phase.BatchComplete.NextStep.StudyBatch ?: return
        viewModelScope.launch { enterStudyPhase(next.batchIndex) }
    }

    /** "Finish for now": keeps the session where it is instead of abandoning it. Nothing needs writing
     *  here — [enterCheckpoint] already persisted the checkpoint — so this only asks the screen to
     *  navigate back, where the dashboard will offer to resume at the next batch. Refuses on the final
     *  checkpoint, where the exits are the cleanup pass or the summary, not a park. */
    fun finishForNow() {
        val checkpoint = _uiState.value.phase as? LessonUiState.Phase.BatchComplete ?: return
        if (checkpoint.next !is LessonUiState.Phase.BatchComplete.NextStep.StudyBatch) return
        _uiState.update { it.copy(exit = LessonUiState.ExitRequest.Parked) }
    }

    /** Declines the final checkpoint's extra practice and shows the session summary. */
    fun finishSessionNow() {
        val checkpoint = _uiState.value.phase as? LessonUiState.Phase.BatchComplete ?: return
        if (checkpoint.next !is LessonUiState.Phase.BatchComplete.NextStep.PracticeMissed) return
        viewModelScope.launch { finishSession() }
    }

    /** Accepts the final checkpoint's extra practice: one more pass over the items missed during this
     *  session, asking only the halves that were missed.
     *
     *  Summary-neutral by construction — every question in this pass belongs to an item whose lesson
     *  was already committed, so answering it can neither start a lesson twice (startedAssignmentIds)
     *  nor clear the miss flags that made the item count as missed in the first place. The pass exists
     *  purely to give the session's weakest items one more retrieval. */
    fun practiceMissedItems() {
        val checkpoint = _uiState.value.phase as? LessonUiState.Phase.BatchComplete ?: return
        if (checkpoint.next !is LessonUiState.Phase.BatchComplete.NextStep.PracticeMissed) return
        viewModelScope.launch {
            val missedItems = sessionMissedItems()
            if (missedItems.isEmpty()) {
                finishSession()
                return@launch
            }
            currentRound = QuizRound.CLEANUP
            quizQueue.build(
                missedItems,
                typesFor = { item -> progressByAssignmentId[item.assignmentId]?.missedQuestionTypes().orEmpty() }
            )
            totalQuizCount = quizQueue.size

            val next = quizQueue.current
            if (next == null) {
                finishSession()
                return@launch
            }

            // This pass reaches back across every batch, so re-point the live observation at exactly
            // the items it asks about rather than whatever batch happened to be in play last.
            pitchAccentItems.value = missedItems
            sessionTiming.resume()
            val questionStartedAt = questionTiming.restart()
            _uiState.update {
                it.copy(
                    isCheckingPitchAccent = false,
                    pitchAccentCheckFailed = false,
                    phase = LessonUiState.Phase.Quiz(
                        currentItem = next.item,
                        currentQuestionType = next.type,
                        round = QuizRound.CLEANUP,
                        batchIndex = currentBatchIndex,
                        batchCount = batchCount,
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
    }

    /** Discards a persisted in-progress lesson session (study, quiz, or a parked checkpoint) and
     *  exits — a clean slate next time. Mirrors ReviewViewModel.abandonSession. */
    fun abandonSession() {
        viewModelScope.launch {
            sessionController.abandon()
            clearSessionState()
            _uiState.update { it.copy(exit = LessonUiState.ExitRequest.Abandoned) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sessionTiming.pause()
        pronunciationAudioPlayer.stop()
    }

    /** Items learned, how many were correct without ever missing, which were missed at least once,
     *  and timing — mirrors ReviewViewModel.sessionSummary(). "Missed" here means at least one wrong
     *  attempt during the quiz, not a real SRS miss — every lesson item is requeued until correct.
     *  Spans the whole session: every batch, plus the cleanup pass. */
    private fun sessionSummary(): QuizSessionSummary<LessonItem> =
        summarizeQuizSession(progressByAssignmentId.values, answeredQuestions, sessionTiming.currentElapsedMs())

    /** Every item missed at least once anywhere in this session — what the final checkpoint offers
     *  extra practice on, and what tells [isSessionOverAfterCurrentPass] whether there's an offer to
     *  make at all. */
    private fun sessionMissedItems(): List<LessonItem> =
        progressByAssignmentId.values
            .filter { it.hadIncorrectMeaning || it.hadIncorrectReading }
            .map { it.item }

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

    /** Ends the session and shows its summary — terminal: the persisted snapshot is cleared before the
     *  summary renders, so nothing can save a session the learner has already finished. */
    private suspend fun finishSession() {
        sessionTiming.freeze()
        sessionController.complete()
        outboxRepository.requestSyncNow()
        val summary = sessionSummary()
        persistLastSessionSummary(summary)
        // The summary has no pitch accents to show, so the live observation and the check state that
        // belonged to the pass just finished are done with.
        pitchAccentItems.value = emptyList()
        _uiState.update {
            it.copy(
                phase = summary.toCompletePhase(),
                pitchAccentsBySubjectId = emptyMap(),
                isCheckingPitchAccent = false,
                pitchAccentCheckFailed = false
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
        val completedProgress = sessionBatches.getOrNull(completedBatchIndex).orEmpty()
            .mapNotNull { progressByAssignmentId[it] }
        val missedInBatch = completedProgress
            .filter { it.hadIncorrectMeaning || it.hadIncorrectReading }
            .map { it.item }
        val studyNextBatch = nextBatchIndex < batchCount
        val sessionMisses = sessionMissedItems()

        if (!studyNextBatch && sessionMisses.isEmpty()) {
            finishSession()
            return
        }

        val next = if (studyNextBatch) {
            LessonUiState.Phase.BatchComplete.NextStep.StudyBatch(
                batchIndex = nextBatchIndex,
                remainingSessionItems = sessionBatches.drop(nextBatchIndex).sumOf { it.size }
            )
        } else {
            LessonUiState.Phase.BatchComplete.NextStep.PracticeMissed(itemCount = sessionMisses.size)
        }

        _uiState.update {
            it.copy(
                // Nothing pitch-related is on screen at a checkpoint any more, so drop the
                // observation's items and the check state that belonged to the pass just finished.
                pitchAccentsBySubjectId = emptyMap(),
                isCheckingPitchAccent = false,
                pitchAccentCheckFailed = false,
                phase = LessonUiState.Phase.BatchComplete(
                    batchIndex = completedBatchIndex,
                    batchCount = batchCount,
                    itemsLearned = completedProgress.size,
                    itemsCorrectFirstTry = completedProgress.count { p -> !p.hadIncorrectMeaning && !p.hadIncorrectReading },
                    missedItems = missedInBatch,
                    next = next
                )
            )
        }
        pitchAccentItems.value = emptyList()
        sessionController.persist(checkpointSnapshot())
    }

    private suspend fun advanceQuiz() {
        val next = quizQueue.current
        if (next != null) {
            val questionStartedAt = questionTiming.restart()
            // The new question owns neither the previous one's hint nor its check's
            // progress/outcome — a failure on one word must not caption the next one's "not checked
            // yet". Its completion is dropped later by checkPitchAccent's own subject-id guard.
            updateQuiz {
                it.copy(
                    currentItem = next.item,
                    currentQuestionType = next.type,
                    answerInput = "",
                    feedback = null,
                    rankChange = null,
                    answerReading = null,
                    answerReadingAudio = null,
                    isDetailsExpanded = false,
                    remainingQuizCount = quizQueue.size,
                    timing = it.timing.copy(
                        questionActiveElapsedMs = 0L,
                        questionActiveSegmentStartMs = questionStartedAt,
                        questionElapsedMs = null
                    )
                )
            }
            _uiState.update { it.copy(isCheckingPitchAccent = false, pitchAccentCheckFailed = false) }
            return
        }

        // The pass is over, and its durability write already happened at grading time (see
        // commitGradeDurably), so this only has to decide where the learner goes next: the next batch's
        // checkpoint, or — after the cleanup pass — the summary.
        if (currentRound == QuizRound.CLEANUP) {
            finishSession()
            return
        }
        enterCheckpoint(nextBatchIndex = currentBatchIndex + 1)
    }
}
