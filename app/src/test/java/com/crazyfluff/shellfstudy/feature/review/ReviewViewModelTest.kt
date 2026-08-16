package com.crazyfluff.shellfstudy.feature.review

import com.crazyfluff.shellfstudy.shared.feature.review.ReviewViewModel
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.shared.data.PlaybackState
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.ReviewSessionRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.model.RankChange
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.lifecycle.AppForegroundTracker
import com.crazyfluff.shellfstudy.shared.quiz.AnswerFeedback
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType
import com.crazyfluff.shellfstudy.fakes.FakeLifecycleOwner
import com.crazyfluff.shellfstudy.fakes.FakePronunciationAudioPlayer
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

class ReviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var assignmentRepository: AssignmentRepository
    private lateinit var outboxRepository: OutboxRepository
    private lateinit var statsRepository: StatsRepository
    private lateinit var reviewSessionRepository: ReviewSessionRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var pronunciationAudioPlayer: FakePronunciationAudioPlayer
    private lateinit var appForegroundTracker: AppForegroundTracker
    private lateinit var repositories: TestRepositories

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repositories = buildTestRepositories(server.url("/").toString())
        assignmentRepository = repositories.assignmentRepository
        statsRepository = repositories.statsRepository
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(mainDispatcherRule.dispatcher + SupervisorJob()),
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        reviewSessionRepository = ReviewSessionRepository(dataStore, Json { ignoreUnknownKeys = true })
        outboxRepository = OutboxRepository(repositories.outboxDao, repositories.outboxSyncScheduler, dataStore)
        val settingsDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(mainDispatcherRule.dispatcher + SupervisorJob()),
            produceFile = { tempFolder.newFile("settings.preferences_pb") }
        )
        settingsRepository = SettingsRepository(settingsDataStore)
        pronunciationAudioPlayer = FakePronunciationAudioPlayer()
        appForegroundTracker = AppForegroundTracker()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun TestScope.createViewModel() = ReviewViewModel(
        assignmentRepository, outboxRepository, statsRepository, reviewSessionRepository, pronunciationAudioPlayer, settingsRepository,
        appForegroundTracker, backgroundScope
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
    fun `kana vocabulary item is meaning-only and completes the session without asking for a reading`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(kanaVocabAssignmentsJson()), jsonResponse(kanaVocabSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            assertThat(state.totalCount).isEqualTo(1)
            assertThat(state.currentQuestionType).isEqualTo(QuestionType.MEANING)

            viewModel.onAnswerInputChange("Rain")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem()
            assertThat(feedbackState.feedback?.isCorrect).isTrue()

            viewModel.onContinue()
            val finalState = awaitItem()
            // If the kana_vocabulary fix is absent, isFullyDone would return false here
            // (requiresReading was true) and the session would not complete.
            assertThat(finalState.isSessionComplete).isTrue()
        }
    }

    @Test
    fun `radical item is a single meaning-only question that completes the session when answered correctly`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `a rank change from completing an item surfaces once and clears on continue`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.rankChange).isNull()

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            // The rank change arrives asynchronously — the optimistic patch runs in its own
            // coroutine, not strictly ordered against the feedback update, so wait until both have
            // landed rather than assuming a fixed number of emissions.
            var settled = awaitItem()
            while (settled.feedback == null || settled.rankChange == null) settled = awaitItem()
            assertThat(settled.feedback?.isCorrect).isTrue()
            // radicalAssignmentsJson fixes the cached assignment at srs_stage 1 (Apprentice I); the
            // optimistic local prediction is one stage up on a correct answer.
            assertThat(settled.rankChange).isEqualTo(RankChange(SrsStage.APPRENTICE_1, SrsStage.APPRENTICE_2))

            viewModel.onContinue()
            val finalState = awaitItem()
            assertThat(finalState.isSessionComplete).isTrue()
            assertThat(finalState.rankChange).isNull()
        }
    }

    @Test
    fun `completing a review durably queues the submission in the outbox instead of calling the network`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            var settled = awaitItem()
            while (settled.feedback == null) settled = awaitItem()
        }

        val queued = repositories.outboxDao.allReviewSubmissions()
        assertThat(queued).hasSize(1)
        assertThat(queued.first().assignmentId).isEqualTo(101L)
        assertThat(queued.first().incorrectMeaningAnswers).isEqualTo(0)
        assertThat(repositories.outboxSyncScheduler.requestCount).isEqualTo(1)
        // No POST /reviews should ever have been made from the ViewModel path — the network call is
        // now exclusively the background sync worker's job.
        assertThat(server.requestCount).isAtMost(2) // just the assignments + subjects sync
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

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            var settled = awaitItem()
            while (settled.feedback == null) settled = awaitItem()

            viewModel.viewModelScope.cancel()
        }

        val queued = repositories.outboxDao.allReviewSubmissions()
        assertThat(queued).hasSize(1)
        assertThat(queued.first().assignmentId).isEqualTo(101L)
        // The session snapshot persisted at grading time is durability bookkeeping too, and must
        // equally survive the cancellation — this item's progress must be recorded, not lost.
        assertThat(reviewSessionRepository.load()?.progress?.single()?.meaningDone).isTrue()
    }

    @Test
    fun `an incorrect answer requeues the same question instead of advancing`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `submitting a reading into a meaning question rejects it instead of grading a miss`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.currentQuestionType).isEqualTo(QuestionType.MEANING)

            viewModel.onAnswerInputChange("くち")
            awaitItem()
            viewModel.submitAnswer()
            val mismatchState = awaitItem()
            assertThat(mismatchState.answerTypeMismatchCount).isEqualTo(1)
            // Rejected outright, not graded as a miss — feedback stays null and the question isn't
            // consumed (remainingCount unchanged, no requeue).
            assertThat(mismatchState.feedback).isNull()
            assertThat(mismatchState.remainingCount).isEqualTo(1)

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
            // Queue order is shuffled — answer reading questions correctly until meaning comes up.
            while (state.currentQuestionType != QuestionType.MEANING) {
                viewModel.onAnswerInputChange("mizu")
                awaitItem()
                viewModel.submitAnswer()
                awaitItem()
                viewModel.onContinue()
                state = awaitItem()
            }
            val remainingBeforeMismatch = state.remainingCount

            viewModel.onAnswerInputChange("mizu")
            awaitItem()
            viewModel.submitAnswer()
            val mismatchState = awaitItem()
            assertThat(mismatchState.answerTypeMismatchCount).isEqualTo(1)
            // Rejected outright, not graded as a miss — feedback stays null and the question isn't
            // consumed (remainingCount unchanged, no requeue).
            assertThat(mismatchState.feedback).isNull()
            assertThat(mismatchState.remainingCount).isEqualTo(remainingBeforeMismatch)

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
            val remainingBeforeMismatch = state.remainingCount

            viewModel.onAnswerInputChange("Water")
            awaitItem()
            viewModel.submitAnswer()
            val mismatchState = awaitItem()
            assertThat(mismatchState.answerTypeMismatchCount).isEqualTo(1)
            assertThat(mismatchState.feedback).isNull()
            // Rejected outright, not graded as a miss — the queue is untouched.
            assertThat(mismatchState.remainingCount).isEqualTo(remainingBeforeMismatch)

            viewModel.onAnswerInputChange("mizu")
            awaitItem()
            viewModel.submitAnswer()
            val correctState = awaitItem()
            assertThat(correctState.feedback?.isCorrect).isTrue()
        }
    }

    @Test
    fun `dontKnowAnswer grades as incorrect and requeues, without auto-expanding details`() = runTest(mainDispatcherRule.dispatcher) {
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
            // "I don't know" shouldn't force the detail sheet open — same as a regular wrong answer.
            assertThat(feedbackState.isDetailsExpanded).isFalse()
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
    fun `toggleDetails flips both ways, closeDetails always ends up false`() = runTest(mainDispatcherRule.dispatcher) {
        // closeDetails is the definitively-directional close used by SubjectDetailSheet's scrim
        // tap, close button, and back handler — those must never risk re-opening the sheet, unlike
        // toggleDetails (the swipe handle's real flip). Regression coverage for that distinction.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.isDetailsExpanded).isFalse()

            viewModel.toggleDetails()
            assertThat(awaitItem().isDetailsExpanded).isTrue()

            viewModel.toggleDetails()
            assertThat(awaitItem().isDetailsExpanded).isFalse()

            viewModel.closeDetails()
            // Already false — closeDetails is idempotent, not a toggle, so this must not flip it
            // back to true.
            expectNoEvents()
        }
    }

    @Test
    fun `dontKnowAnswer does nothing once feedback is already showing`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `kanji item requires both meaning and reading before the session completes`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `answering a reading question autoplays the correct pronunciation when the setting is enabled`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `answering a meaning question never autoplays pronunciation audio`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `autoplay is skipped once the setting is turned off`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `autoplay is skipped when restrictAudioToMp3 is enabled and only an ogg clip exists`() = runTest(mainDispatcherRule.dispatcher) {
        settingsRepository.setRestrictAudioToMp3(true)
        dispatch(jsonResponse(kanjiAssignmentsJson()), jsonResponse(kanjiSubjectsJsonWithOggOnlyAudio()))

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
    fun `undo reverts an incorrect answer so it doesn't count as a miss`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `abandonSession clears persisted state and marks the session abandoned`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `a new ViewModel resumes a persisted session instead of refetching from the network`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `showSubjectTypeLabel, showTotalTimer, and showQuestionTimer settings flow into uiState`() = runTest(mainDispatcherRule.dispatcher) {
        settingsRepository.setShowSubjectTypeLabel(true)
        settingsRepository.setShowTotalTimer(true)
        settingsRepository.setShowQuestionTimer(true)
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading || !state.showSubjectTypeLabel || !state.showTotalTimer || !state.showQuestionTimer) state = awaitItem()
            assertThat(state.showSubjectTypeLabel).isTrue()
            assertThat(state.showTotalTimer).isTrue()
            assertThat(state.showQuestionTimer).isTrue()
        }
    }

    @Test
    fun `session summary reports missed items, slowest answers capped at five, and non-negative timing`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(kanjiAssignmentsJson()), jsonResponse(kanjiSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.sessionActiveSegmentStartMs).isNotNull()
            assertThat(state.questionStartTimeMs).isNotNull()

            // Miss the first question drawn (whichever type it is), then work through both question
            // types until the session completes.
            viewModel.onAnswerInputChange("wrong")
            awaitItem()
            viewModel.submitAnswer()
            val missedState = awaitItem()
            assertThat(missedState.feedback?.isCorrect).isFalse()

            viewModel.onContinue()
            state = awaitItem()

            var isComplete = false
            var safetyCounter = 0
            while (!isComplete && safetyCounter < 10) {
                safetyCounter++
                val answer = if (state.currentQuestionType == QuestionType.MEANING) "Water" else "mizu"
                viewModel.onAnswerInputChange(answer)
                awaitItem()
                viewModel.submitAnswer()
                awaitItem()
                viewModel.onContinue()
                state = awaitItem()
                isComplete = state.isSessionComplete
            }

            assertThat(state.isSessionComplete).isTrue()
            assertThat(state.sessionMissedItems).hasSize(1)
            assertThat(state.sessionMissedItems.first().characters).isEqualTo("水")
            assertThat(state.sessionSlowestAnswers).isNotEmpty()
            assertThat(state.sessionSlowestAnswers.size).isAtMost(5)
            val elapsedTimes = state.sessionSlowestAnswers.map { it.elapsedMs }
            assertThat(elapsedTimes).isEqualTo(elapsedTimes.sortedDescending())
            assertThat(state.sessionTotalElapsedMs).isAtLeast(0L)
            assertThat(state.sessionAverageTimePerItemMs).isAtLeast(0L)
        }
    }

    @Test
    fun `submitting an answer freezes questionElapsedMs, and advancing to the next question resets it`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(kanjiAssignmentsJson()), jsonResponse(kanjiSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.questionElapsedMs).isNull()

            val answer = if (state.currentQuestionType == QuestionType.MEANING) "Water" else "mizu"
            viewModel.onAnswerInputChange(answer)
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem()
            assertThat(feedbackState.questionElapsedMs).isNotNull()

            viewModel.onContinue()
            val nextState = awaitItem()
            assertThat(nextState.questionElapsedMs).isNull()
        }
    }

    @Test
    fun `undo clears the frozen questionElapsedMs`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.onAnswerInputChange("typo")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem()
            assertThat(feedbackState.questionElapsedMs).isNotNull()

            viewModel.undoLastAnswer()
            val undoneState = awaitItem()
            assertThat(undoneState.questionElapsedMs).isNull()
        }
    }

    @Test
    fun `undo removes the just-recorded incorrect answer from session timing`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.onAnswerInputChange("typo")
            awaitItem()
            viewModel.submitAnswer()
            awaitItem()

            viewModel.undoLastAnswer()
            awaitItem()

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            awaitItem()

            viewModel.onContinue()
            val finalState = awaitItem()
            assertThat(finalState.isSessionComplete).isTrue()
            // The undone incorrect attempt shouldn't be double-counted — first-try accuracy is
            // unaffected and only one item was ever reviewed.
            assertThat(finalState.sessionItemsReviewed).isEqualTo(1)
            assertThat(finalState.sessionItemsCorrectFirstTry).isEqualTo(1)
            assertThat(finalState.sessionMissedItems).isEmpty()
        }
    }

    @Test
    fun `resuming after fully completing one item of several preserves progress on the rest instead of resetting the whole session`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression test: completing the radical here pushes its next-review time into the future
        // via applyOptimisticReviewResult, dropping it out of the due queue even though its
        // (completed) progress is still persisted. resumeFromPersisted must not treat that as a
        // reason to discard the *entire* persisted session (queue + still-in-progress kanji
        // progress) and silently fall back to a fresh fetch — it should only ever fall back when a
        // *queue* entry (not a stray progress entry) can't be resolved. totalCount is the
        // observable proof: it's fixed at session start (1 radical question + 2 kanji questions =
        // 3) and never recomputed as items complete, so a fresh-fetch fallback would report 2
        // (only the still-due kanji) instead of the true 3.
        dispatch(jsonResponse(twoItemAssignmentsJson()), jsonResponse(twoItemSubjectsJson()))

        val firstViewModel = createViewModel()
        firstViewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            var radicalCompleted = false
            var safetyCounter = 0
            while (!radicalCompleted && safetyCounter < 10) {
                safetyCounter++
                if (state.currentItem?.assignmentId == 101L) {
                    // The radical — a single meaning question; answering it correctly completes it.
                    firstViewModel.onAnswerInputChange("Mouth")
                    awaitItem()
                    firstViewModel.submitAnswer()
                    awaitItem()
                    radicalCompleted = true
                } else {
                    // The kanji — answer wrong so it's requeued and never completes, keeping the
                    // session (and the resumed one below) meaningfully in-progress.
                    firstViewModel.dontKnowAnswer()
                    awaitItem()
                }
                firstViewModel.onContinue()
                state = awaitItem()
            }

            assertThat(radicalCompleted).isTrue()
            assertThat(state.isSessionComplete).isFalse()
        }

        // Simulate leaving and coming back: a fresh ViewModel must resume the original session —
        // not silently reset it — even though assignment 101's completed progress is no longer in
        // the due queue.
        val requestCountBeforeResume = server.requestCount
        val secondViewModel = createViewModel()
        secondViewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.errorMessage).isNull()
            assertThat(state.isSessionComplete).isFalse()
            assertThat(state.currentItem?.assignmentId).isEqualTo(555L)
            // The real assertion: totalCount must still reflect the original 3-question session
            // (1 radical + 2 kanji), not a recomputed 2 (only the kanji still due) — which is what
            // a silent fetchFreshQueue() fallback would produce.
            assertThat(state.totalCount).isEqualTo(3)
        }
        // No fresh sync should have been needed either — the persisted queue was reused as-is.
        assertThat(server.requestCount).isEqualTo(requestCountBeforeResume)
    }

    @Test
    fun `an empty due queue completes the session immediately with nothing to answer`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(emptyCollectionJson()), jsonResponse(emptyCollectionJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.isSessionComplete).isTrue()
            assertThat(state.totalCount).isEqualTo(0)
        }
    }

    @Test
    fun `resuming a persisted session preserves the accumulated active time instead of resetting it to zero`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression test: resumeFromPersisted used to derive elapsed time from an absolute session
        // start timestamp restored across resumes, which counted 100% of time spent away
        // (backgrounded, or navigated off and back) as if it were active review time. It should
        // instead carry over only the accumulated *active* time — proven with a fake, unmistakably
        // large value rather than comparing real wall-clock reads, since this whole test executes
        // in well under a second.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val firstViewModel = createViewModel()
        firstViewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
        }

        val fakeAccumulatedElapsedMs = 1_000_000L
        val persisted = reviewSessionRepository.load()!!
        reviewSessionRepository.save(persisted.copy(sessionActiveElapsedMs = fakeAccumulatedElapsedMs))

        val secondViewModel = createViewModel()
        secondViewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.sessionActiveElapsedMs).isEqualTo(fakeAccumulatedElapsedMs)
            assertThat(state.sessionActiveSegmentStartMs).isNotNull()

            // Forces a fresh persisted snapshot so the resumed accumulated time can be inspected.
            secondViewModel.onAnswerInputChange("wrong")
            awaitItem()
            secondViewModel.submitAnswer()
            awaitItem()
        }

        val resumedSnapshot = reviewSessionRepository.load()
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
            assertThat(state.sessionActiveSegmentStartMs).isNotNull()

            appForegroundTracker.onStop(FakeLifecycleOwner)
            val pausedState = awaitItem()
            assertThat(pausedState.sessionActiveSegmentStartMs).isNull()
            val elapsedWhilePaused = pausedState.sessionActiveElapsedMs

            appForegroundTracker.onStart(FakeLifecycleOwner)
            val resumedState = awaitItem()
            assertThat(resumedState.sessionActiveSegmentStartMs).isNotNull()
            // Resumes right where it left off — the time spent "away" (backgrounded) must not have
            // been folded in as if it were active review time.
            assertThat(resumedState.sessionActiveElapsedMs).isEqualTo(elapsedWhilePaused)
        }
    }

    @Test
    fun `backgrounding the app after completing a session does not resurrect a resumable session`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression test: pauseActiveSegment (triggered by the app backgrounding, or by this
        // ViewModel being cleared when the user navigates off the complete screen) used to
        // unconditionally re-persist a session snapshot even after advanceToNextQuestion had
        // already cleared the repository on completion — resurrecting a stale, empty-queue
        // "active session" record. The dashboard would then offer to resume a 0-review session
        // that, once opened, immediately re-completed.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem()
            assertThat(feedbackState.feedback?.isCorrect).isTrue()

            viewModel.onContinue()
            val finalState = awaitItem()
            assertThat(finalState.isSessionComplete).isTrue()
            assertThat(reviewSessionRepository.load()).isNull()

            appForegroundTracker.onStop(FakeLifecycleOwner)
            val pausedState = awaitItem()
            assertThat(pausedState.isSessionComplete).isTrue()
        }

        assertThat(reviewSessionRepository.load()).isNull()
    }

    @Test
    fun `wrapUp only counts items with actual progress in the final summary, not untouched ones it drops from the queue`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression test: sessionSummary() used to read every entry in progressByAssignmentId,
        // which is seeded for the whole original queue up front (see buildQueue) — after wrapUp()
        // drops never-attempted items from the queue, their still-present-but-untouched entries were
        // still being counted as "reviewed", inflating sessionItemsReviewed and, in turn,
        // understating sessionAverageTimePerItemMs.
        dispatch(jsonResponse(threeRadicalAssignmentsJson()), jsonResponse(threeRadicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            // Fully complete one item before wrapping up.
            viewModel.onAnswerInputChange(state.currentItem!!.meanings.first())
            awaitItem()
            viewModel.submitAnswer()
            awaitItem()
            viewModel.onContinue()
            state = awaitItem()

            // Two items remain, both still completely untouched. wrapUp() retains only whichever is
            // now "current" and drops the other outright.
            viewModel.wrapUp()
            val wrappedState = awaitItem()
            assertThat(wrappedState.totalCount).isEqualTo(2)
            assertThat(wrappedState.remainingCount).isEqualTo(1)

            // Finish the one retained item.
            viewModel.onAnswerInputChange(wrappedState.currentItem!!.meanings.first())
            awaitItem()
            viewModel.submitAnswer()
            awaitItem()
            viewModel.onContinue()
            val finalState = awaitItem()

            assertThat(finalState.isSessionComplete).isTrue()
            // Exactly the two items actually answered — not the third, dropped-while-untouched one.
            assertThat(finalState.sessionItemsReviewed).isEqualTo(2)
            assertThat(finalState.sessionItemsCorrectFirstTry).isEqualTo(2)
            assertThat(finalState.sessionMissedItems).isEmpty()
        }
    }

    private fun threeRadicalAssignmentsJson() = """
        {
          "object": "collection", "url": "https://api.wanikani.com/v2/assignments", "total_count": 3,
          "data": [
            {
              "id": 101, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/101",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": 1, "subject_type": "radical",
                "srs_stage": 1, "available_at": "2026-01-01T00:00:00.000000Z", "hidden": false
              }
            },
            {
              "id": 102, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/102",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": 2, "subject_type": "radical",
                "srs_stage": 1, "available_at": "2026-01-01T00:00:00.000000Z", "hidden": false
              }
            },
            {
              "id": 103, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/103",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": 3, "subject_type": "radical",
                "srs_stage": 1, "available_at": "2026-01-01T00:00:00.000000Z", "hidden": false
              }
            }
          ]
        }
    """.trimIndent()

    private fun threeRadicalSubjectsJson() = """
        {
          "object": "collection", "url": "https://api.wanikani.com/v2/subjects", "total_count": 3,
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
            },
            {
              "id": 3, "object": "radical", "url": "https://api.wanikani.com/v2/subjects/3",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2020-01-01T00:00:00.000000Z", "level": 1, "slug": "tree",
                "characters": "木",
                "meanings": [{"meaning": "Tree", "primary": true, "accepted_meaning": true}],
                "readings": []
              }
            }
          ]
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

    private fun twoItemAssignmentsJson() = """
        {
          "object": "collection", "url": "https://api.wanikani.com/v2/assignments", "total_count": 2,
          "data": [
            {
              "id": 101, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/101",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": 1, "subject_type": "radical",
                "srs_stage": 1, "available_at": "2026-01-01T00:00:00.000000Z", "hidden": false
              }
            },
            {
              "id": 555, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/555",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": 440, "subject_type": "kanji",
                "srs_stage": 3, "available_at": "2026-01-01T00:00:00.000000Z", "hidden": false
              }
            }
          ]
        }
    """.trimIndent()

    private fun twoItemSubjectsJson() = """
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
              "id": 440, "object": "kanji", "url": "https://api.wanikani.com/v2/subjects/440",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2020-01-01T00:00:00.000000Z", "level": 3, "slug": "water",
                "characters": "水",
                "meanings": [{"meaning": "Water", "primary": true, "accepted_meaning": true}],
                "readings": [{"reading": "みず", "primary": true, "accepted_reading": true}]
              }
            }
          ]
        }
    """.trimIndent()

    private fun kanjiSubjectsJsonWithOggOnlyAudio() = """
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
                  "url": "https://api.wanikani.com/audio/mizu.ogg",
                  "content_type": "audio/ogg",
                  "metadata": {"gender": "female", "pronunciation": "みず"}
                }
              ]
            }
          }]
        }
    """.trimIndent()

    private fun kanaVocabAssignmentsJson() = """
        {
          "object": "collection", "url": "https://api.wanikani.com/v2/assignments", "total_count": 1,
          "data": [{
            "id": 202, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/202",
            "data_updated_at": "2026-01-01T00:00:00.000000Z",
            "data": {
              "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": 9001, "subject_type": "kana_vocabulary",
              "srs_stage": 2, "available_at": "2026-01-01T00:00:00.000000Z", "hidden": false
            }
          }]
        }
    """.trimIndent()

    private fun kanaVocabSubjectsJson() = """
        {
          "object": "collection", "url": "https://api.wanikani.com/v2/subjects", "total_count": 1,
          "data": [{
            "id": 9001, "object": "kana_vocabulary", "url": "https://api.wanikani.com/v2/subjects/9001",
            "data_updated_at": "2026-01-01T00:00:00.000000Z",
            "data": {
              "created_at": "2020-01-01T00:00:00.000000Z", "level": 1, "slug": "rain",
              "characters": "あめ",
              "meanings": [{"meaning": "Rain", "primary": true, "accepted_meaning": true}],
              "readings": []
            }
          }]
        }
    """.trimIndent()

    private fun emptyCollectionJson() = """
        {"object": "collection", "url": "https://api.wanikani.com/v2/x", "total_count": 0, "data": []}
    """.trimIndent()

    /** The ViewModel no longer calls POST /reviews at all (that's the background sync worker's
     *  job), so this response is never actually consumed — it just needs to exist as the
     *  dispatcher's fallback branch for that path. */
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
}
