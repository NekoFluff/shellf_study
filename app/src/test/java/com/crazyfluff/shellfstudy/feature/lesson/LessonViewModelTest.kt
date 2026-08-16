package com.crazyfluff.shellfstudy.feature.lesson

import com.crazyfluff.shellfstudy.shared.data.PersistedLessonPhase
import com.crazyfluff.shellfstudy.shared.data.PersistedLessonSession
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonPhase
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonViewModel
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.LessonSessionRepository
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.PitchAccentRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.SubjectRepository
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
        repositories = buildTestRepositories(server.url("/").toString())
        assignmentRepository = repositories.assignmentRepository
        pitchAccentRepository = repositories.pitchAccentRepository
        subjectRepository = repositories.subjectRepository
        strokeOrderRepository = FakeStrokeOrderRepository()
        outboxRepository = OutboxRepository(repositories.outboxDao, repositories.outboxSyncScheduler, dataStore)
        lessonSessionRepository = LessonSessionRepository(dataStore, Json { ignoreUnknownKeys = true })
        pronunciationAudioPlayer = FakePronunciationAudioPlayer()
        appForegroundTracker = AppForegroundTracker()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun TestScope.createViewModel() = LessonViewModel(
        assignmentRepository, outboxRepository, lessonSessionRepository, pitchAccentRepository, settingsRepository,
        subjectRepository, strokeOrderRepository, pronunciationAudioPlayer, appForegroundTracker, backgroundScope
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
            while (state.isLoading) state = awaitItem()

            assertThat(state.phase).isEqualTo(LessonPhase.SELECT)
            assertThat(state.availableLessons).hasSize(1)
            assertThat(state.selectedAssignmentIds).containsExactly(101L)
        }
    }

    @Test
    fun `showSubjectTypeLabel setting flows into uiState`() = runTest(mainDispatcherRule.dispatcher) {
        settingsRepository.setShowSubjectTypeLabel(true)
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading || !state.showSubjectTypeLabel) state = awaitItem()
            assertThat(state.showSubjectTypeLabel).isTrue()
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
            while (state.isLoading || !state.showTotalTimer || !state.showQuestionTimer) state = awaitItem()
            assertThat(state.showTotalTimer).isTrue()
            assertThat(state.showQuestionTimer).isTrue()
        }
    }

    @Test
    fun `starting the quiz sets sessionActiveSegmentStartMs and questionStartTimeMs`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()
            viewModel.nextStudyCard()
            val quizState = awaitItem()

            assertThat(quizState.sessionActiveSegmentStartMs).isNotNull()
            assertThat(quizState.questionStartTimeMs).isNotNull()
        }
    }

    @Test
    fun `an empty lesson queue is reported as no lessons available`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(emptyCollectionJson()), jsonResponse(emptyCollectionJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.hasNoLessonsAvailable).isTrue()
        }
    }

    @Test
    fun `toggling a lesson selection adds or removes it`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(twoRadicalAssignmentsJson()), jsonResponse(twoRadicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.selectedAssignmentIds).containsExactly(101L, 102L)

            viewModel.toggleLessonSelection(101L)
            assertThat(awaitItem().selectedAssignmentIds).containsExactly(102L)

            viewModel.toggleLessonSelection(101L)
            assertThat(awaitItem().selectedAssignmentIds).containsExactly(101L, 102L)
        }
    }

    @Test
    fun `selectNone and selectAll clear and restore the full selection`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(twoRadicalAssignmentsJson()), jsonResponse(twoRadicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.selectNone()
            assertThat(awaitItem().selectedAssignmentIds).isEmpty()

            viewModel.selectAll()
            assertThat(awaitItem().selectedAssignmentIds).containsExactly(101L, 102L)
        }
    }

    @Test
    fun `startSelectedLessons enters the study phase with only the selected items`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(twoRadicalAssignmentsJson()), jsonResponse(twoRadicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.toggleLessonSelection(102L)
            awaitItem()

            viewModel.startSelectedLessons()
            val studyState = awaitItem()
            assertThat(studyState.phase).isEqualTo(LessonPhase.STUDY)
            assertThat(studyState.studyItems).hasSize(1)
            assertThat(studyState.studyItems.first().assignmentId).isEqualTo(101L)
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
            while (state.isLoading) state = awaitItem()

            viewModel.startSelectedLessons()
            val studyState = awaitItem()

            assertThat(studyState.strokeOrderBySubjectId[1L]).isInstanceOf(StrokeOrderUiState.Available::class.java)
            assertThat(studyState.strokeOrderBySubjectId[2L]).isEqualTo(StrokeOrderUiState.Unavailable)
        }
    }

    @Test
    fun `advancing past the last study card starts the quiz`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.startSelectedLessons()
            val studyState = awaitItem()
            assertThat(studyState.studyIndex).isEqualTo(0)

            viewModel.nextStudyCard()
            val quizState = awaitItem()
            assertThat(quizState.phase).isEqualTo(LessonPhase.QUIZ)
            assertThat(quizState.currentQuestionType).isEqualTo(QuestionType.MEANING)
            assertThat(quizState.totalQuizCount).isEqualTo(1)
        }
    }

    @Test
    fun `previousStudyCard moves back a card but not before the first`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(twoRadicalAssignmentsJson()), jsonResponse(twoRadicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()

            viewModel.nextStudyCard()
            val secondCard = awaitItem()
            assertThat(secondCard.studyIndex).isEqualTo(1)

            viewModel.previousStudyCard()
            val backToFirst = awaitItem()
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
            while (state.isLoading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()

            viewModel.onStudyCardSwiped(1)
            assertThat(awaitItem().studyIndex).isEqualTo(1)
        }
    }

    @Test
    fun `a correct quiz answer marks the assignment started once all its questions are done`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()

            viewModel.nextStudyCard()
            awaitItem() // quiz begins

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem()
            assertThat(feedbackState.feedback?.isCorrect).isTrue()

            viewModel.onContinue()
            val finalState = awaitItem()
            assertThat(finalState.isSessionComplete).isTrue()
        }

        // Local-write-first: no network call happens from the ViewModel path at all — the lesson
        // start is durably queued for the background sync worker instead.
        val queued = repositories.outboxDao.allLessonStarts()
        assertThat(queued).hasSize(1)
        assertThat(queued.first().assignmentId).isEqualTo(101L)
        assertThat(repositories.outboxSyncScheduler.requestCount).isEqualTo(1)
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
            while (state.isLoading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()

            viewModel.nextStudyCard()
            awaitItem() // quiz begins

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem()
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
            while (state.isLoading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()

            viewModel.nextStudyCard()
            awaitItem() // quiz begins

            viewModel.onAnswerInputChange("wrong")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem()
            assertThat(feedbackState.feedback?.isCorrect).isFalse()
            assertThat(feedbackState.remainingQuizCount).isEqualTo(1)

            viewModel.onContinue()
            val requeuedState = awaitItem()
            assertThat(requeuedState.isSessionComplete).isFalse()
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
            while (state.isLoading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()
            viewModel.nextStudyCard()
            awaitItem() // studyIndex 1
            viewModel.nextStudyCard()
            val quizState = awaitItem() // quiz begins
            assertThat(quizState.questionElapsedMs).isNull()

            val item = quizState.currentQuizItem!!
            viewModel.onAnswerInputChange(item.meanings.first())
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem()
            assertThat(feedbackState.questionElapsedMs).isNotNull()

            viewModel.onContinue()
            val nextState = awaitItem()
            assertThat(nextState.isSessionComplete).isFalse()
            assertThat(nextState.questionElapsedMs).isNull()
        }
    }

    @Test
    fun `dontKnowAnswer grades as incorrect and requeues`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()

            viewModel.nextStudyCard()
            awaitItem() // quiz begins

            viewModel.dontKnowAnswer()
            val feedbackState = awaitItem()
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
            while (state.isLoading) state = awaitItem()

            firstViewModel.startSelectedLessons()
            awaitItem()

            firstViewModel.nextStudyCard()
            val secondCard = awaitItem()
            assertThat(secondCard.studyIndex).isEqualTo(1)
        }
        val requestCountAfterFirstLoad = server.requestCount

        // Simulate leaving and coming back mid-study, before the quiz ever begins: a fresh
        // ViewModel sharing the same repositories should land back on the same card in the same
        // batch, rather than forcing lesson re-selection and restudying from card one.
        val secondViewModel = createViewModel()
        secondViewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.phase).isEqualTo(LessonPhase.STUDY)
            assertThat(state.studyIndex).isEqualTo(1)
            assertThat(state.studyItems.map { it.assignmentId }).containsExactly(101L, 102L).inOrder()
        }
        assertThat(server.requestCount).isEqualTo(requestCountAfterFirstLoad)
    }

    @Test
    fun `a new ViewModel resumes a persisted quiz session instead of refetching from the network`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val firstViewModel = createViewModel()
        firstViewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            firstViewModel.startSelectedLessons()
            awaitItem()

            firstViewModel.nextStudyCard()
            val quizState = awaitItem()
            assertThat(quizState.phase).isEqualTo(LessonPhase.QUIZ)
        }
        val requestCountAfterFirstLoad = server.requestCount

        // Simulate leaving and coming back: a fresh ViewModel sharing the same repositories should
        // pick the in-progress quiz back up rather than hitting the network again.
        val secondViewModel = createViewModel()
        secondViewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.phase).isEqualTo(LessonPhase.QUIZ)
            assertThat(state.totalQuizCount).isEqualTo(1)
            assertThat(state.currentQuizItem?.assignmentId).isEqualTo(101L)
        }
        assertThat(server.requestCount).isEqualTo(requestCountAfterFirstLoad)
    }

    @Test
    fun `resuming falls back to a fresh fetch when a queued item can no longer be found`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val firstViewModel = createViewModel()
        firstViewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            firstViewModel.startSelectedLessons()
            awaitItem()

            firstViewModel.nextStudyCard()
            awaitItem() // quiz begins, persisted
        }

        // Simulate the assignment being started elsewhere (e.g. synced in from another device)
        // between sessions — it's no longer due for a lesson, so the persisted queue entry
        // referencing it can't be resolved on resume, and resumeFromPersisted must fall back to a
        // fresh fetch instead of crashing.
        val existing = repositories.assignmentDao.getById(101L)!!
        repositories.assignmentDao.upsertAll(listOf(existing.copy(startedAt = "2026-01-02T00:00:00.000000Z")))

        val secondViewModel = createViewModel()
        secondViewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.errorMessage).isNull()
            assertThat(state.hasNoLessonsAvailable).isTrue()
        }
        assertThat(lessonSessionRepository.load()).isNull()
    }

    @Test
    fun `completing the quiz clears the persisted lesson session`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

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
            assertThat(finalState.isSessionComplete).isTrue()
        }

        assertThat(lessonSessionRepository.load()).isNull()
    }

    @Test
    fun `abandonSession clears persisted state and marks the session abandoned`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

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
            while (state.isLoading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()
            viewModel.nextStudyCard()
            awaitItem() // quiz begins

            // Miss the only question first, then answer it correctly — a "correct on first try"
            // count of zero and one missed item is the expected result.
            viewModel.onAnswerInputChange("wrong")
            awaitItem()
            viewModel.submitAnswer()
            val missedState = awaitItem()
            assertThat(missedState.feedback?.isCorrect).isFalse()

            viewModel.onContinue()
            awaitItem() // requeued question shown again

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            awaitItem()

            viewModel.onContinue()
            val finalState = awaitItem()

            assertThat(finalState.isSessionComplete).isTrue()
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
            while (state.isLoading) state = awaitItem()

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
                val item = state.currentQuizItem!!
                viewModel.onAnswerInputChange(item.meanings.first())
                awaitItem()
                viewModel.submitAnswer()
                awaitItem()
                viewModel.onContinue()
                state = awaitItem()
                isComplete = state.isSessionComplete
            }

            assertThat(state.isSessionComplete).isTrue()
            assertThat(state.sessionItemsLearned).isEqualTo(2)
            assertThat(state.sessionItemsCorrectFirstTry).isEqualTo(2)
            assertThat(state.sessionMissedItems).isEmpty()
        }
    }

    @Test
    fun `resuming a persisted session preserves progress for the eventual session summary`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(twoRadicalAssignmentsJson()), jsonResponse(twoRadicalSubjectsJson()))

        val firstViewModel = createViewModel()
        firstViewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

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
            while (state.isLoading) state = awaitItem()

            var isComplete = false
            var safetyCounter = 0
            while (!isComplete && safetyCounter < 10) {
                safetyCounter++
                val item = state.currentQuizItem!!
                secondViewModel.onAnswerInputChange(item.meanings.first())
                awaitItem()
                secondViewModel.submitAnswer()
                awaitItem()
                secondViewModel.onContinue()
                state = awaitItem()
                isComplete = state.isSessionComplete
            }

            assertThat(state.isSessionComplete).isTrue()
            assertThat(state.sessionItemsLearned).isEqualTo(2)
            assertThat(state.sessionMissedItems).hasSize(1)
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
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val firstViewModel = createViewModel()
        firstViewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

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
            while (state.isLoading) state = awaitItem()
            assertThat(state.phase).isEqualTo(LessonPhase.QUIZ)
            assertThat(state.sessionActiveElapsedMs).isEqualTo(fakeAccumulatedElapsedMs)
            assertThat(state.sessionActiveSegmentStartMs).isNotNull()

            // Forces a fresh persisted snapshot so the resumed accumulated time can be inspected.
            secondViewModel.onAnswerInputChange("Mouth")
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
            while (state.isLoading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()
            viewModel.nextStudyCard()
            val quizState = awaitItem() // quiz begins
            assertThat(quizState.sessionActiveSegmentStartMs).isNotNull()

            appForegroundTracker.onStop(FakeLifecycleOwner)
            val pausedState = awaitItem()
            assertThat(pausedState.sessionActiveSegmentStartMs).isNull()
            val elapsedWhilePaused = pausedState.sessionActiveElapsedMs

            appForegroundTracker.onStart(FakeLifecycleOwner)
            val resumedState = awaitItem()
            assertThat(resumedState.sessionActiveSegmentStartMs).isNotNull()
            // Resumes right where it left off — the time spent "away" (backgrounded) must not have
            // been folded in as if it were active quiz time.
            assertThat(resumedState.sessionActiveElapsedMs).isEqualTo(elapsedWhilePaused)
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
            while (state.isLoading) state = awaitItem()

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
            assertThat(finalState.isSessionComplete).isTrue()
            assertThat(lessonSessionRepository.load()).isNull()

            appForegroundTracker.onStop(FakeLifecycleOwner)
            val pausedState = awaitItem()
            assertThat(pausedState.isSessionComplete).isTrue()
        }

        assertThat(lessonSessionRepository.load()).isNull()
    }

    @Test
    fun `submitting a reading into a meaning question rejects it instead of grading a miss`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()
            viewModel.nextStudyCard()
            val quizState = awaitItem() // quiz begins
            assertThat(quizState.currentQuestionType).isEqualTo(QuestionType.MEANING)

            viewModel.onAnswerInputChange("くち")
            awaitItem()
            viewModel.submitAnswer()
            val mismatchState = awaitItem()
            assertThat(mismatchState.answerTypeMismatchCount).isEqualTo(1)
            // Rejected outright, not graded as a miss — feedback stays null and the question isn't
            // consumed (remainingQuizCount unchanged, no requeue).
            assertThat(mismatchState.feedback).isNull()
            assertThat(mismatchState.remainingQuizCount).isEqualTo(1)

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val correctState = awaitItem()
            assertThat(correctState.feedback?.isCorrect).isTrue()
        }
    }

    @Test
    fun `submitting a romaji reading into a meaning question rejects it instead of grading a miss`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(kanjiAssignmentsJson()), jsonResponse(kanjiSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()
            viewModel.nextStudyCard()
            state = awaitItem() // quiz begins

            // Queue order is shuffled — answer reading questions correctly until meaning comes up.
            while (state.currentQuestionType != QuestionType.MEANING) {
                viewModel.onAnswerInputChange("mizu")
                awaitItem()
                viewModel.submitAnswer()
                awaitItem()
                viewModel.onContinue()
                state = awaitItem()
            }
            val remainingBeforeMismatch = state.remainingQuizCount

            viewModel.onAnswerInputChange("mizu")
            awaitItem()
            viewModel.submitAnswer()
            val mismatchState = awaitItem()
            assertThat(mismatchState.answerTypeMismatchCount).isEqualTo(1)
            // Rejected outright, not graded as a miss — feedback stays null and the question isn't
            // consumed (remainingQuizCount unchanged, no requeue).
            assertThat(mismatchState.feedback).isNull()
            assertThat(mismatchState.remainingQuizCount).isEqualTo(remainingBeforeMismatch)

            viewModel.onAnswerInputChange("Water")
            awaitItem()
            viewModel.submitAnswer()
            val correctState = awaitItem()
            assertThat(correctState.feedback?.isCorrect).isTrue()
        }
    }

    @Test
    fun `submitting a meaning into a reading question rejects it instead of grading a miss`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(kanjiAssignmentsJson()), jsonResponse(kanjiSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.startSelectedLessons()
            awaitItem()
            viewModel.nextStudyCard()
            state = awaitItem() // quiz begins

            // Queue order is shuffled — answer meaning questions correctly until reading comes up.
            while (state.currentQuestionType != QuestionType.READING) {
                viewModel.onAnswerInputChange("Water")
                awaitItem()
                viewModel.submitAnswer()
                awaitItem()
                viewModel.onContinue()
                state = awaitItem()
            }
            // Captured before the mismatch submission — if the reading question happened to be
            // drawn first, the meaning question is still outstanding, so this is 2, not 1.
            val remainingBeforeMismatch = state.remainingQuizCount

            viewModel.onAnswerInputChange("Water")
            awaitItem()
            viewModel.submitAnswer()
            val mismatchState = awaitItem()
            assertThat(mismatchState.answerTypeMismatchCount).isEqualTo(1)
            assertThat(mismatchState.feedback).isNull()
            // Rejected outright, not graded as a miss — the queue is untouched.
            assertThat(mismatchState.remainingQuizCount).isEqualTo(remainingBeforeMismatch)

            viewModel.onAnswerInputChange("mizu")
            awaitItem()
            viewModel.submitAnswer()
            val correctState = awaitItem()
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
            while (state.isLoading) state = awaitItem()

            viewModel.startSelectedLessons()
            val studyState = awaitItem()
            assertThat(studyState.phase).isEqualTo(LessonPhase.STUDY)

            // Home button: no segment running yet in STUDY phase — pauseActiveSegment returns early
            // with no state update. yield() lets the dispatcher run the foreground-tracker collector
            // (which processes false) before the start event fires; without the yield the two are
            // conflated and the collector only sees the net value (true → false → true = no change),
            // so resumeActiveSegment is never called and there is nothing to test.
            appForegroundTracker.onStop(FakeLifecycleOwner)
            yield()
            // Return: foreground tracker fires resumeActiveSegment() — starts a segment even
            // though we're still in STUDY phase.
            appForegroundTracker.onStart(FakeLifecycleOwner)
            awaitItem() // sessionActiveSegmentStartMs set
            // Home button (or onCleared from Back) with a now-running segment: this is the
            // path that used to corrupt the session by calling persistCurrentState().
            appForegroundTracker.onStop(FakeLifecycleOwner)
            awaitItem() // sessionActiveSegmentStartMs cleared
        }

        val persisted = lessonSessionRepository.load()
        assertThat(persisted).isNotNull()
        assertThat(persisted!!.phase).isEqualTo(PersistedLessonPhase.STUDY)
    }

    @Test
    fun `resuming a stale empty-queue QUIZ snapshot clears it so the next visit starts fresh`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression: resumeQuizPhase() used to set isSessionComplete=true without clearing the
        // persisted session when it found an empty quizQueue. The stale record stayed in DataStore,
        // so every subsequent visit to the lesson screen also showed "Lesson complete!" — an
        // infinite loop the user couldn't escape. A stale empty-queue QUIZ record is produced by
        // (e.g.) the STUDY-phase corruption above, or by answering the last quiz question correctly
        // and navigating away before tapping Continue.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))
        lessonSessionRepository.save(PersistedLessonSession(phase = PersistedLessonPhase.QUIZ))

        // First visit: the stale session produces a (spurious) "lesson complete" screen once.
        val firstViewModel = createViewModel()
        firstViewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.isSessionComplete).isTrue()
        }
        // Session must be cleared so the next visit doesn't also show "lesson complete".
        assertThat(lessonSessionRepository.load()).isNull()

        // Second visit: gets a fresh lesson load rather than another infinite "lesson complete".
        val secondViewModel = createViewModel()
        secondViewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.isSessionComplete).isFalse()
            assertThat(state.phase).isEqualTo(LessonPhase.SELECT)
        }
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
