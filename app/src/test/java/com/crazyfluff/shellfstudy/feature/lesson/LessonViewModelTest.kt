package com.crazyfluff.shellfstudy.feature.lesson

import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.core.data.WaniKaniRepository
import com.crazyfluff.shellfstudy.fakes.FakeAssignmentDao
import com.crazyfluff.shellfstudy.fakes.FakeSubjectDao
import com.crazyfluff.shellfstudy.fakes.buildTestApi
import com.crazyfluff.shellfstudy.fakes.jsonResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class LessonViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer
    private lateinit var waniKaniRepository: WaniKaniRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        waniKaniRepository = WaniKaniRepository(
            api = buildTestApi(server.url("/").toString()),
            subjectDao = FakeSubjectDao(),
            assignmentDao = FakeAssignmentDao()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun createViewModel() = LessonViewModel(waniKaniRepository)

    @Test
    fun `loads a batch of lessons into the study phase`() = runTest {
        server.enqueue(jsonResponse(radicalAssignmentsJson()))
        server.enqueue(jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            assertThat(state.phase).isEqualTo(LessonPhase.STUDY)
            assertThat(state.studyItems).hasSize(1)
            assertThat(state.studyItems.first().meaningMnemonic).isEqualTo("A stream of water.")
        }
    }

    @Test
    fun `an empty lesson queue is reported as no lessons available`() = runTest {
        server.enqueue(jsonResponse(emptyAssignmentsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.hasNoLessonsAvailable).isTrue()
        }
    }

    @Test
    fun `advancing past the last study card starts the quiz`() = runTest {
        server.enqueue(jsonResponse(radicalAssignmentsJson()))
        server.enqueue(jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.studyIndex).isEqualTo(0)

            viewModel.nextStudyCard()
            val quizState = awaitItem()
            assertThat(quizState.phase).isEqualTo(LessonPhase.QUIZ)
            assertThat(quizState.currentQuestionType).isEqualTo(LessonQuestionType.MEANING)
            assertThat(quizState.totalQuizCount).isEqualTo(1)
        }
    }

    @Test
    fun `previousStudyCard moves back a card but not before the first`() = runTest {
        server.enqueue(jsonResponse(twoRadicalAssignmentsJson()))
        server.enqueue(jsonResponse(twoRadicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

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
    fun `a correct quiz answer marks the assignment started once all its questions are done`() = runTest {
        server.enqueue(jsonResponse(radicalAssignmentsJson()))
        server.enqueue(jsonResponse(radicalSubjectsJson()))
        server.enqueue(jsonResponse(startAssignmentResultJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

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

        server.takeRequest(5, TimeUnit.SECONDS) // assignments
        server.takeRequest(5, TimeUnit.SECONDS) // subjects
        val startRequest = server.takeRequest(5, TimeUnit.SECONDS)
        assertThat(startRequest).isNotNull()
        assertThat(startRequest?.method).isEqualTo("PUT")
        assertThat(startRequest?.path).contains("/assignments/101/start")
    }

    @Test
    fun `an incorrect quiz answer requeues the question instead of starting the assignment`() = runTest {
        server.enqueue(jsonResponse(radicalAssignmentsJson()))
        server.enqueue(jsonResponse(radicalSubjectsJson()))
        server.enqueue(jsonResponse(startAssignmentResultJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

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
        server.enqueue(jsonResponse(radicalAssignmentsJson()))
        server.enqueue(jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

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

    private fun emptyAssignmentsJson() = """
        {"object": "collection", "url": "https://api.wanikani.com/v2/assignments", "total_count": 0, "data": []}
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
