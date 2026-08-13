package com.crazyfluff.shellfstudy.feature.lesson

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.core.data.AssignmentRepository
import com.crazyfluff.shellfstudy.core.data.LessonSessionRepository
import com.crazyfluff.shellfstudy.core.data.OutboxRepository
import com.crazyfluff.shellfstudy.core.data.PitchAccentRepository
import com.crazyfluff.shellfstudy.core.data.SettingsRepository
import com.crazyfluff.shellfstudy.core.data.SubjectRepository
import com.crazyfluff.shellfstudy.core.data.model.StrokeOrderStroke
import com.crazyfluff.shellfstudy.core.data.strokeorder.StrokeOrderRepository
import com.crazyfluff.shellfstudy.core.designsystem.strokeorder.StrokeOrderUiState
import com.crazyfluff.shellfstudy.core.quiz.QuestionType
import com.crazyfluff.shellfstudy.fakes.FakeStrokeOrderRepository
import com.crazyfluff.shellfstudy.fakes.TestRepositories
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.fakes.jsonResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun TestScope.createViewModel() = LessonViewModel(
        assignmentRepository, outboxRepository, lessonSessionRepository, pitchAccentRepository, settingsRepository,
        subjectRepository, strokeOrderRepository, backgroundScope
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
