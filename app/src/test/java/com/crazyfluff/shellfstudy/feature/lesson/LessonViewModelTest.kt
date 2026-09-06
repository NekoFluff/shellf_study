package com.crazyfluff.shellfstudy.feature.lesson

import com.crazyfluff.shellfstudy.shared.data.PersistedLessonPhase
import com.crazyfluff.shellfstudy.shared.data.PersistedLessonSession
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonUiState
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonViewModel
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.LastSessionKind
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import com.crazyfluff.shellfstudy.shared.data.LessonSessionRepository
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.PitchAccentRepository
import com.crazyfluff.shellfstudy.shared.data.PlaybackState
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.SubjectRepository
import com.crazyfluff.shellfstudy.shared.session.LessonSessionController
import com.crazyfluff.shellfstudy.shared.data.model.RankChange
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.data.model.StrokeOrderStroke
import com.crazyfluff.shellfstudy.shared.data.StrokeOrderRepository
import com.crazyfluff.shellfstudy.shared.designsystem.strokeorder.StrokeOrderUiState
import com.crazyfluff.shellfstudy.shared.lifecycle.AppForegroundTracker
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType
import com.crazyfluff.shellfstudy.fakes.FakeLifecycleOwner
import com.crazyfluff.shellfstudy.fakes.FakePronunciationAudioPlayer
import com.crazyfluff.shellfstudy.fakes.FakeStrokeOrderRepository
import com.crazyfluff.shellfstudy.fakes.TestRepositories
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.fakes.jsonResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LessonViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var repositories: TestRepositories
    private lateinit var assignmentRepository: AssignmentRepository
    private lateinit var outboxRepository: OutboxRepository
    private lateinit var lessonSessionRepository: LessonSessionRepository
    private lateinit var lastSessionSummaryRepository: LastSessionSummaryRepository
    private lateinit var pitchAccentRepository: PitchAccentRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var subjectRepository: SubjectRepository
    private var strokeOrderRepository: StrokeOrderRepository = FakeStrokeOrderRepository()
    private lateinit var pronunciationAudioPlayer: FakePronunciationAudioPlayer
    private lateinit var appForegroundTracker: AppForegroundTracker

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(mainDispatcherRule.dispatcher + SupervisorJob()),
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        settingsRepository = SettingsRepository(dataStore)
        repositories = buildTestRepositories(server.url("/").toString(), defaultDispatcher = mainDispatcherRule.dispatcher)
        assignmentRepository = repositories.assignmentRepository
        pitchAccentRepository = repositories.pitchAccentRepository
        subjectRepository = repositories.subjectRepository
        strokeOrderRepository = FakeStrokeOrderRepository()
        outboxRepository = OutboxRepository(repositories.outboxDao, repositories.outboxSyncScheduler, dataStore)
        lessonSessionRepository = LessonSessionRepository(dataStore, Json { ignoreUnknownKeys = true })
        lastSessionSummaryRepository = LastSessionSummaryRepository(dataStore, Json { ignoreUnknownKeys = true })
        pronunciationAudioPlayer = FakePronunciationAudioPlayer()
        appForegroundTracker = AppForegroundTracker()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun TestScope.createViewModel() = LessonViewModel(
        assignmentRepository, repositories.statsRepository, outboxRepository,
        LessonSessionController(backgroundScope, lessonSessionRepository),
        lastSessionSummaryRepository, pitchAccentRepository, settingsRepository, subjectRepository, strokeOrderRepository,
        pronunciationAudioPlayer, appForegroundTracker, backgroundScope
    )

    /** Routes by path — refreshing the lesson queue now syncs subjects and assignments, in either order. */
    private fun dispatch(
        assignmentsResponse: MockResponse,
        subjectsResponse: MockResponse,
        startResponse: MockResponse? = null
    ) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("/start") -> startResponse ?: startAssignmentResultJson().let(::jsonResponse)
                    path.startsWith("/assignments") -> assignmentsResponse
                    path.startsWith("/subjects") -> subjectsResponse
                    else -> jsonResponse(emptyCollectionJson())
                }
            }
        }
    }

    @Test
    fun `loads a batch of lessons into the select phase with all pre-selected`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            val select = state.phase as LessonUiState.Phase.Select
            assertThat(select.availableLessons).hasSize(1)
            assertThat(select.selectedAssignmentIds).containsExactly(101L)
        }
    }

    @Test
    fun `showSubjectTypeLabel setting flows into uiState`() = runTest(mainDispatcherRule.dispatcher) {
        settingsRepository.setShowSubjectTypeLabel(true)
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading || !state.settings.showSubjectTypeLabel) state = awaitItem()
            assertThat(state.settings.showSubjectTypeLabel).isTrue()
        }
    }

    @Test
    fun `showTotalTimer and showQuestionTimer settings flow into uiState`() = runTest(mainDispatcherRule.dispatcher) {
        settingsRepository.setShowTotalTimer(true)
        settingsRepository.setShowQuestionTimer(true)
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading || !state.settings.showTotalTimer || !state.settings.showQuestionTimer) state = awaitItem()
            assertThat(state.settings.showTotalTimer).isTrue()
            assertThat(state.settings.showQuestionTimer).isTrue()
        }
    }

    @Test
    fun `starting the quiz sets sessionActiveSegmentStartMs and questionActiveSegmentStartMs`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()
            viewModel.nextStudyCard()
            val quizState = awaitItem()

            val quiz = quizState.phase as LessonUiState.Phase.Quiz
            assertThat(quiz.timing.sessionActiveSegmentStartMs).isNotNull()
            assertThat(quiz.timing.questionActiveSegmentStartMs).isNotNull()
        }
    }

    @Test
    fun `an empty lesson queue is reported as no lessons available`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(emptyCollectionJson()), jsonResponse(emptyCollectionJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()
            assertThat(state.phase).isEqualTo(LessonUiState.Phase.NoLessonsAvailable)
        }
    }

    @Test
    fun `toggling a lesson selection adds or removes it`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(twoRadicalAssignmentsJson()), jsonResponse(twoRadicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()
            assertThat((state.phase as LessonUiState.Phase.Select).selectedAssignmentIds).containsExactly(101L, 102L)

            viewModel.toggleLessonSelection(101L)
            assertThat((awaitItem().phase as LessonUiState.Phase.Select).selectedAssignmentIds).containsExactly(102L)

            viewModel.toggleLessonSelection(101L)
            assertThat((awaitItem().phase as LessonUiState.Phase.Select).selectedAssignmentIds).containsExactly(101L, 102L)
        }
    }

    @Test
    fun `selectNone and selectAll clear and restore the full selection`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(twoRadicalAssignmentsJson()), jsonResponse(twoRadicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.selectNone()
            assertThat((awaitItem().phase as LessonUiState.Phase.Select).selectedAssignmentIds).isEmpty()

            viewModel.selectAll()
            assertThat((awaitItem().phase as LessonUiState.Phase.Select).selectedAssignmentIds).containsExactly(101L, 102L)
        }
    }

    @Test
    fun `startSelectedLessons enters the study phase with only the selected items`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(twoRadicalAssignmentsJson()), jsonResponse(twoRadicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.toggleLessonSelection(102L)
            awaitItem()

            viewModel.startSelectedLessons()
            val study = awaitItem().phase as LessonUiState.Phase.Study
            assertThat(study.studyItems).hasSize(1)
            assertThat(study.studyItems.first().assignmentId).isEqualTo(101L)
        }
    }

    @Test
    fun `startSelectedLessons loads stroke order per subject, keyed by subject id`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(twoRadicalAssignmentsJson()), jsonResponse(twoRadicalSubjectsJson()))
        strokeOrderRepository = FakeStrokeOrderRepository(
            mapOf('口' to listOf(StrokeOrderStroke(pathData = "M10,10L90,10", labelX = 5f, labelY = 5f)))
        )

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            val study = awaitItem().phase as LessonUiState.Phase.Study

            assertThat(study.strokeOrderBySubjectId[1L]).isInstanceOf(StrokeOrderUiState.Available::class.java)
            assertThat(study.strokeOrderBySubjectId[2L]).isEqualTo(StrokeOrderUiState.Unavailable)
        }
    }

    @Test
    fun `advancing past the last study card starts the quiz`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            val study = awaitItem().phase as LessonUiState.Phase.Study
            assertThat(study.studyIndex).isEqualTo(0)

            viewModel.nextStudyCard()
            val quiz = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(quiz.currentQuestionType).isEqualTo(QuestionType.MEANING)
            assertThat(quiz.totalQuizCount).isEqualTo(1)
        }
    }

    @Test
    fun `previousStudyCard moves back a card but not before the first`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(twoRadicalAssignmentsJson()), jsonResponse(twoRadicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()

            viewModel.nextStudyCard()
            val secondCard = awaitItem().phase as LessonUiState.Phase.Study
            assertThat(secondCard.studyIndex).isEqualTo(1)

            viewModel.previousStudyCard()
            val backToFirst = awaitItem().phase as LessonUiState.Phase.Study
            assertThat(backToFirst.studyIndex).isEqualTo(0)

            viewModel.previousStudyCard()
            expectNoEvents()
        }
    }

    @Test
    fun `onStudyCardSwiped updates the study index directly`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(twoRadicalAssignmentsJson()), jsonResponse(twoRadicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()

            viewModel.onStudyCardSwiped(1)
            assertThat((awaitItem().phase as LessonUiState.Phase.Study).studyIndex).isEqualTo(1)
        }
    }

    @Test
    fun `a correct quiz answer marks the assignment started once all its questions are done`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()

            viewModel.nextStudyCard()
            awaitItem() // quiz begins

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(feedbackState.feedback?.isCorrect).isTrue()

            viewModel.onContinue()
            val finalState = awaitItem()
            assertThat(finalState.phase).isInstanceOf(LessonUiState.Phase.Complete::class.java)
        }

        // Local-write-first: no network call happens from the ViewModel path at all — the lesson
        // start is durably queued for the background sync worker instead.
        val queued = repositories.outboxDao.allLessonStarts()
        assertThat(queued).hasSize(1)
        assertThat(queued.first().assignmentId).isEqualTo(101L)
        assertThat(repositories.outboxSyncScheduler.requestCount).isEqualTo(1)
        // Session completion should flush the outbox immediately rather than waiting out the
        // per-answer debounce, so the dashboard's pending-sync count doesn't look stale.
        assertThat(repositories.outboxSyncScheduler.immediateRequestCount).isEqualTo(1)

        // Completing a session snapshots its summary so it can be revisited later from the dashboard.
        val savedSummary = lastSessionSummaryRepository.loadLesson()
        assertThat(savedSummary).isNotNull()
        assertThat(savedSummary!!.kind).isEqualTo(LastSessionKind.LESSON)
        assertThat(savedSummary.itemsCount).isEqualTo(1)
    }

    @Test
    fun `a newly-started item surfaces a rank change once and clears on continue`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()
            // No rank change can be showing yet outside the Quiz phase — Phase.Select structurally
            // has no rankChange field at all.

            viewModel.startSelectedLessons()
            awaitItem()

            viewModel.nextStudyCard()
            awaitItem() // quiz begins

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(feedbackState.feedback?.isCorrect).isTrue()
            // radicalAssignmentsJson fixes the cached assignment at srs_stage 0 (Locked) — every
            // lesson item starts the same way, straight to the SRS system's starting stage.
            assertThat(feedbackState.rankChange).isEqualTo(RankChange(SrsStage.LOCKED, SrsStage.APPRENTICE_1))

            viewModel.onContinue()
            val finalState = awaitItem()
            // Complete no longer carries a rankChange field at all — leaving the Quiz phase behind
            // is itself the "cleared" state.
            assertThat(finalState.phase).isInstanceOf(LessonUiState.Phase.Complete::class.java)
        }
    }

    @Test
    fun `undo reverts an incorrect answer so it doesn't count as a miss`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()

            viewModel.nextStudyCard()
            awaitItem() // quiz begins

            viewModel.onAnswerInputChange("wrong")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(feedbackState.feedback?.isCorrect).isFalse()

            viewModel.undoLastAnswer()
            val undoneState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(undoneState.feedback).isNull()
            assertThat(undoneState.answerInput).isEqualTo("")

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val retriedState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(retriedState.feedback?.isCorrect).isTrue()

            viewModel.onContinue()
            val finalState = awaitItem()
            assertThat(finalState.phase).isInstanceOf(LessonUiState.Phase.Complete::class.java)
        }

        // The undone wrong answer must not count toward the session's missed-item tally — the
        // only item in this session should show as correct-on-first-try.
        val savedSummary = lastSessionSummaryRepository.loadLesson()
        assertThat(savedSummary?.itemsCount).isEqualTo(1)
        assertThat(savedSummary?.correctFirstTry).isEqualTo(1)
    }

    @Test
    fun `clearing the ViewModel immediately after grading does not lose the durable write`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression test for durability writes (outbox enqueue, SRS patch, session snapshot)
        // being parented to an application-scoped CoroutineScope instead of viewModelScope: a rushed
        // back-press clears the ViewModel (cancelling viewModelScope) the instant feedback is shown,
        // and that must not be able to cancel the write. viewModelScope.cancel() here simulates
        // exactly what ViewModel.clear() does to viewModelScope when the screen is left.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()

            viewModel.nextStudyCard()
            awaitItem() // quiz begins

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(feedbackState.feedback?.isCorrect).isTrue()

            viewModel.viewModelScope.cancel()
        }

        val queued = repositories.outboxDao.allLessonStarts()
        assertThat(queued).hasSize(1)
        assertThat(queued.first().assignmentId).isEqualTo(101L)
    }

    @Test
    fun `an incorrect quiz answer requeues the question instead of starting the assignment`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()

            viewModel.nextStudyCard()
            awaitItem() // quiz begins

            viewModel.onAnswerInputChange("wrong")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(feedbackState.feedback?.isCorrect).isFalse()
            assertThat(feedbackState.remainingQuizCount).isEqualTo(1)

            viewModel.onContinue()
            val requeuedState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(requeuedState.currentQuestionType).isEqualTo(QuestionType.MEANING)
        }
    }

    @Test
    fun `submitting an answer freezes questionElapsedMs, and advancing to the next question resets it`() = runTest(mainDispatcherRule.dispatcher) {
        // Two single-question (radical) items, so answering the first correctly advances to a
        // genuine next question rather than completing the session — the reset only happens on
        // that "next question" path, not the session-complete one.
        dispatch(jsonResponse(twoRadicalAssignmentsJson()), jsonResponse(twoRadicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()
            viewModel.nextStudyCard()
            awaitItem() // studyIndex 1
            viewModel.nextStudyCard()
            val quizState = awaitItem().phase as LessonUiState.Phase.Quiz // quiz begins
            assertThat(quizState.timing.questionElapsedMs).isNull()

            val item = quizState.currentItem
            viewModel.onAnswerInputChange(item.meanings.first())
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(feedbackState.timing.questionElapsedMs).isNotNull()

            viewModel.onContinue()
            val nextState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(nextState.timing.questionElapsedMs).isNull()
        }
    }

    @Test
    fun `dontKnowAnswer grades as incorrect and requeues`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()

            viewModel.nextStudyCard()
            awaitItem() // quiz begins

            viewModel.dontKnowAnswer()
            val feedbackState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(feedbackState.feedback?.isCorrect).isFalse()
            assertThat(feedbackState.remainingQuizCount).isEqualTo(1)
        }
    }

    @Test
    fun `a new ViewModel resumes a persisted study session on the same card instead of restarting selection`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(twoRadicalAssignmentsJson()), jsonResponse(twoRadicalSubjectsJson()))

        val firstViewModel = createViewModel()
        firstViewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            firstViewModel.startSelectedLessons()
            awaitItem()

            firstViewModel.nextStudyCard()
            val secondCard = awaitItem().phase as LessonUiState.Phase.Study
            assertThat(secondCard.studyIndex).isEqualTo(1)
        }
        val requestCountAfterFirstLoad = server.requestCount

        // Simulate leaving and coming back mid-study, before the quiz ever begins: a fresh
        // ViewModel sharing the same repositories should land back on the same card in the same
        // batch, rather than forcing lesson re-selection and restudying from card one.
        val secondViewModel = createViewModel()
        secondViewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()
            val study = state.phase as LessonUiState.Phase.Study
            assertThat(study.studyIndex).isEqualTo(1)
            assertThat(study.studyItems.map { it.assignmentId }).containsExactly(101L, 102L).inOrder()
        }
        assertThat(server.requestCount).isEqualTo(requestCountAfterFirstLoad)
    }

    @Test
    fun `a new ViewModel resumes a persisted quiz session instead of refetching from the network`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val firstViewModel = createViewModel()
        firstViewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            firstViewModel.startSelectedLessons()
            awaitItem()

            firstViewModel.nextStudyCard()
            val quizState = awaitItem()
            assertThat(quizState.phase).isInstanceOf(LessonUiState.Phase.Quiz::class.java)
        }
        val requestCountAfterFirstLoad = server.requestCount

        // Simulate leaving and coming back: a fresh ViewModel sharing the same repositories should
        // pick the in-progress quiz back up rather than hitting the network again.
        val secondViewModel = createViewModel()
        secondViewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()
            val quiz = state.phase as LessonUiState.Phase.Quiz
            assertThat(quiz.totalQuizCount).isEqualTo(1)
            assertThat(quiz.currentItem.assignmentId).isEqualTo(101L)
        }
        assertThat(server.requestCount).isEqualTo(requestCountAfterFirstLoad)
    }

    @Test
    fun `resuming falls back to a fresh fetch when a queued item can no longer be found`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val firstViewModel = createViewModel()
        firstViewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            firstViewModel.startSelectedLessons()
            awaitItem()

            firstViewModel.nextStudyCard()
            awaitItem() // quiz begins, persisted
        }

        // Simulate the assignment's row genuinely vanishing from local cache (e.g. app storage was
        // cleared) between sessions — resumeQuizPhase resolves persisted entries by id regardless
        // of due status, so only an actually-missing row (not merely "no longer due") can't be
        // resolved on resume, and must fall back to a fresh fetch instead of crashing.
        repositories.assignmentDao.clearAll()

        val secondViewModel = createViewModel()
        secondViewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()
            assertThat(state.phase).isEqualTo(LessonUiState.Phase.NoLessonsAvailable)
        }
        assertThat(lessonSessionRepository.load()).isNull()
    }

    @Test
    fun `completing the quiz clears the persisted lesson session`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()

            viewModel.nextStudyCard()
            awaitItem() // quiz begins
            assertThat(lessonSessionRepository.load()).isNotNull()

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            awaitItem()

            viewModel.onContinue()
            val finalState = awaitItem()
            assertThat(finalState.phase).isInstanceOf(LessonUiState.Phase.Complete::class.java)
        }

        assertThat(lessonSessionRepository.load()).isNull()
    }

    @Test
    fun `abandonSession clears persisted state and marks the session abandoned`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()

            viewModel.abandonSession()
            var abandonedState = awaitItem()
            while (!abandonedState.isAbandoned) abandonedState = awaitItem()
            assertThat(abandonedState.isAbandoned).isTrue()
        }

        assertThat(lessonSessionRepository.load()).isNull()
    }

    @Test
    fun `session summary reports items learned, correct-first-try, missed items, and timing`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()
            viewModel.nextStudyCard()
            awaitItem() // quiz begins

            // Miss the only question first, then answer it correctly — a "correct on first try"
            // count of zero and one missed item is the expected result.
            viewModel.onAnswerInputChange("wrong")
            awaitItem()
            viewModel.submitAnswer()
            val missedState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(missedState.feedback?.isCorrect).isFalse()

            viewModel.onContinue()
            awaitItem() // requeued question shown again

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            awaitItem()

            viewModel.onContinue()
            val finalState = awaitItem().phase as LessonUiState.Phase.Complete

            assertThat(finalState.sessionItemsLearned).isEqualTo(1)
            assertThat(finalState.sessionItemsCorrectFirstTry).isEqualTo(0)
            assertThat(finalState.sessionMissedItems).hasSize(1)
            assertThat(finalState.sessionMissedItems.first().characters).isEqualTo("口")
            assertThat(finalState.sessionSlowestAnswers).isNotEmpty()
            assertThat(finalState.sessionSlowestAnswers.size).isAtMost(5)
            assertThat(finalState.sessionTotalElapsedMs).isAtLeast(0L)
            assertThat(finalState.sessionAverageTimePerItemMs).isAtLeast(0L)
        }
    }

    @Test
    fun `session summary reports full correct-first-try count when nothing was missed`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(twoRadicalAssignmentsJson()), jsonResponse(twoRadicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()
            viewModel.nextStudyCard()
            awaitItem() // studyIndex 1
            viewModel.nextStudyCard()
            state = awaitItem() // quiz begins

            var isComplete = false
            var safetyCounter = 0
            while (!isComplete && safetyCounter < 10) {
                safetyCounter++
                val item = (state.phase as LessonUiState.Phase.Quiz).currentItem
                viewModel.onAnswerInputChange(item.meanings.first())
                awaitItem()
                viewModel.submitAnswer()
                awaitItem()
                viewModel.onContinue()
                state = awaitItem()
                isComplete = state.phase is LessonUiState.Phase.Complete
            }

            val complete = state.phase as LessonUiState.Phase.Complete
            assertThat(complete.sessionItemsLearned).isEqualTo(2)
            assertThat(complete.sessionItemsCorrectFirstTry).isEqualTo(2)
            assertThat(complete.sessionMissedItems).isEmpty()
        }
    }

    @Test
    fun `resuming a persisted session preserves progress for the eventual session summary`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(twoRadicalAssignmentsJson()), jsonResponse(twoRadicalSubjectsJson()))

        val firstViewModel = createViewModel()
        firstViewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            firstViewModel.startSelectedLessons()
            awaitItem()
            firstViewModel.nextStudyCard()
            awaitItem()
            firstViewModel.nextStudyCard()
            awaitItem() // quiz begins

            // Miss the first-drawn question once, then move on — its incorrect-attempt flag should
            // survive into the persisted snapshot even though the item isn't done yet.
            firstViewModel.onAnswerInputChange("wrong")
            awaitItem()
            firstViewModel.submitAnswer()
            awaitItem()
            firstViewModel.onContinue()
            awaitItem()
        }

        // Simulate leaving and coming back mid-quiz: a fresh ViewModel sharing the same repositories
        // must resume with the missed-once item still counted as missed in the session summary,
        // not silently forget it happened.
        val secondViewModel = createViewModel()
        secondViewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            var isComplete = false
            var safetyCounter = 0
            while (!isComplete && safetyCounter < 10) {
                safetyCounter++
                val item = (state.phase as LessonUiState.Phase.Quiz).currentItem
                secondViewModel.onAnswerInputChange(item.meanings.first())
                awaitItem()
                secondViewModel.submitAnswer()
                awaitItem()
                secondViewModel.onContinue()
                state = awaitItem()
                isComplete = state.phase is LessonUiState.Phase.Complete
            }

            val complete = state.phase as LessonUiState.Phase.Complete
            assertThat(complete.sessionItemsLearned).isEqualTo(2)
            assertThat(complete.sessionMissedItems).hasSize(1)
        }
    }

    @Test
    fun `resuming after fully completing one lesson item preserves it in the eventual session summary`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression test: finishing assignment 101 here calls applyOptimisticLessonStart, which
        // sets its startedAt and drops it out of observeDueForLesson() even though its (completed)
        // progress is still persisted. resumeQuizPhase must still resolve it on resume so it
        // contributes to the final session summary, instead of silently disappearing from the tally.
        dispatch(jsonResponse(twoRadicalAssignmentsJson()), jsonResponse(twoRadicalSubjectsJson()))

        val firstViewModel = createViewModel()
        firstViewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            firstViewModel.startSelectedLessons()
            awaitItem()
            firstViewModel.nextStudyCard()
            awaitItem()
            firstViewModel.nextStudyCard()
            state = awaitItem() // quiz begins

            var oneCompleted = false
            var safetyCounter = 0
            while (!oneCompleted && safetyCounter < 10) {
                safetyCounter++
                val item = (state.phase as LessonUiState.Phase.Quiz).currentItem
                if (item.assignmentId == 101L) {
                    firstViewModel.onAnswerInputChange("Mouth")
                    awaitItem()
                    firstViewModel.submitAnswer()
                    awaitItem()
                    oneCompleted = true
                } else {
                    // Keep the other item in-progress (never finishing it) so the session stays
                    // meaningfully incomplete going into the pause.
                    firstViewModel.onAnswerInputChange("wrong")
                    awaitItem()
                    firstViewModel.submitAnswer()
                    awaitItem()
                }
                firstViewModel.onContinue()
                state = awaitItem()
            }

            assertThat(oneCompleted).isTrue()
            assertThat(state.phase).isNotInstanceOf(LessonUiState.Phase.Complete::class.java)
        }

        // Simulate leaving and coming back: assignment 101 is no longer due for a lesson (it's
        // started), but its completed progress must still be resolved and counted on resume.
        val secondViewModel = createViewModel()
        secondViewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()
            assertThat(state.phase).isNotInstanceOf(LessonUiState.Phase.Complete::class.java)

            var isComplete = false
            var safetyCounter = 0
            while (!isComplete && safetyCounter < 10) {
                safetyCounter++
                val item = (state.phase as LessonUiState.Phase.Quiz).currentItem
                secondViewModel.onAnswerInputChange(item.meanings.first())
                awaitItem()
                secondViewModel.submitAnswer()
                awaitItem()
                secondViewModel.onContinue()
                state = awaitItem()
                isComplete = state.phase is LessonUiState.Phase.Complete
            }

            val complete = state.phase as LessonUiState.Phase.Complete
            // The real assertion: both items count toward the final tally, not just the one
            // answered after resume — a dropped progress entry for item 101 would report 1 here.
            assertThat(complete.sessionItemsLearned).isEqualTo(2)
            // Item 101's answer, graded before the pause, must still show up in the "slowest
            // answers" summary — answeredQuestions is restored from the persisted session just like
            // progressByAssignmentId, not reset to only the post-resume segment.
            assertThat(complete.sessionSlowestAnswers.map { it.item.assignmentId }).contains(101L)
        }
    }

    @Test
    fun `resuming a persisted quiz preserves the accumulated active time instead of resetting it to zero`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression test: resumeFromPersisted used to derive elapsed time from an absolute session
        // start timestamp restored across resumes, which counted 100% of time spent away
        // (backgrounded, or navigated off and back) as if it were active quiz time. It should
        // instead carry over only the accumulated *active* time — proven with a fake, unmistakably
        // large value rather than comparing real wall-clock reads, since this whole test executes
        // in well under a second.
        // Uses the two-question kanji fixture (rather than the single-question radical one) so
        // answering one question below doesn't complete the whole quiz — that path clears the
        // session snapshot outright instead of saving one (see gradeAnswer's queueIsEmpty), which
        // would leave nothing for this test to inspect.
        dispatch(jsonResponse(kanjiAssignmentsJson()), jsonResponse(kanjiSubjectsJson()))

        val firstViewModel = createViewModel()
        firstViewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            firstViewModel.startSelectedLessons()
            awaitItem()
            firstViewModel.nextStudyCard()
            awaitItem() // quiz begins, persisted
        }

        val fakeAccumulatedElapsedMs = 1_000_000L
        val persisted = lessonSessionRepository.load()!!
        lessonSessionRepository.save(persisted.copy(sessionActiveElapsedMs = fakeAccumulatedElapsedMs))

        val secondViewModel = createViewModel()
        secondViewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()
            val quiz = state.phase as LessonUiState.Phase.Quiz
            assertThat(quiz.timing.sessionActiveElapsedMs).isEqualTo(fakeAccumulatedElapsedMs)
            assertThat(quiz.timing.sessionActiveSegmentStartMs).isNotNull()

            // Forces a fresh persisted snapshot so the resumed accumulated time can be inspected —
            // answering just one of the kanji's two questions leaves the quiz still in progress.
            val answer = if (quiz.currentQuestionType == QuestionType.MEANING) "Water" else "mizu"
            secondViewModel.onAnswerInputChange(answer)
            awaitItem()
            secondViewModel.submitAnswer()
            awaitItem()
        }

        val resumedSnapshot = lessonSessionRepository.load()
        assertThat(resumedSnapshot).isNotNull()
        // At least the restored base — the fresh viewing segment since resume adds a little more
        // real wall-clock time on top, never less.
        assertThat(resumedSnapshot!!.sessionActiveElapsedMs).isAtLeast(fakeAccumulatedElapsedMs)
    }

    @Test
    fun `backgrounding the app pauses the total timer, and returning to it resumes without resetting the accumulated time`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()
            viewModel.nextStudyCard()
            val quizState = awaitItem().phase as LessonUiState.Phase.Quiz // quiz begins
            assertThat(quizState.timing.sessionActiveSegmentStartMs).isNotNull()

            appForegroundTracker.onStop(FakeLifecycleOwner)
            val pausedState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(pausedState.timing.sessionActiveSegmentStartMs).isNull()
            val elapsedWhilePaused = pausedState.timing.sessionActiveElapsedMs

            appForegroundTracker.onStart(FakeLifecycleOwner)
            val resumedState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(resumedState.timing.sessionActiveSegmentStartMs).isNotNull()
            // Resumes right where it left off — the time spent "away" (backgrounded) must not have
            // been folded in as if it were active quiz time.
            assertThat(resumedState.timing.sessionActiveElapsedMs).isEqualTo(elapsedWhilePaused)
        }
    }

    @Test
    fun `backgrounding the app pauses the per-question timer, and returning to it resumes without resetting the accumulated time`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression test: the per-question timer used to be plain wall-clock (Clock.System.now()
        // minus a stored "question shown at" timestamp) with no connection to AppForegroundTracker,
        // so backgrounding mid-question inflated both the live display and the elapsedMs recorded
        // for "slowest answers". It's now driven by the same QuizSessionTiming primitive as the
        // total-session timer above, so it must behave identically across a background/foreground
        // cycle.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()
            viewModel.nextStudyCard()
            val quizState = awaitItem().phase as LessonUiState.Phase.Quiz // quiz begins
            assertThat(quizState.timing.questionActiveSegmentStartMs).isNotNull()

            appForegroundTracker.onStop(FakeLifecycleOwner)
            val pausedState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(pausedState.timing.questionActiveSegmentStartMs).isNull()
            val elapsedWhilePaused = pausedState.timing.questionActiveElapsedMs

            appForegroundTracker.onStart(FakeLifecycleOwner)
            val resumedState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(resumedState.timing.questionActiveSegmentStartMs).isNotNull()
            // Resumes right where it left off — the time spent "away" (backgrounded) must not have
            // been folded in as if it were active question time.
            assertThat(resumedState.timing.questionActiveElapsedMs).isEqualTo(elapsedWhilePaused)

            // Grading now must record an elapsedMs built on that same paused-and-resumed total, not
            // a fresh wall-clock read from when the question first appeared.
            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val gradedState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(gradedState.timing.questionElapsedMs).isAtLeast(elapsedWhilePaused)
        }
    }

    @Test
    fun `backgrounding the app after completing the quiz does not resurrect a resumable session`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression test: pauseActiveSegment (triggered by the app backgrounding, or by this
        // ViewModel being cleared when the user navigates off the complete screen) used to
        // unconditionally re-persist a session snapshot even after the quiz-complete branch had
        // already cleared the repository — resurrecting a stale, empty-queue "active session"
        // record. The dashboard would then offer to resume a 0-lesson session that, once opened,
        // immediately re-completed.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()
            viewModel.nextStudyCard()
            awaitItem() // quiz begins

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            awaitItem()

            viewModel.onContinue()
            val finalState = awaitItem()
            assertThat(finalState.phase).isInstanceOf(LessonUiState.Phase.Complete::class.java)
            assertThat(lessonSessionRepository.load()).isNull()

            // Backgrounding from the Complete screen: the pause path has no timing fields to
            // update (they only exist on the Quiz variant) and no snapshot to persist, so no
            // state update is emitted here — the old flat-state pause used to publish one. The
            // guarantee under test is that nothing gets resurrected, checked below once the
            // tracker event has been drained.
            appForegroundTracker.onStop(FakeLifecycleOwner)
        }

        testScheduler.advanceUntilIdle()

        assertThat(lessonSessionRepository.load()).isNull()
    }

    @Test
    fun `grading the last quiz question clears the persisted session immediately, before Continue is tapped`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression test for a race where grading the last question saved a snapshot of the
        // now-empty quiz queue, and advanceQuiz's completion-time clear (fired later, once the user
        // tapped Continue) raced that save on applicationScope's multi-threaded dispatcher —
        // occasionally the stale save landed after the clear and resurrected the session. gradeAnswer
        // now clears lessonSessionRepository outright the instant grading empties the queue, so
        // there's no save left to race — verified here by checking the repository *before*
        // onContinue() is even called, not after.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()
            viewModel.nextStudyCard()
            awaitItem() // quiz begins

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            // Still on the feedback screen (Quiz phase — the cast alone proves the phase hasn't
            // flipped to Complete yet, which only happens once onContinue() runs) — yet the
            // savepoint must already be gone.
            awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(lessonSessionRepository.load()).isNull()
        }
    }

    @Test
    fun `backgrounding between grading the last quiz question and tapping Continue does not resurrect a resumable session`() = runTest(mainDispatcherRule.dispatcher) {
        // Companion to the "grading the last quiz question clears..." test above: once gradeAnswer
        // has cleared the savepoint but before onContinue() has run, isSessionComplete is still
        // false — the pause handler's guard must key off the quiz queue being empty too, not just
        // isSessionComplete/isAbandoned, or backgrounding in this exact window would re-save a
        // stale snapshot and undo the clear.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()
            viewModel.nextStudyCard()
            awaitItem() // quiz begins

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(lessonSessionRepository.load()).isNull()

            appForegroundTracker.onStop(FakeLifecycleOwner)
            awaitItem()
        }

        assertThat(lessonSessionRepository.load()).isNull()
    }

    @Test
    fun `submitting a reading into a meaning question rejects it instead of grading a miss`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()
            viewModel.nextStudyCard()
            val quizState = awaitItem().phase as LessonUiState.Phase.Quiz // quiz begins
            assertThat(quizState.currentQuestionType).isEqualTo(QuestionType.MEANING)

            viewModel.onAnswerInputChange("くち")
            awaitItem()
            viewModel.submitAnswer()
            val mismatchState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(mismatchState.answerTypeMismatchCount).isEqualTo(1)
            // Rejected outright, not graded as a miss — feedback stays null and the question isn't
            // consumed (remainingQuizCount unchanged, no requeue).
            assertThat(mismatchState.feedback).isNull()
            assertThat(mismatchState.remainingQuizCount).isEqualTo(1)

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val correctState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(correctState.feedback?.isCorrect).isTrue()
        }
    }

    @Test
    fun `submitting a romaji reading into a meaning question rejects it instead of grading a miss`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(kanjiAssignmentsJson()), jsonResponse(kanjiSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()
            viewModel.nextStudyCard()
            state = awaitItem() // quiz begins

            // Queue order is shuffled — answer reading questions correctly until meaning comes up.
            while ((state.phase as LessonUiState.Phase.Quiz).currentQuestionType != QuestionType.MEANING) {
                viewModel.onAnswerInputChange("mizu")
                awaitItem()
                viewModel.submitAnswer()
                awaitItem()
                viewModel.onContinue()
                state = awaitItem()
            }
            val remainingBeforeMismatch = (state.phase as LessonUiState.Phase.Quiz).remainingQuizCount

            viewModel.onAnswerInputChange("mizu")
            awaitItem()
            viewModel.submitAnswer()
            val mismatchState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(mismatchState.answerTypeMismatchCount).isEqualTo(1)
            // Rejected outright, not graded as a miss — feedback stays null and the question isn't
            // consumed (remainingQuizCount unchanged, no requeue).
            assertThat(mismatchState.feedback).isNull()
            assertThat(mismatchState.remainingQuizCount).isEqualTo(remainingBeforeMismatch)

            viewModel.onAnswerInputChange("Water")
            awaitItem()
            viewModel.submitAnswer()
            val correctState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(correctState.feedback?.isCorrect).isTrue()
        }
    }

    @Test
    fun `submitting a meaning into a reading question rejects it instead of grading a miss`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(kanjiAssignmentsJson()), jsonResponse(kanjiSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()
            viewModel.nextStudyCard()
            state = awaitItem() // quiz begins

            // Queue order is shuffled — answer meaning questions correctly until reading comes up.
            while ((state.phase as LessonUiState.Phase.Quiz).currentQuestionType != QuestionType.READING) {
                viewModel.onAnswerInputChange("Water")
                awaitItem()
                viewModel.submitAnswer()
                awaitItem()
                viewModel.onContinue()
                state = awaitItem()
            }
            // Captured before the mismatch submission — if the reading question happened to be
            // drawn first, the meaning question is still outstanding, so this is 2, not 1.
            val remainingBeforeMismatch = (state.phase as LessonUiState.Phase.Quiz).remainingQuizCount

            viewModel.onAnswerInputChange("Water")
            awaitItem()
            viewModel.submitAnswer()
            val mismatchState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(mismatchState.answerTypeMismatchCount).isEqualTo(1)
            assertThat(mismatchState.feedback).isNull()
            // Rejected outright, not graded as a miss — the queue is untouched.
            assertThat(mismatchState.remainingQuizCount).isEqualTo(remainingBeforeMismatch)

            viewModel.onAnswerInputChange("mizu")
            awaitItem()
            viewModel.submitAnswer()
            val correctState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(correctState.feedback?.isCorrect).isTrue()
        }
    }

    @Test
    fun `backgrounding after returning to a study-phase session preserves the study snapshot`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression: pauseActiveSegment() was calling persistCurrentState() (which always writes
        // phase=QUIZ) even while the ViewModel was in STUDY phase. The foreground tracker fires
        // resumeActiveSegment() unconditionally on any return to foreground, starting a segment even
        // in STUDY phase; the next background event then hit the bad persist path. The study snapshot
        // written by persistStudySnapshot was overwritten with an empty-queue QUIZ record, which
        // resumeQuizPhase() read back as "lesson complete".
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            val studyState = awaitItem()
            assertThat(studyState.phase).isInstanceOf(LessonUiState.Phase.Study::class.java)

            // Home button then return: the tracker fires pause()/resume() on each transition.
            // In STUDY phase both are now structural no-ops — quiz timing fields only exist once
            // the Quiz phase begins, so neither event emits a state update, and neither
            // re-persists (persistStudySnapshot already keeps the STUDY record current on every
            // card change). This foreground/background cycle is the one that used to corrupt the
            // session by calling persistCurrentState() with an empty-queue QUIZ snapshot —
            // yield() after each event lets the foreground-tracker collector process it before
            // the next one fires.
            appForegroundTracker.onStop(FakeLifecycleOwner)
            yield()
            appForegroundTracker.onStart(FakeLifecycleOwner)
            yield()
            appForegroundTracker.onStop(FakeLifecycleOwner)
            yield()
        }

        val persisted = lessonSessionRepository.load()
        assertThat(persisted).isNotNull()
        assertThat(persisted!!.phase).isEqualTo(PersistedLessonPhase.STUDY)
    }

    @Test
    fun `a stale empty-queue QUIZ snapshot never surfaces as a session to resume`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression: resumeQuizPhase() used to set isSessionComplete=true without clearing the
        // persisted session when it found an empty quizQueue — the stale record stayed in DataStore,
        // so every subsequent visit to the lesson screen also showed "Lesson complete!", an infinite
        // loop the user couldn't escape. A stale empty-queue QUIZ record is produced by (e.g.) the
        // STUDY-phase corruption above, or by answering the last quiz question correctly and
        // navigating away before tapping Continue.
        //
        // This is now caught structurally, on the read side: LessonSessionRepository.load() treats
        // an empty-queue QUIZ snapshot as unresumable and self-heals by clearing it before ever
        // returning it — so LessonViewModel never even reaches "lesson complete" for it; the very
        // first visit already falls through to a fresh lesson load, with no lingering DataStore
        // record and no intermediate "complete" flash for either this or any later visit.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))
        lessonSessionRepository.save(PersistedLessonSession(phase = PersistedLessonPhase.QUIZ))

        val viewModel = createViewModel()
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()
            // Falling through to the SELECT phase (rather than staying stuck on Complete) already
            // proves the stale record didn't surface as a resumable "lesson complete" session.
            assertThat(state.phase).isInstanceOf(LessonUiState.Phase.Select::class.java)
        }
        assertThat(lessonSessionRepository.load()).isNull()
    }

    @Test
    fun `a network error during load sets an error message and clears the loading state`() = runTest(mainDispatcherRule.dispatcher) {
        // Subjects endpoint returns 500 — refreshQueue returns ApiResult.Error, so fetchFreshQueue
        // sets errorMessage on the uiState.
        dispatch(
            assignmentsResponse = jsonResponse(radicalAssignmentsJson()),
            subjectsResponse = jsonResponse("{}", 500)
        )

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()
            // Casting to Error already proves loading has cleared — Loading and Error are disjoint
            // variants of the same sealed Phase.
            assertThat((state.phase as LessonUiState.Phase.Error).message).isNotEmpty()
        }
    }

    @Test
    fun `retrying load() after an error clears the error and shows the lesson select screen`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(
            assignmentsResponse = jsonResponse(radicalAssignmentsJson()),
            subjectsResponse = jsonResponse("{}", 500)
        )

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()
            assertThat(state.phase).isInstanceOf(LessonUiState.Phase.Error::class.java)

            // Fix the server and retry via the public retry entry point.
            dispatch(
                assignmentsResponse = jsonResponse(radicalAssignmentsJson()),
                subjectsResponse = jsonResponse(radicalSubjectsJson())
            )
            viewModel.load()

            state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading || state.phase is LessonUiState.Phase.Error) state = awaitItem()
            assertThat(state.phase).isInstanceOf(LessonUiState.Phase.Select::class.java)
        }
    }

    @Test
    fun `startSelectedLessons with nothing selected is a no-op and stays in the SELECT phase`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.selectNone()
            awaitItem() // state with empty selection
            viewModel.startSelectedLessons()

            // Must be a no-op — no phase transition, no crash.
            expectNoEvents()
            assertThat(viewModel.uiState.value.phase).isInstanceOf(LessonUiState.Phase.Select::class.java)
        }
    }

    @Test
    fun `pressing Back from the quiz preserves the session for resume on the dashboard`() = runTest(mainDispatcherRule.dispatcher) {
        // Back button = save-and-exit: the session must remain in DataStore so the dashboard card
        // shows "Resume" and the user can continue the quiz later.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()
            viewModel.startSelectedLessons()
            awaitItem() // STUDY phase — persistStudySnapshot wrote the session to DataStore
            viewModel.nextStudyCard()
            awaitItem() // QUIZ phase — beginQuiz() started an active-time segment
        }

        // Simulate the user pressing Back: Android calls ViewModel.clear() → onCleared().
        ViewModel::class.java.getDeclaredMethod("onCleared")
            .apply { isAccessible = true }
            .invoke(viewModel)
        testScheduler.advanceUntilIdle()

        // Session must still be in DataStore — Back must not clear it.
        assertThat(lessonSessionRepository.load()).isNotNull()
    }

    @Test
    fun `abandoning the session does not leave a resumable session after navigation`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression: abandonSession() cleared DataStore but onCleared() then fired and
        // re-wrote the session via pauseActiveSegment() because isAbandoned was never checked.
        // The dashboard would show "Resume" even after an explicit Abandon.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()
            viewModel.startSelectedLessons()
            awaitItem() // STUDY phase
            viewModel.nextStudyCard()
            awaitItem() // QUIZ phase (segment started)
            // Abandon clears DataStore then sets isAbandoned = true; wait for both.
            viewModel.abandonSession()
            var s = awaitItem()
            while (!s.isAbandoned) s = awaitItem()
        }

        // isAbandoned is now true. onCleared() must not re-write the session.
        ViewModel::class.java.getDeclaredMethod("onCleared")
            .apply { isAccessible = true }
            .invoke(viewModel)
        testScheduler.advanceUntilIdle()

        assertThat(lessonSessionRepository.load()).isNull()
    }

    @Test
    fun `selectFirst selects only the first n items from the available lessons`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(twoRadicalAssignmentsJson()), jsonResponse(twoRadicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()
            // Default pre-selects all 2 (batch size >= available count)
            assertThat((state.phase as LessonUiState.Phase.Select).selectedAssignmentIds).hasSize(2)

            viewModel.selectFirst(1)
            val afterSelectFirst = awaitItem().phase as LessonUiState.Phase.Select
            assertThat(afterSelectFirst.selectedAssignmentIds).containsExactly(101L)
        }
    }

    @Test
    fun `onStudyCardSwiped with an out-of-range index is a no-op`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            val studyState = awaitItem().phase as LessonUiState.Phase.Study
            assertThat(studyState.studyIndex).isEqualTo(0)
            assertThat(studyState.studyItems).hasSize(1)

            viewModel.onStudyCardSwiped(5) // out of range for a 1-item list
            expectNoEvents()
            assertThat((viewModel.uiState.value.phase as LessonUiState.Phase.Study).studyIndex).isEqualTo(0)
        }
    }

    @Test
    fun `submitAnswer with blank input is a no-op`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()
            viewModel.nextStudyCard()
            awaitItem() // quiz begins

            // Empty input — submitAnswer must not grade or produce feedback
            viewModel.submitAnswer()
            expectNoEvents()
            val quiz = viewModel.uiState.value.phase as LessonUiState.Phase.Quiz
            assertThat(quiz.feedback).isNull()
            assertThat(quiz.remainingQuizCount).isEqualTo(1)
        }
    }

    @Test
    fun `submitAnswer while feedback is already showing is a no-op`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()
            viewModel.nextStudyCard()
            awaitItem() // quiz begins

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(feedbackState.feedback).isNotNull()
            val remainingAfterFirstSubmit = feedbackState.remainingQuizCount

            // Second submit while feedback is visible — must be a no-op
            viewModel.submitAnswer()
            expectNoEvents()
            val quiz = viewModel.uiState.value.phase as LessonUiState.Phase.Quiz
            assertThat(quiz.remainingQuizCount).isEqualTo(remainingAfterFirstSubmit)
        }
    }

    @Test
    fun `answering a reading question autoplays the correct pronunciation when the setting is enabled`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(kanjiAssignmentsJson()), jsonResponse(kanjiSubjectsJsonWithAudio()))

        val viewModel = createViewModel()

        // Real DataStore reads settle asynchronously on their own IO dispatcher, unlike the
        // Main-dispatcher ViewModel coroutines the test rule makes run synchronously — so wait for
        // the play() side effect via the player's own state flow rather than checking playedAudios
        // immediately after the uiState turbine block exits.
        pronunciationAudioPlayer.state.test {
            assertThat(awaitItem()).isEqualTo(PlaybackState.IDLE)

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

                viewModel.startSelectedLessons()
                awaitItem()
                viewModel.nextStudyCard()
                state = awaitItem() // quiz begins

                while ((state.phase as LessonUiState.Phase.Quiz).currentQuestionType != QuestionType.READING) {
                    viewModel.onAnswerInputChange(if ((state.phase as LessonUiState.Phase.Quiz).currentQuestionType == QuestionType.MEANING) "Water" else "mizu")
                    awaitItem()
                    viewModel.submitAnswer()
                    awaitItem()
                    viewModel.onContinue()
                    state = awaitItem()
                }

                viewModel.onAnswerInputChange("mizu")
                awaitItem()
                viewModel.submitAnswer()
                awaitItem()
            }

            assertThat(awaitItem()).isEqualTo(PlaybackState.PLAYING)
        }

        assertThat(pronunciationAudioPlayer.playedAudios).hasSize(1)
        assertThat(pronunciationAudioPlayer.playedAudios.first().url).isEqualTo("https://api.wanikani.com/audio/mizu.mp3")
    }

    private fun kanjiAssignmentsJson() = """
        {
          "object": "collection", "url": "https://api.wanikani.com/v2/assignments", "total_count": 1,
          "data": [{
            "id": 101, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/101",
            "data_updated_at": "2026-01-01T00:00:00.000000Z",
            "data": {
              "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": 1, "subject_type": "kanji",
              "srs_stage": 0, "unlocked_at": "2026-01-01T00:00:00.000000Z", "hidden": false
            }
          }]
        }
    """.trimIndent()

    private fun kanjiSubjectsJson() = """
        {
          "object": "collection", "url": "https://api.wanikani.com/v2/subjects", "total_count": 1,
          "data": [{
            "id": 1, "object": "kanji", "url": "https://api.wanikani.com/v2/subjects/1",
            "data_updated_at": "2026-01-01T00:00:00.000000Z",
            "data": {
              "created_at": "2020-01-01T00:00:00.000000Z", "level": 3, "slug": "water",
              "characters": "水",
              "meanings": [{"meaning": "Water", "primary": true, "accepted_meaning": true}],
              "readings": [{"reading": "みず", "primary": true, "accepted_reading": true}]
            }
          }]
        }
    """.trimIndent()

    private fun kanjiSubjectsJsonWithAudio() = """
        {
          "object": "collection", "url": "https://api.wanikani.com/v2/subjects", "total_count": 1,
          "data": [{
            "id": 1, "object": "kanji", "url": "https://api.wanikani.com/v2/subjects/1",
            "data_updated_at": "2026-01-01T00:00:00.000000Z",
            "data": {
              "created_at": "2020-01-01T00:00:00.000000Z", "level": 3, "slug": "water",
              "characters": "水",
              "meanings": [{"meaning": "Water", "primary": true, "accepted_meaning": true}],
              "readings": [{"reading": "みず", "primary": true, "accepted_reading": true}],
              "pronunciation_audios": [
                {
                  "url": "https://api.wanikani.com/audio/mizu.mp3",
                  "content_type": "audio/mpeg",
                  "metadata": {"gender": "female", "pronunciation": "みず"}
                }
              ]
            }
          }]
        }
    """.trimIndent()

    private fun radicalAssignmentsJson() = """
        {
          "object": "collection", "url": "https://api.wanikani.com/v2/assignments", "total_count": 1,
          "data": [{
            "id": 101, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/101",
            "data_updated_at": "2026-01-01T00:00:00.000000Z",
            "data": {
              "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": 1, "subject_type": "radical",
              "srs_stage": 0, "unlocked_at": "2026-01-01T00:00:00.000000Z", "hidden": false
            }
          }]
        }
    """.trimIndent()

    private fun radicalSubjectsJson() = """
        {
          "object": "collection", "url": "https://api.wanikani.com/v2/subjects", "total_count": 1,
          "data": [{
            "id": 1, "object": "radical", "url": "https://api.wanikani.com/v2/subjects/1",
            "data_updated_at": "2026-01-01T00:00:00.000000Z",
            "data": {
              "created_at": "2020-01-01T00:00:00.000000Z", "level": 1, "slug": "mouth",
              "characters": "口",
              "meanings": [{"meaning": "Mouth", "primary": true, "accepted_meaning": true}],
              "readings": [],
              "meaning_mnemonic": "A stream of water."
            }
          }]
        }
    """.trimIndent()

    private fun twoRadicalAssignmentsJson() = """
        {
          "object": "collection", "url": "https://api.wanikani.com/v2/assignments", "total_count": 2,
          "data": [
            {
              "id": 101, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/101",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": 1, "subject_type": "radical",
                "srs_stage": 0, "unlocked_at": "2026-01-01T00:00:00.000000Z", "hidden": false
              }
            },
            {
              "id": 102, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/102",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": 2, "subject_type": "radical",
                "srs_stage": 0, "unlocked_at": "2026-01-01T00:00:00.000000Z", "hidden": false
              }
            }
          ]
        }
    """.trimIndent()

    private fun twoRadicalSubjectsJson() = """
        {
          "object": "collection", "url": "https://api.wanikani.com/v2/subjects", "total_count": 2,
          "data": [
            {
              "id": 1, "object": "radical", "url": "https://api.wanikani.com/v2/subjects/1",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2020-01-01T00:00:00.000000Z", "level": 1, "slug": "mouth",
                "characters": "口",
                "meanings": [{"meaning": "Mouth", "primary": true, "accepted_meaning": true}],
                "readings": []
              }
            },
            {
              "id": 2, "object": "radical", "url": "https://api.wanikani.com/v2/subjects/2",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2020-01-01T00:00:00.000000Z", "level": 1, "slug": "ground",
                "characters": "一",
                "meanings": [{"meaning": "Ground", "primary": true, "accepted_meaning": true}],
                "readings": []
              }
            }
          ]
        }
    """.trimIndent()

    private fun emptyCollectionJson() = """
        {"object": "collection", "url": "https://api.wanikani.com/v2/x", "total_count": 0, "data": []}
    """.trimIndent()

    private fun startAssignmentResultJson() = """
        {
          "id": 101, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/101",
          "data_updated_at": "2026-01-01T00:00:00.000000Z",
          "data": {
            "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": 1, "subject_type": "radical",
            "srs_stage": 1, "started_at": "2026-01-01T00:00:00.000000Z", "hidden": false
          }
        }
    """.trimIndent()
}
