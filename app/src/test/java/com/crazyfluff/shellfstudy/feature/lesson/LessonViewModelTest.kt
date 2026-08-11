package com.crazyfluff.shellfstudy.feature.lesson

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.core.data.AssignmentRepository
import com.crazyfluff.shellfstudy.core.data.PitchAccentRepository
import com.crazyfluff.shellfstudy.core.data.SettingsRepository
import com.crazyfluff.shellfstudy.core.data.SubjectRepository
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.fakes.jsonResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

class LessonViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var assignmentRepository: AssignmentRepository
    private lateinit var pitchAccentRepository: PitchAccentRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var subjectRepository: SubjectRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        settingsRepository = SettingsRepository(dataStore)
        val repositories = buildTestRepositories(server.url("/").toString())
        assignmentRepository = repositories.assignmentRepository
        pitchAccentRepository = repositories.pitchAccentRepository
        subjectRepository = repositories.subjectRepository
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun createViewModel() =
        LessonViewModel(assignmentRepository, pitchAccentRepository, settingsRepository, subjectRepository)

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
    fun `loads a batch of lessons into the select phase with all pre-selected`() = runTest {
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
    fun `an empty lesson queue is reported as no lessons available`() = runTest {
        dispatch(jsonResponse(emptyCollectionJson()), jsonResponse(emptyCollectionJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.hasNoLessonsAvailable).isTrue()
        }
    }

    @Test
    fun `toggling a lesson selection adds or removes it`() = runTest {
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
    fun `selectNone and selectAll clear and restore the full selection`() = runTest {
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
    fun `startSelectedLessons enters the study phase with only the selected items`() = runTest {
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
    fun `advancing past the last study card starts the quiz`() = runTest {
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
            assertThat(quizState.currentQuestionType).isEqualTo(LessonQuestionType.MEANING)
            assertThat(quizState.totalQuizCount).isEqualTo(1)
        }
    }

    @Test
    fun `previousStudyCard moves back a card but not before the first`() = runTest {
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
    fun `onStudyCardSwiped updates the study index directly`() = runTest {
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
    fun `a correct quiz answer marks the assignment started once all its questions are done`() = runTest {
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

        val startRequest = generateSequence { server.takeRequest(1, TimeUnit.SECONDS) }
            .firstOrNull { it.path?.contains("/start") == true }
        assertThat(startRequest).isNotNull()
        assertThat(startRequest?.method).isEqualTo("PUT")
        assertThat(startRequest?.path).contains("/assignments/101/start")
    }

    @Test
    fun `an incorrect quiz answer requeues the question instead of starting the assignment`() = runTest {
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
            assertThat(requeuedState.currentQuestionType).isEqualTo(LessonQuestionType.MEANING)
        }
    }

    @Test
    fun `dontKnowAnswer grades as incorrect and requeues`() = runTest {
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
