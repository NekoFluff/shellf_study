package com.crazyfluff.shellfstudy.feature.review

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.core.audio.PlaybackState
import com.crazyfluff.shellfstudy.core.data.AssignmentRepository
import com.crazyfluff.shellfstudy.core.data.ReviewSessionRepository
import com.crazyfluff.shellfstudy.core.data.SettingsRepository
import com.crazyfluff.shellfstudy.core.data.WaniKaniRepository
import com.crazyfluff.shellfstudy.core.data.model.RankChange
import com.crazyfluff.shellfstudy.core.data.model.SrsStage
import com.crazyfluff.shellfstudy.fakes.FakePronunciationAudioPlayer
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.fakes.jsonResponse
import com.google.common.truth.Truth.assertThat
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

class ReviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var waniKaniRepository: WaniKaniRepository
    private lateinit var assignmentRepository: AssignmentRepository
    private lateinit var reviewSessionRepository: ReviewSessionRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var pronunciationAudioPlayer: FakePronunciationAudioPlayer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val repositories = buildTestRepositories(server.url("/").toString())
        waniKaniRepository = repositories.waniKaniRepository
        assignmentRepository = repositories.assignmentRepository
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        reviewSessionRepository = ReviewSessionRepository(dataStore, Json { ignoreUnknownKeys = true })
        val settingsDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("settings.preferences_pb") }
        )
        settingsRepository = SettingsRepository(settingsDataStore)
        pronunciationAudioPlayer = FakePronunciationAudioPlayer()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun createViewModel() = ReviewViewModel(
        waniKaniRepository, assignmentRepository, reviewSessionRepository, pronunciationAudioPlayer, settingsRepository
    )

    /** Routes by path — refreshing the review queue now syncs subjects and assignments, in either order. */
    private fun dispatch(assignmentsResponse: MockResponse, subjectsResponse: MockResponse, reviewResponse: MockResponse? = null) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    request.method == "POST" && path.startsWith("/reviews") -> reviewResponse ?: jsonResponse(reviewResultJson())
                    path.startsWith("/assignments") -> assignmentsResponse
                    path.startsWith("/subjects") -> subjectsResponse
                    else -> jsonResponse(emptyCollectionJson())
                }
            }
        }
    }

    @Test
    fun `radical item is a single meaning-only question that completes the session when answered correctly`() = runTest {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            assertThat(state.totalCount).isEqualTo(1)
            assertThat(state.currentQuestionType).isEqualTo(QuestionType.MEANING)

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem()
            assertThat(feedbackState.feedback?.isCorrect).isTrue()

            viewModel.onContinue()
            val finalState = awaitItem()
            assertThat(finalState.isSessionComplete).isTrue()
            // Answered correctly first try, and the stale feedback from the last question must not
            // leak into the completed state (it would otherwise keep the swipe-up handle visible).
            assertThat(finalState.feedback).isNull()
            assertThat(finalState.sessionItemsReviewed).isEqualTo(1)
            assertThat(finalState.sessionItemsCorrectFirstTry).isEqualTo(1)
        }
    }

    @Test
    fun `a rank change from completing an item surfaces once and clears on continue`() = runTest {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()), jsonResponse(reviewResultJsonWithRankChange()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.rankChange).isNull()

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            // The rank change arrives asynchronously — submitReview is a separate network call not
            // strictly ordered against the feedback update, so wait until both have landed rather
            // than assuming a fixed number of emissions.
            var settled = awaitItem()
            while (settled.feedback == null || settled.rankChange == null) settled = awaitItem()
            assertThat(settled.feedback?.isCorrect).isTrue()
            assertThat(settled.rankChange).isEqualTo(RankChange(SrsStage.APPRENTICE_3, SrsStage.GURU_1))

            viewModel.onContinue()
            val finalState = awaitItem()
            assertThat(finalState.isSessionComplete).isTrue()
            assertThat(finalState.rankChange).isNull()
        }
    }

    @Test
    fun `an incorrect answer requeues the same question instead of advancing`() = runTest {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.onAnswerInputChange("wrong answer")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem()
            assertThat(feedbackState.feedback?.isCorrect).isFalse()
            assertThat(feedbackState.remainingCount).isEqualTo(1)

            viewModel.onContinue()
            val requeuedState = awaitItem()
            assertThat(requeuedState.isSessionComplete).isFalse()
            assertThat(requeuedState.currentQuestionType).isEqualTo(QuestionType.MEANING)
            assertThat(requeuedState.feedback).isNull()

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val correctState = awaitItem()
            assertThat(correctState.feedback?.isCorrect).isTrue()

            viewModel.onContinue()
            val finalState = awaitItem()
            assertThat(finalState.isSessionComplete).isTrue()
            // Needed a retry, so it doesn't count as correct-on-first-try even though it was
            // eventually answered correctly.
            assertThat(finalState.sessionItemsReviewed).isEqualTo(1)
            assertThat(finalState.sessionItemsCorrectFirstTry).isEqualTo(0)
        }
    }

    @Test
    fun `dontKnowAnswer grades as incorrect, requeues, and expands details`() = runTest {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.isDetailsExpanded).isFalse()

            viewModel.dontKnowAnswer()
            val feedbackState = awaitItem()
            assertThat(feedbackState.feedback?.isCorrect).isFalse()
            assertThat(feedbackState.feedback?.correctAnswer).isEqualTo("Mouth")
            assertThat(feedbackState.isDetailsExpanded).isTrue()
            // Requeued, not dropped — remaining count is unchanged, still one question to answer.
            assertThat(feedbackState.remainingCount).isEqualTo(1)

            viewModel.onContinue()
            val requeuedState = awaitItem()
            assertThat(requeuedState.isSessionComplete).isFalse()

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val correctState = awaitItem()
            assertThat(correctState.feedback?.isCorrect).isTrue()

            viewModel.onContinue()
            val finalState = awaitItem()
            assertThat(finalState.isSessionComplete).isTrue()
        }
    }

    @Test
    fun `dontKnowAnswer does nothing once feedback is already showing`() = runTest {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.dontKnowAnswer()
            val feedbackState = awaitItem()
            assertThat(feedbackState.feedback).isNotNull()

            // A second dontKnowAnswer() while feedback is already showing must be a no-op —
            // otherwise it would silently double-count the miss against the same question.
            viewModel.dontKnowAnswer()
            expectNoEvents()
        }
    }

    @Test
    fun `kanji item requires both meaning and reading before the session completes`() = runTest {
        dispatch(jsonResponse(kanjiAssignmentsJson()), jsonResponse(kanjiSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.totalCount).isEqualTo(2)

            var isComplete = false
            var safetyCounter = 0
            while (!isComplete && safetyCounter < 10) {
                safetyCounter++
                val current = state
                // Reading answers are typed as romaji, same as the real reading field — this
                // exercises RomajiConverter grading, not just literal hiragana comparison.
                val answer = if (current.currentQuestionType == QuestionType.MEANING) "Water" else "mizu"
                viewModel.onAnswerInputChange(answer)
                awaitItem()
                viewModel.submitAnswer()
                awaitItem() // feedback
                viewModel.onContinue()
                state = awaitItem()
                isComplete = state.isSessionComplete
            }

            assertThat(isComplete).isTrue()
        }
    }

    @Test
    fun `answering a reading question autoplays the correct pronunciation when the setting is enabled`() = runTest {
        dispatch(jsonResponse(kanjiAssignmentsJson()), jsonResponse(kanjiSubjectsJson()))

        val viewModel = createViewModel()

        // Real DataStore reads settle asynchronously on their own IO dispatcher, unlike the
        // Main-dispatcher ViewModel coroutines the test rule makes run synchronously — so wait for
        // the play() side effect via the player's own state flow rather than checking playedAudios
        // immediately after the uiState turbine block exits.
        pronunciationAudioPlayer.state.test {
            assertThat(awaitItem()).isEqualTo(PlaybackState.IDLE)

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.isLoading) state = awaitItem()
                while (state.currentQuestionType != QuestionType.READING) {
                    viewModel.onAnswerInputChange(if (state.currentQuestionType == QuestionType.MEANING) "Water" else "mizu")
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

    @Test
    fun `answering a meaning question never autoplays pronunciation audio`() = runTest {
        // A radical only ever produces a single MEANING question (see questionTypesFor) — unlike
        // the kanji fixture used elsewhere, this sidesteps the shuffled queue potentially serving a
        // READING question first, which would legitimately (and racily, since the setting read is
        // real disk IO) autoplay before this test ever gets to the MEANING answer under test.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJsonWithAudio()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.currentQuestionType).isEqualTo(QuestionType.MEANING)

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            awaitItem()
        }

        assertThat(pronunciationAudioPlayer.playedAudios).isEmpty()
    }

    @Test
    fun `autoplay is skipped once the setting is turned off`() = runTest {
        settingsRepository.setAutoplayPronunciationAudio(false)
        dispatch(jsonResponse(kanjiAssignmentsJson()), jsonResponse(kanjiSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            while (state.currentQuestionType != QuestionType.READING) {
                viewModel.onAnswerInputChange(if (state.currentQuestionType == QuestionType.MEANING) "Water" else "mizu")
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

        assertThat(pronunciationAudioPlayer.playedAudios).isEmpty()
    }

    @Test
    fun `undo reverts an incorrect answer so it doesn't count as a miss`() = runTest {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.onAnswerInputChange("typo")
            awaitItem()
            viewModel.submitAnswer()
            val incorrectState = awaitItem()
            assertThat(incorrectState.feedback?.isCorrect).isFalse()

            viewModel.undoLastAnswer()
            val undoneState = awaitItem()
            assertThat(undoneState.feedback).isNull()
            assertThat(undoneState.answerInput).isEmpty()
            // Undo doesn't requeue a duplicate — remaining count is back to exactly one question.
            assertThat(undoneState.remainingCount).isEqualTo(1)

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val correctState = awaitItem()
            assertThat(correctState.feedback?.isCorrect).isTrue()

            viewModel.onContinue()
            val finalState = awaitItem()
            assertThat(finalState.isSessionComplete).isTrue()
        }
    }

    @Test
    fun `abandonSession clears persisted state and marks the session abandoned`() = runTest {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(reviewSessionRepository.load()).isNotNull()

            viewModel.abandonSession()
            var abandonedState = awaitItem()
            while (!abandonedState.isAbandoned) abandonedState = awaitItem()
            assertThat(abandonedState.isAbandoned).isTrue()
        }

        assertThat(reviewSessionRepository.load()).isNull()
    }

    @Test
    fun `a new ViewModel resumes a persisted session instead of refetching from the network`() = runTest {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val firstViewModel = createViewModel()
        firstViewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
        }
        val requestCountAfterFirstLoad = server.requestCount

        // Simulate leaving and coming back: a fresh ViewModel sharing the same repositories
        // should pick the in-progress session back up rather than hitting the network again.
        val secondViewModel = createViewModel()
        secondViewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.totalCount).isEqualTo(1)
            assertThat(state.currentItem?.characters).isEqualTo("口")
        }
        assertThat(server.requestCount).isEqualTo(requestCountAfterFirstLoad)
    }

    @Test
    fun `an empty due queue completes the session immediately with nothing to answer`() = runTest {
        dispatch(jsonResponse(emptyCollectionJson()), jsonResponse(emptyCollectionJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.isSessionComplete).isTrue()
            assertThat(state.totalCount).isEqualTo(0)
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
              "srs_stage": 1, "available_at": "2026-01-01T00:00:00.000000Z", "hidden": false
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
              "readings": []
            }
          }]
        }
    """.trimIndent()

    /** Same fixture as [radicalSubjectsJson] but with a pronunciation clip attached, to prove the
     *  autoplay gate is on question type (MEANING never autoplays) rather than on audio presence. */
    private fun radicalSubjectsJsonWithAudio() = """
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
              "pronunciation_audios": [
                {
                  "url": "https://api.wanikani.com/audio/kuchi.mp3",
                  "content_type": "audio/mpeg",
                  "metadata": {"gender": "female", "pronunciation": "くち"}
                }
              ]
            }
          }]
        }
    """.trimIndent()

    private fun kanjiAssignmentsJson() = """
        {
          "object": "collection", "url": "https://api.wanikani.com/v2/assignments", "total_count": 1,
          "data": [{
            "id": 555, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/555",
            "data_updated_at": "2026-01-01T00:00:00.000000Z",
            "data": {
              "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": 440, "subject_type": "kanji",
              "srs_stage": 3, "available_at": "2026-01-01T00:00:00.000000Z", "hidden": false
            }
          }]
        }
    """.trimIndent()

    private fun kanjiSubjectsJson() = """
        {
          "object": "collection", "url": "https://api.wanikani.com/v2/subjects", "total_count": 1,
          "data": [{
            "id": 440, "object": "kanji", "url": "https://api.wanikani.com/v2/subjects/440",
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

    private fun emptyCollectionJson() = """
        {"object": "collection", "url": "https://api.wanikani.com/v2/x", "total_count": 0, "data": []}
    """.trimIndent()

    /** Same stage in and out — no rank-change emission, so tests unrelated to that feature don't
     *  need to account for an extra uiState update. See [reviewResultJsonWithRankChange] for that. */
    private fun reviewResultJson() = """
        {
          "id": 1, "object": "review", "url": "https://api.wanikani.com/v2/reviews/1",
          "data_updated_at": "2026-01-01T00:00:00.000000Z",
          "data": {
            "assignment_id": 555, "subject_id": 440, "starting_srs_stage": 3, "ending_srs_stage": 3,
            "incorrect_meaning_answers": 0, "incorrect_reading_answers": 0,
            "created_at": "2026-01-01T00:00:00.000000Z"
          }
        }
    """.trimIndent()

    private fun reviewResultJsonWithRankChange() = """
        {
          "id": 1, "object": "review", "url": "https://api.wanikani.com/v2/reviews/1",
          "data_updated_at": "2026-01-01T00:00:00.000000Z",
          "data": {
            "assignment_id": 555, "subject_id": 440, "starting_srs_stage": 3, "ending_srs_stage": 5,
            "incorrect_meaning_answers": 0, "incorrect_reading_answers": 0,
            "created_at": "2026-01-01T00:00:00.000000Z"
          }
        }
    """.trimIndent()
}
