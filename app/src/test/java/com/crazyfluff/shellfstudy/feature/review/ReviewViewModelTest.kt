package com.crazyfluff.shellfstudy.feature.review

import com.crazyfluff.shellfstudy.shared.feature.review.ReviewUiState
import com.crazyfluff.shellfstudy.shared.feature.review.ReviewViewModel
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.shared.data.PlaybackState
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.LastSessionKind
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.ReviewSessionRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.model.RankChange
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.lifecycle.AppForegroundTracker
import com.crazyfluff.shellfstudy.shared.quiz.AnswerFeedback
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType
import com.crazyfluff.shellfstudy.shared.session.QuizSessionController
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
    private lateinit var lastSessionSummaryRepository: LastSessionSummaryRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var pronunciationAudioPlayer: FakePronunciationAudioPlayer
    private lateinit var appForegroundTracker: AppForegroundTracker
    private lateinit var repositories: TestRepositories

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repositories = buildTestRepositories(server.url("/").toString(), defaultDispatcher = mainDispatcherRule.dispatcher)
        assignmentRepository = repositories.assignmentRepository
        statsRepository = repositories.statsRepository
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(mainDispatcherRule.dispatcher + SupervisorJob()),
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        reviewSessionRepository = ReviewSessionRepository(dataStore, Json { ignoreUnknownKeys = true })
        lastSessionSummaryRepository = LastSessionSummaryRepository(dataStore, Json { ignoreUnknownKeys = true })
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
        assignmentRepository, outboxRepository, statsRepository,
        QuizSessionController(backgroundScope, reviewSessionRepository), lastSessionSummaryRepository,
        pronunciationAudioPlayer, settingsRepository, appForegroundTracker, backgroundScope
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
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            assertThat((state.phase as ReviewUiState.Phase.Active).totalCount).isEqualTo(1)
            assertThat((state.phase as ReviewUiState.Phase.Active).currentQuestionType).isEqualTo(QuestionType.MEANING)

            viewModel.onAnswerInputChange("Rain")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem()
            assertThat((feedbackState.phase as ReviewUiState.Phase.Active).feedback?.isCorrect).isTrue()

            viewModel.onContinue()
            val finalState = awaitItem()
            // If the kana_vocabulary fix is absent, isFullyDone would return false here
            // (requiresReading was true) and the session would not complete.
            assertThat((finalState.phase is ReviewUiState.Phase.Complete)).isTrue()
        }
    }

    @Test
    fun `radical item is a single meaning-only question that completes the session when answered correctly`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            assertThat((state.phase as ReviewUiState.Phase.Active).totalCount).isEqualTo(1)
            assertThat((state.phase as ReviewUiState.Phase.Active).currentQuestionType).isEqualTo(QuestionType.MEANING)

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem()
            assertThat((feedbackState.phase as ReviewUiState.Phase.Active).feedback?.isCorrect).isTrue()

            viewModel.onContinue()
            val finalState = awaitItem()
            // Answered correctly first try, and the stale feedback from the last question can't leak
            // into the completed state (it would otherwise keep the swipe-up handle visible) — that's
            // now a structural guarantee rather than something to assert at runtime, since
            // Phase.Complete has no feedback field to leak into in the first place.
            val complete = finalState.phase as ReviewUiState.Phase.Complete
            assertThat(complete.sessionItemsReviewed).isEqualTo(1)
            assertThat(complete.sessionItemsCorrectFirstTry).isEqualTo(1)
        }
        // Session completion should flush the outbox immediately rather than waiting out the
        // per-answer debounce, so the dashboard's pending-sync count doesn't look stale.
        assertThat(repositories.outboxSyncScheduler.immediateRequestCount).isEqualTo(1)

        // Completing a session snapshots its summary so it can be revisited later from the dashboard.
        val savedSummary = lastSessionSummaryRepository.loadReview()
        assertThat(savedSummary).isNotNull()
        assertThat(savedSummary!!.kind).isEqualTo(LastSessionKind.REVIEW)
        assertThat(savedSummary.itemsCount).isEqualTo(1)
        assertThat(savedSummary.correctFirstTry).isEqualTo(1)
    }

    @Test
    fun `a rank change from completing an item surfaces once and clears on continue`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            assertThat((state.phase as ReviewUiState.Phase.Active).rankChange).isNull()

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            // The rank change arrives asynchronously — the optimistic patch runs in its own
            // coroutine, not strictly ordered against the feedback update, so wait until both have
            // landed rather than assuming a fixed number of emissions.
            var settled = awaitItem()
            while ((settled.phase as ReviewUiState.Phase.Active).feedback == null || (settled.phase as ReviewUiState.Phase.Active).rankChange == null) settled = awaitItem()
            assertThat((settled.phase as ReviewUiState.Phase.Active).feedback?.isCorrect).isTrue()
            // radicalAssignmentsJson fixes the cached assignment at srs_stage 1 (Apprentice I); the
            // optimistic local prediction is one stage up on a correct answer.
            assertThat((settled.phase as ReviewUiState.Phase.Active).rankChange).isEqualTo(RankChange(SrsStage.APPRENTICE_1, SrsStage.APPRENTICE_2))

            viewModel.onContinue()
            val finalState = awaitItem()
            // The rank change (an Active-only concept) can't survive into the completed state —
            // that's a structural guarantee now, since Phase.Complete has no rankChange field at all,
            // rather than something to assert by reading a field back as null.
            assertThat(finalState.phase).isInstanceOf(ReviewUiState.Phase.Complete::class.java)
        }
    }

    @Test
    fun `completing a review durably queues the submission in the outbox instead of calling the network`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            var settled = awaitItem()
            while ((settled.phase as ReviewUiState.Phase.Active).feedback == null) settled = awaitItem()

            // Submission to WaniKani is deferred until Continue is pressed (see
            // ReviewViewModel.pendingSubmissionAssignmentId) so an undo can still retract it —
            // nothing should be queued yet.
            assertThat(repositories.outboxDao.allReviewSubmissions()).isEmpty()

            viewModel.onContinue()
            awaitItem()
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
    fun `clearing the ViewModel immediately after grading does not lose the pending state`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression test for durability writes (session snapshot, including any pending
        // submission) being parented to an application-scoped CoroutineScope instead of
        // viewModelScope: a rushed back-press clears the ViewModel (cancelling viewModelScope) the
        // instant feedback is shown, and that must not be able to cancel the write.
        // viewModelScope.cancel() here simulates exactly what ViewModel.clear() does to
        // viewModelScope when the screen is left. Submission to WaniKani itself is deferred until
        // Continue (see ReviewViewModel.pendingSubmissionAssignmentId), so the outbox must stay
        // empty here regardless — what must survive is the *snapshot* recording that a submission
        // is pending, so a later resume can still commit it instead of silently dropping it.
        // Uses the two-item queue (rather than the single-item radical fixture) so grading the
        // first question doesn't complete the whole session — that path clears currentItem/
        // currentQuestionType, which this test also reads from uiState.
        dispatch(jsonResponse(twoItemAssignmentsJson()), jsonResponse(twoItemSubjectsJson()))

        val viewModel = createViewModel()

        var gradedItemId = -1L
        var gradedType = QuestionType.MEANING
        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            val active = state.phase as ReviewUiState.Phase.Active
            gradedItemId = active.currentItem.assignmentId
            gradedType = active.currentQuestionType
            val answer = when {
                gradedItemId == 101L -> "Mouth"
                gradedType == QuestionType.MEANING -> "Water"
                else -> "mizu"
            }
            viewModel.onAnswerInputChange(answer)
            awaitItem()
            viewModel.submitAnswer()
            var settled = awaitItem()
            while ((settled.phase as ReviewUiState.Phase.Active).feedback == null) settled = awaitItem()

            viewModel.viewModelScope.cancel()
        }

        // Submission is always deferred to Continue now, regardless of which item was graded.
        assertThat(repositories.outboxDao.allReviewSubmissions()).isEmpty()

        // The session snapshot persisted at grading time is durability bookkeeping too, and must
        // equally survive the cancellation — this item's progress must be recorded, not lost.
        val persisted = reviewSessionRepository.load()
        val progress = persisted?.progress?.firstOrNull { it.assignmentId == gradedItemId }
        assertThat(progress).isNotNull()
        if (gradedType == QuestionType.MEANING) {
            assertThat(progress!!.meaningDone).isTrue()
        } else {
            assertThat(progress!!.readingDone).isTrue()
        }
        // The radical (101) is meaning-only, so answering it correctly completes it in one shot,
        // recording it as a pending submission; the kanji (555) needs both meaning and reading, so
        // answering just one doesn't complete it yet and leaves nothing pending.
        if (gradedItemId == 101L) {
            assertThat(persisted!!.pendingSubmissionAssignmentId).isEqualTo(101L)
        } else {
            assertThat(persisted!!.pendingSubmissionAssignmentId).isNull()
        }
    }

    @Test
    fun `an incorrect answer requeues the same question instead of advancing`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            viewModel.onAnswerInputChange("wrong answer")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem()
            assertThat((feedbackState.phase as ReviewUiState.Phase.Active).feedback?.isCorrect).isFalse()
            assertThat((feedbackState.phase as ReviewUiState.Phase.Active).remainingCount).isEqualTo(1)

            viewModel.onContinue()
            val requeuedState = awaitItem()
            assertThat((requeuedState.phase is ReviewUiState.Phase.Complete)).isFalse()
            assertThat((requeuedState.phase as ReviewUiState.Phase.Active).currentQuestionType).isEqualTo(QuestionType.MEANING)
            assertThat((requeuedState.phase as ReviewUiState.Phase.Active).feedback).isNull()

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val correctState = awaitItem()
            assertThat((correctState.phase as ReviewUiState.Phase.Active).feedback?.isCorrect).isTrue()

            viewModel.onContinue()
            val finalState = awaitItem()
            assertThat((finalState.phase is ReviewUiState.Phase.Complete)).isTrue()
            // Needed a retry, so it doesn't count as correct-on-first-try even though it was
            // eventually answered correctly.
            assertThat((finalState.phase as ReviewUiState.Phase.Complete).sessionItemsReviewed).isEqualTo(1)
            assertThat((finalState.phase as ReviewUiState.Phase.Complete).sessionItemsCorrectFirstTry).isEqualTo(0)
        }
    }

    @Test
    fun `submitting a reading into a meaning question rejects it instead of grading a miss`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            assertThat((state.phase as ReviewUiState.Phase.Active).currentQuestionType).isEqualTo(QuestionType.MEANING)

            viewModel.onAnswerInputChange("くち")
            awaitItem()
            viewModel.submitAnswer()
            val mismatchState = awaitItem()
            assertThat((mismatchState.phase as ReviewUiState.Phase.Active).answerTypeMismatchCount).isEqualTo(1)
            // Rejected outright, not graded as a miss — feedback stays null and the question isn't
            // consumed (remainingCount unchanged, no requeue).
            assertThat((mismatchState.phase as ReviewUiState.Phase.Active).feedback).isNull()
            assertThat((mismatchState.phase as ReviewUiState.Phase.Active).remainingCount).isEqualTo(1)

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val correctState = awaitItem()
            assertThat((correctState.phase as ReviewUiState.Phase.Active).feedback?.isCorrect).isTrue()
        }
    }

    @Test
    fun `submitting a romaji reading into a meaning question rejects it instead of grading a miss`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(kanjiAssignmentsJson()), jsonResponse(kanjiSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            // Queue order is shuffled — answer reading questions correctly until meaning comes up.
            while ((state.phase as ReviewUiState.Phase.Active).currentQuestionType != QuestionType.MEANING) {
                viewModel.onAnswerInputChange("mizu")
                awaitItem()
                viewModel.submitAnswer()
                awaitItem()
                viewModel.onContinue()
                state = awaitItem()
            }
            val remainingBeforeMismatch = (state.phase as ReviewUiState.Phase.Active).remainingCount

            viewModel.onAnswerInputChange("mizu")
            awaitItem()
            viewModel.submitAnswer()
            val mismatchState = awaitItem()
            assertThat((mismatchState.phase as ReviewUiState.Phase.Active).answerTypeMismatchCount).isEqualTo(1)
            // Rejected outright, not graded as a miss — feedback stays null and the question isn't
            // consumed (remainingCount unchanged, no requeue).
            assertThat((mismatchState.phase as ReviewUiState.Phase.Active).feedback).isNull()
            assertThat((mismatchState.phase as ReviewUiState.Phase.Active).remainingCount).isEqualTo(remainingBeforeMismatch)

            viewModel.onAnswerInputChange("Water")
            awaitItem()
            viewModel.submitAnswer()
            val correctState = awaitItem()
            assertThat((correctState.phase as ReviewUiState.Phase.Active).feedback?.isCorrect).isTrue()
        }
    }

    @Test
    fun `submitting a meaning into a reading question rejects it instead of grading a miss`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(kanjiAssignmentsJson()), jsonResponse(kanjiSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            // Queue order is shuffled — answer meaning questions correctly until reading comes up.
            while ((state.phase as ReviewUiState.Phase.Active).currentQuestionType != QuestionType.READING) {
                viewModel.onAnswerInputChange("Water")
                awaitItem()
                viewModel.submitAnswer()
                awaitItem()
                viewModel.onContinue()
                state = awaitItem()
            }
            // Captured before the mismatch submission — if the reading question happened to be
            // drawn first, the meaning question is still outstanding, so this is 2, not 1.
            val remainingBeforeMismatch = (state.phase as ReviewUiState.Phase.Active).remainingCount

            viewModel.onAnswerInputChange("Water")
            awaitItem()
            viewModel.submitAnswer()
            val mismatchState = awaitItem()
            assertThat((mismatchState.phase as ReviewUiState.Phase.Active).answerTypeMismatchCount).isEqualTo(1)
            assertThat((mismatchState.phase as ReviewUiState.Phase.Active).feedback).isNull()
            // Rejected outright, not graded as a miss — the queue is untouched.
            assertThat((mismatchState.phase as ReviewUiState.Phase.Active).remainingCount).isEqualTo(remainingBeforeMismatch)

            viewModel.onAnswerInputChange("mizu")
            awaitItem()
            viewModel.submitAnswer()
            val correctState = awaitItem()
            assertThat((correctState.phase as ReviewUiState.Phase.Active).feedback?.isCorrect).isTrue()
        }
    }

    @Test
    fun `dontKnowAnswer grades as incorrect and requeues, without auto-expanding details`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            assertThat((state.phase as ReviewUiState.Phase.Active).isDetailsExpanded).isFalse()

            viewModel.dontKnowAnswer()
            val feedbackState = awaitItem()
            assertThat((feedbackState.phase as ReviewUiState.Phase.Active).feedback?.isCorrect).isFalse()
            assertThat((feedbackState.phase as ReviewUiState.Phase.Active).feedback?.correctAnswer).isEqualTo("Mouth")
            // "I don't know" shouldn't force the detail sheet open — same as a regular wrong answer.
            assertThat((feedbackState.phase as ReviewUiState.Phase.Active).isDetailsExpanded).isFalse()
            // Requeued, not dropped — remaining count is unchanged, still one question to answer.
            assertThat((feedbackState.phase as ReviewUiState.Phase.Active).remainingCount).isEqualTo(1)

            viewModel.onContinue()
            val requeuedState = awaitItem()
            assertThat((requeuedState.phase is ReviewUiState.Phase.Complete)).isFalse()

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val correctState = awaitItem()
            assertThat((correctState.phase as ReviewUiState.Phase.Active).feedback?.isCorrect).isTrue()

            viewModel.onContinue()
            val finalState = awaitItem()
            assertThat((finalState.phase is ReviewUiState.Phase.Complete)).isTrue()
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
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            assertThat((state.phase as ReviewUiState.Phase.Active).isDetailsExpanded).isFalse()

            viewModel.toggleDetails()
            assertThat((awaitItem().phase as ReviewUiState.Phase.Active).isDetailsExpanded).isTrue()

            viewModel.toggleDetails()
            assertThat((awaitItem().phase as ReviewUiState.Phase.Active).isDetailsExpanded).isFalse()

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
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            viewModel.dontKnowAnswer()
            val feedbackState = awaitItem()
            assertThat((feedbackState.phase as ReviewUiState.Phase.Active).feedback).isNotNull()

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
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            assertThat((state.phase as ReviewUiState.Phase.Active).totalCount).isEqualTo(2)

            var isComplete = false
            var safetyCounter = 0
            while (!isComplete && safetyCounter < 10) {
                safetyCounter++
                val current = state
                // Reading answers are typed as romaji, same as the real reading field — this
                // exercises RomajiConverter grading, not just literal hiragana comparison.
                val answer = if ((current.phase as ReviewUiState.Phase.Active).currentQuestionType == QuestionType.MEANING) "Water" else "mizu"
                viewModel.onAnswerInputChange(answer)
                awaitItem()
                viewModel.submitAnswer()
                awaitItem() // feedback
                viewModel.onContinue()
                state = awaitItem()
                isComplete = (state.phase is ReviewUiState.Phase.Complete)
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
                while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
                while ((state.phase as ReviewUiState.Phase.Active).currentQuestionType != QuestionType.READING) {
                    viewModel.onAnswerInputChange(if ((state.phase as ReviewUiState.Phase.Active).currentQuestionType == QuestionType.MEANING) "Water" else "mizu")
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
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            assertThat((state.phase as ReviewUiState.Phase.Active).currentQuestionType).isEqualTo(QuestionType.MEANING)

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
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            while ((state.phase as ReviewUiState.Phase.Active).currentQuestionType != QuestionType.READING) {
                viewModel.onAnswerInputChange(if ((state.phase as ReviewUiState.Phase.Active).currentQuestionType == QuestionType.MEANING) "Water" else "mizu")
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
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            while ((state.phase as ReviewUiState.Phase.Active).currentQuestionType != QuestionType.READING) {
                viewModel.onAnswerInputChange(if ((state.phase as ReviewUiState.Phase.Active).currentQuestionType == QuestionType.MEANING) "Water" else "mizu")
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
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            viewModel.onAnswerInputChange("typo")
            awaitItem()
            viewModel.submitAnswer()
            val incorrectState = awaitItem()
            assertThat((incorrectState.phase as ReviewUiState.Phase.Active).feedback?.isCorrect).isFalse()

            viewModel.undoLastAnswer()
            val undoneState = awaitItem()
            assertThat((undoneState.phase as ReviewUiState.Phase.Active).feedback).isNull()
            assertThat((undoneState.phase as ReviewUiState.Phase.Active).answerInput).isEmpty()
            // Undo doesn't requeue a duplicate — remaining count is back to exactly one question.
            assertThat((undoneState.phase as ReviewUiState.Phase.Active).remainingCount).isEqualTo(1)

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val correctState = awaitItem()
            assertThat((correctState.phase as ReviewUiState.Phase.Active).feedback?.isCorrect).isTrue()

            viewModel.onContinue()
            val finalState = awaitItem()
            assertThat((finalState.phase is ReviewUiState.Phase.Complete)).isTrue()
        }
    }

    @Test
    fun `undo after a correct answer retracts it instead of submitting to WaniKani`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val correctState = awaitItem()
            assertThat((correctState.phase as ReviewUiState.Phase.Active).feedback?.isCorrect).isTrue()
            assertThat(reviewSessionRepository.load()?.pendingSubmissionAssignmentId).isEqualTo(101L)

            viewModel.undoLastAnswer()
            val undoneState = awaitItem()
            assertThat((undoneState.phase as ReviewUiState.Phase.Active).feedback).isNull()
            assertThat((undoneState.phase as ReviewUiState.Phase.Active).answerInput).isEmpty()
            // Undo pushes the question back to the front rather than dropping it — still one
            // question left to answer.
            assertThat((undoneState.phase as ReviewUiState.Phase.Active).remainingCount).isEqualTo(1)
            assertThat(reviewSessionRepository.load()?.pendingSubmissionAssignmentId).isNull()
            assertThat(repositories.outboxDao.allReviewSubmissions()).isEmpty()

            // Answering it again completes the session normally.
            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val correctAgainState = awaitItem()
            assertThat((correctAgainState.phase as ReviewUiState.Phase.Active).feedback?.isCorrect).isTrue()

            viewModel.onContinue()
            val finalState = awaitItem()
            assertThat((finalState.phase is ReviewUiState.Phase.Complete)).isTrue()
        }

        assertThat(repositories.outboxDao.allReviewSubmissions()).hasSize(1)
    }

    @Test
    fun `abandonSession clears persisted state and marks the session abandoned`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            assertThat(reviewSessionRepository.load()).isNotNull()

            viewModel.abandonSession()
            var abandonedState = awaitItem()
            while (!abandonedState.isAbandoned) abandonedState = awaitItem()
            assertThat(abandonedState.isAbandoned).isTrue()
        }

        assertThat(reviewSessionRepository.load()).isNull()
    }

    @Test
    fun `abandonSession still commits a correct answer that hasn't been continued past yet`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression test: abandoning discards progress on not-yet-submitted items, but a
        // correct-but-not-yet-continued answer already read as "finished" to the user (they saw
        // the "Correct!" feedback) and the abandon confirmation dialog promises submitted items are
        // safe — abandonSession must commit it rather than silently dropping it with the rest.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem()
            assertThat((feedbackState.phase as ReviewUiState.Phase.Active).feedback?.isCorrect).isTrue()
            assertThat(reviewSessionRepository.load()?.pendingSubmissionAssignmentId).isEqualTo(101L)

            viewModel.abandonSession()
            var abandonedState = awaitItem()
            while (!abandonedState.isAbandoned) abandonedState = awaitItem()
        }

        val queued = repositories.outboxDao.allReviewSubmissions()
        assertThat(queued).hasSize(1)
        assertThat(queued.first().assignmentId).isEqualTo(101L)
        assertThat(reviewSessionRepository.load()).isNull()
    }

    @Test
    fun `a new ViewModel resumes a persisted session instead of refetching from the network`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val firstViewModel = createViewModel()
        firstViewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
        }
        val requestCountAfterFirstLoad = server.requestCount

        // Simulate leaving and coming back: a fresh ViewModel sharing the same repositories
        // should pick the in-progress session back up rather than hitting the network again.
        val secondViewModel = createViewModel()
        secondViewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            assertThat((state.phase as ReviewUiState.Phase.Active).totalCount).isEqualTo(1)
            assertThat((state.phase as ReviewUiState.Phase.Active).currentItem.characters).isEqualTo("口")
        }
        assertThat(server.requestCount).isEqualTo(requestCountAfterFirstLoad)
    }

    @Test
    fun `resuming a session with a pending submission commits it, as if Continue had been tapped`() = runTest(mainDispatcherRule.dispatcher) {
        // A correct-but-not-yet-continued answer's WaniKani submission only lives in memory until
        // Continue is pressed (see ReviewViewModel.pendingSubmissionAssignmentId) — if the process
        // dies in that window (simulated here by just creating a fresh ViewModel over the same
        // persisted session, the same way every other resume test does), the persisted snapshot's
        // pendingSubmissionAssignmentId must still get it submitted rather than silently dropping it.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val firstViewModel = createViewModel()
        firstViewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            firstViewModel.onAnswerInputChange("Mouth")
            awaitItem()
            firstViewModel.submitAnswer()
            val correctState = awaitItem()
            assertThat((correctState.phase as ReviewUiState.Phase.Active).feedback?.isCorrect).isTrue()
        }
        assertThat(repositories.outboxDao.allReviewSubmissions()).isEmpty()
        assertThat(reviewSessionRepository.load()?.pendingSubmissionAssignmentId).isEqualTo(101L)

        val secondViewModel = createViewModel()
        secondViewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            assertThat((state.phase is ReviewUiState.Phase.Complete)).isTrue()
        }

        val queued = repositories.outboxDao.allReviewSubmissions()
        assertThat(queued).hasSize(1)
        assertThat(queued.first().assignmentId).isEqualTo(101L)
        assertThat(reviewSessionRepository.load()).isNull()
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
            while ((state.phase is ReviewUiState.Phase.Loading) || !state.settings.showSubjectTypeLabel || !state.settings.showTotalTimer || !state.settings.showQuestionTimer) state = awaitItem()
            assertThat(state.settings.showSubjectTypeLabel).isTrue()
            assertThat(state.settings.showTotalTimer).isTrue()
            assertThat(state.settings.showQuestionTimer).isTrue()
        }
    }

    @Test
    fun `disabling close-enough answers requires an exact meaning match`() = runTest(mainDispatcherRule.dispatcher) {
        settingsRepository.setCloseEnoughAnswersEnabled(false)
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            // "Mouth" (length 5) normally tolerates a single-edit typo close match ("Mouht", the
            // last two letters transposed) — disabled here, so it must be graded incorrect instead.
            viewModel.onAnswerInputChange("Mouht")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem()
            assertThat((feedbackState.phase as ReviewUiState.Phase.Active).feedback?.isCorrect).isFalse()
        }
    }

    @Test
    fun `session summary reports missed items, slowest answers capped at five, and non-negative timing`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(kanjiAssignmentsJson()), jsonResponse(kanjiSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            assertThat((state.phase as ReviewUiState.Phase.Active).timing.sessionActiveSegmentStartMs).isNotNull()
            assertThat((state.phase as ReviewUiState.Phase.Active).timing.questionActiveSegmentStartMs).isNotNull()

            // Miss the first question drawn (whichever type it is), then work through both question
            // types until the session completes.
            viewModel.onAnswerInputChange("wrong")
            awaitItem()
            viewModel.submitAnswer()
            val missedState = awaitItem()
            assertThat((missedState.phase as ReviewUiState.Phase.Active).feedback?.isCorrect).isFalse()

            viewModel.onContinue()
            state = awaitItem()

            var isComplete = false
            var safetyCounter = 0
            while (!isComplete && safetyCounter < 10) {
                safetyCounter++
                val answer = if ((state.phase as ReviewUiState.Phase.Active).currentQuestionType == QuestionType.MEANING) "Water" else "mizu"
                viewModel.onAnswerInputChange(answer)
                awaitItem()
                viewModel.submitAnswer()
                awaitItem()
                viewModel.onContinue()
                state = awaitItem()
                isComplete = (state.phase is ReviewUiState.Phase.Complete)
            }

            assertThat((state.phase is ReviewUiState.Phase.Complete)).isTrue()
            assertThat((state.phase as ReviewUiState.Phase.Complete).sessionMissedItems).hasSize(1)
            assertThat((state.phase as ReviewUiState.Phase.Complete).sessionMissedItems.first().characters).isEqualTo("水")
            assertThat((state.phase as ReviewUiState.Phase.Complete).sessionSlowestAnswers).isNotEmpty()
            assertThat((state.phase as ReviewUiState.Phase.Complete).sessionSlowestAnswers.size).isAtMost(5)
            val elapsedTimes = (state.phase as ReviewUiState.Phase.Complete).sessionSlowestAnswers.map { it.elapsedMs }
            assertThat(elapsedTimes).isEqualTo(elapsedTimes.sortedDescending())
            assertThat((state.phase as ReviewUiState.Phase.Complete).sessionTotalElapsedMs).isAtLeast(0L)
            assertThat((state.phase as ReviewUiState.Phase.Complete).sessionAverageTimePerItemMs).isAtLeast(0L)
        }
    }

    @Test
    fun `submitting an answer freezes questionElapsedMs, and advancing to the next question resets it`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(kanjiAssignmentsJson()), jsonResponse(kanjiSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            assertThat((state.phase as ReviewUiState.Phase.Active).timing.questionElapsedMs).isNull()

            val answer = if ((state.phase as ReviewUiState.Phase.Active).currentQuestionType == QuestionType.MEANING) "Water" else "mizu"
            viewModel.onAnswerInputChange(answer)
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem()
            assertThat((feedbackState.phase as ReviewUiState.Phase.Active).timing.questionElapsedMs).isNotNull()

            viewModel.onContinue()
            val nextState = awaitItem()
            assertThat((nextState.phase as ReviewUiState.Phase.Active).timing.questionElapsedMs).isNull()
        }
    }

    @Test
    fun `undo clears the frozen questionElapsedMs`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            viewModel.onAnswerInputChange("typo")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem()
            assertThat((feedbackState.phase as ReviewUiState.Phase.Active).timing.questionElapsedMs).isNotNull()

            viewModel.undoLastAnswer()
            val undoneState = awaitItem()
            assertThat((undoneState.phase as ReviewUiState.Phase.Active).timing.questionElapsedMs).isNull()
        }
    }

    @Test
    fun `undo removes the just-recorded incorrect answer from session timing`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

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
            val complete = finalState.phase as ReviewUiState.Phase.Complete
            // The undone incorrect attempt shouldn't be double-counted — first-try accuracy is
            // unaffected and only one item was ever reviewed.
            assertThat(complete.sessionItemsReviewed).isEqualTo(1)
            assertThat(complete.sessionItemsCorrectFirstTry).isEqualTo(1)
            assertThat(complete.sessionMissedItems).isEmpty()
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
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            // The queue's draw order is shuffled, so don't stop as soon as the radical happens to
            // complete — keep going until the kanji has also been missed at least once, whichever
            // order they're drawn in. Otherwise, when the radical is drawn first, the loop would
            // exit before ever touching the kanji, leaving it with a first-try-correct outcome
            // after resume and making the assertions below flaky.
            var radicalCompleted = false
            var kanjiMissed = false
            var safetyCounter = 0
            while (!(radicalCompleted && kanjiMissed) && safetyCounter < 10) {
                safetyCounter++
                if ((state.phase as ReviewUiState.Phase.Active).currentItem.assignmentId == 101L) {
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
                    kanjiMissed = true
                }
                firstViewModel.onContinue()
                state = awaitItem()
            }

            assertThat(radicalCompleted).isTrue()
            assertThat(kanjiMissed).isTrue()
            assertThat((state.phase is ReviewUiState.Phase.Complete)).isFalse()
        }

        // Simulate leaving and coming back: a fresh ViewModel must resume the original session —
        // not silently reset it — even though assignment 101's completed progress is no longer in
        // the due queue.
        val requestCountBeforeResume = server.requestCount
        val secondViewModel = createViewModel()
        secondViewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            assertThat((state.phase as? ReviewUiState.Phase.Error)?.message).isNull()
            assertThat((state.phase is ReviewUiState.Phase.Complete)).isFalse()
            assertThat((state.phase as ReviewUiState.Phase.Active).currentItem.assignmentId).isEqualTo(555L)
            // The real assertion: totalCount must still reflect the original 3-question session
            // (1 radical + 2 kanji), not a recomputed 2 (only the kanji still due) — which is what
            // a silent fetchFreshQueue() fallback would produce.
            assertThat((state.phase as ReviewUiState.Phase.Active).totalCount).isEqualTo(3)

            // Finish the session and confirm the graduated radical (101), answered before the
            // pause, still contributes to the final tally instead of silently vanishing from it.
            while (!(state.phase is ReviewUiState.Phase.Complete)) {
                val answer = if ((state.phase as ReviewUiState.Phase.Active).currentQuestionType == QuestionType.MEANING) "Water" else "mizu"
                secondViewModel.onAnswerInputChange(answer)
                awaitItem()
                secondViewModel.submitAnswer()
                awaitItem()
                secondViewModel.onContinue()
                state = awaitItem()
            }

            assertThat((state.phase as ReviewUiState.Phase.Complete).sessionItemsReviewed).isEqualTo(2)
            assertThat((state.phase as ReviewUiState.Phase.Complete).sessionItemsCorrectFirstTry).isEqualTo(1)
            assertThat((state.phase as ReviewUiState.Phase.Complete).sessionMissedItems.map { it.subjectId }).containsExactly(440L)
            // The radical's answer, graded before the pause, must still show up in the "slowest
            // answers" summary — answeredQuestions is restored from the persisted session just like
            // progressByAssignmentId, not reset to only the post-resume segment.
            assertThat((state.phase as ReviewUiState.Phase.Complete).sessionSlowestAnswers.map { it.item.assignmentId }).contains(101L)
        }
        // No fresh sync should have been needed either — the persisted queue was reused as-is.
        assertThat(server.requestCount).isEqualTo(requestCountBeforeResume)
    }

    @Test
    fun `an empty due queue is reported as no reviews available, not a completed session`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(emptyCollectionJson()), jsonResponse(emptyCollectionJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            // NoReviewsAvailable carries no totalCount/question fields at all now — there's
            // structurally nothing to review, rather than an Active phase reporting a count of zero.
            assertThat(state.phase).isInstanceOf(ReviewUiState.Phase.NoReviewsAvailable::class.java)
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
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
        }

        val fakeAccumulatedElapsedMs = 1_000_000L
        val persisted = reviewSessionRepository.load()!!
        reviewSessionRepository.save(persisted.copy(sessionActiveElapsedMs = fakeAccumulatedElapsedMs))

        val secondViewModel = createViewModel()
        secondViewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            assertThat((state.phase as ReviewUiState.Phase.Active).timing.sessionActiveElapsedMs).isEqualTo(fakeAccumulatedElapsedMs)
            assertThat((state.phase as ReviewUiState.Phase.Active).timing.sessionActiveSegmentStartMs).isNotNull()

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
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            assertThat((state.phase as ReviewUiState.Phase.Active).timing.sessionActiveSegmentStartMs).isNotNull()

            appForegroundTracker.onStop(FakeLifecycleOwner)
            val pausedState = awaitItem()
            assertThat((pausedState.phase as ReviewUiState.Phase.Active).timing.sessionActiveSegmentStartMs).isNull()
            val elapsedWhilePaused = (pausedState.phase as ReviewUiState.Phase.Active).timing.sessionActiveElapsedMs

            appForegroundTracker.onStart(FakeLifecycleOwner)
            val resumedState = awaitItem()
            assertThat((resumedState.phase as ReviewUiState.Phase.Active).timing.sessionActiveSegmentStartMs).isNotNull()
            // Resumes right where it left off — the time spent "away" (backgrounded) must not have
            // been folded in as if it were active review time.
            assertThat((resumedState.phase as ReviewUiState.Phase.Active).timing.sessionActiveElapsedMs).isEqualTo(elapsedWhilePaused)
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
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            assertThat((state.phase as ReviewUiState.Phase.Active).timing.questionActiveSegmentStartMs).isNotNull()

            appForegroundTracker.onStop(FakeLifecycleOwner)
            val pausedState = awaitItem()
            assertThat((pausedState.phase as ReviewUiState.Phase.Active).timing.questionActiveSegmentStartMs).isNull()
            val elapsedWhilePaused = (pausedState.phase as ReviewUiState.Phase.Active).timing.questionActiveElapsedMs

            appForegroundTracker.onStart(FakeLifecycleOwner)
            val resumedState = awaitItem()
            assertThat((resumedState.phase as ReviewUiState.Phase.Active).timing.questionActiveSegmentStartMs).isNotNull()
            // Resumes right where it left off — the time spent "away" (backgrounded) must not have
            // been folded in as if it were active question time.
            assertThat((resumedState.phase as ReviewUiState.Phase.Active).timing.questionActiveElapsedMs).isEqualTo(elapsedWhilePaused)

            // Grading now must record an elapsedMs built on that same paused-and-resumed total, not
            // a fresh wall-clock read from when the question first appeared.
            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val gradedState = awaitItem()
            assertThat((gradedState.phase as ReviewUiState.Phase.Active).timing.questionElapsedMs).isAtLeast(elapsedWhilePaused)
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
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem()
            assertThat((feedbackState.phase as ReviewUiState.Phase.Active).feedback?.isCorrect).isTrue()

            viewModel.onContinue()
            val finalState = awaitItem()
            assertThat((finalState.phase is ReviewUiState.Phase.Complete)).isTrue()
            assertThat(reviewSessionRepository.load()).isNull()

            // Backgrounding from the Complete screen. Review's pause path always launches its
            // flush, but the completed session's controller is IDLE, so persist() no-ops — and
            // with phase Complete there are no timing fields to update, so no state update is
            // emitted to await (the old flat-state pause used to publish one). Drain the
            // scheduler so the tracker event and the flush actually run, then verify the
            // cleared session stayed cleared.
            appForegroundTracker.onStop(FakeLifecycleOwner)
        }

        testScheduler.advanceUntilIdle()

        assertThat(reviewSessionRepository.load()).isNull()
    }

    @Test
    fun `grading the last question keeps only a pending-submission snapshot, before Continue is tapped`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression test for a race where grading the last question saved a snapshot of the
        // now-empty queue, and advanceToNextQuestion's completion-time clear (fired later, once the
        // user tapped Continue) raced that save on applicationScope's multi-threaded dispatcher —
        // occasionally the stale save landed after the clear and resurrected the session. gradeAnswer
        // now saves a minimal placeholder — empty queue, just the pending submission id — instead of
        // clearing outright, so a process death in this window doesn't silently drop the not-yet-
        // submitted grade (see ReviewViewModel.pendingSubmissionAssignmentId); advanceToNextQuestion's
        // own clear (once Continue is tapped) still can't race a save, since nothing further gets
        // saved for this item after this point either way.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem()
            assertThat((feedbackState.phase as ReviewUiState.Phase.Active).feedback?.isCorrect).isTrue()
            // Still on the feedback screen — isSessionComplete only flips once onContinue() runs.
            assertThat((feedbackState.phase is ReviewUiState.Phase.Complete)).isFalse()
            val persisted = reviewSessionRepository.load()
            assertThat(persisted).isNotNull()
            assertThat(persisted!!.queue).isEmpty()
            assertThat(persisted.pendingSubmissionAssignmentId).isEqualTo(101L)
        }
    }

    @Test
    fun `backgrounding between grading the last question and tapping Continue does not disturb the pending-submission snapshot`() = runTest(mainDispatcherRule.dispatcher) {
        // Companion to the "grading the last question..." test above: once gradeAnswer has saved the
        // pending-submission placeholder but before onContinue() has run, isSessionComplete is still
        // false — the pause handler's guard must key off the queue being empty too, not just
        // isSessionComplete, or backgrounding in this exact window would re-save a snapshot built
        // from state that no longer matches (queue/progress have already moved on for the *next*
        // question by the time a later pause fires) instead of leaving the placeholder alone.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem()
            assertThat((feedbackState.phase is ReviewUiState.Phase.Complete)).isFalse()
            val persistedBeforePause = reviewSessionRepository.load()
            assertThat(persistedBeforePause?.pendingSubmissionAssignmentId).isEqualTo(101L)

            appForegroundTracker.onStop(FakeLifecycleOwner)
            awaitItem()
        }

        assertThat(reviewSessionRepository.load()?.pendingSubmissionAssignmentId).isEqualTo(101L)
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
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            // Fully complete one item before wrapping up.
            viewModel.onAnswerInputChange((state.phase as ReviewUiState.Phase.Active).currentItem.meanings.first())
            awaitItem()
            viewModel.submitAnswer()
            awaitItem()
            viewModel.onContinue()
            state = awaitItem()

            // Two items remain, both still completely untouched. wrapUp() retains only whichever is
            // now "current" and drops the other outright.
            viewModel.wrapUp()
            val wrappedState = awaitItem()
            assertThat((wrappedState.phase as ReviewUiState.Phase.Active).totalCount).isEqualTo(2)
            assertThat((wrappedState.phase as ReviewUiState.Phase.Active).remainingCount).isEqualTo(1)

            // Finish the one retained item.
            viewModel.onAnswerInputChange((wrappedState.phase as ReviewUiState.Phase.Active).currentItem.meanings.first())
            awaitItem()
            viewModel.submitAnswer()
            awaitItem()
            viewModel.onContinue()
            val finalState = awaitItem()

            val complete = finalState.phase as ReviewUiState.Phase.Complete
            // Exactly the two items actually answered — not the third, dropped-while-untouched one.
            assertThat(complete.sessionItemsReviewed).isEqualTo(2)
            assertThat(complete.sessionItemsCorrectFirstTry).isEqualTo(2)
            assertThat(complete.sessionMissedItems).isEmpty()
        }
    }

    @Test
    fun `wrapUp after the session has already completed does not resurrect the cleared session`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression test for the bug this whole session-persistence redesign is centered on:
        // wrapUp() used to persist unconditionally, with no completion guard, so a stale "Wrap up"
        // tap that lands after the session already completed (e.g. queued right as the overflow menu
        // is dismissed, or a double-tap) could resurrect an already-cleared, logically-finished
        // session in DataStore. QuizSessionController now makes this impossible structurally: once
        // complete() has run, persist() is a no-op regardless of what a stale caller does with it.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            awaitItem()
            viewModel.onContinue()
            val finalState = awaitItem()
            assertThat((finalState.phase is ReviewUiState.Phase.Complete)).isTrue()
            assertThat(reviewSessionRepository.load()).isNull()

            // A stale wrapUp() call arriving after the session is already done and dusted must be a
            // no-op — updateActive's guard clause can't produce a new emission once the phase is
            // Complete (Phase.Complete doesn't even have an isWrappingUp field to set), so there's
            // structurally nothing left for a stale wrapUp() to resurrect.
            viewModel.wrapUp()
            expectNoEvents()
        }

        assertThat(reviewSessionRepository.load()).isNull()
    }

    @Test
    fun `a network error during load sets an error message and clears the loading state`() = runTest(mainDispatcherRule.dispatcher) {
        // Subjects endpoint returns 500 — refreshQueue will return ApiResult.Error after the
        // subjects sync fails, so fetchFreshQueue sets errorMessage on the uiState.
        dispatch(
            assignmentsResponse = jsonResponse(radicalAssignmentsJson()),
            subjectsResponse = jsonResponse("{}", 500)
        )

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            assertThat((state.phase as? ReviewUiState.Phase.Error)?.message).isNotNull()
            assertThat((state.phase is ReviewUiState.Phase.Loading)).isFalse()
        }
    }

    @Test
    fun `retrying loadOrResume after an error clears the error and shows the queue`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(
            assignmentsResponse = jsonResponse(radicalAssignmentsJson()),
            subjectsResponse = jsonResponse("{}", 500)
        )

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            assertThat((state.phase as? ReviewUiState.Phase.Error)?.message).isNotNull()

            // Fix the server and retry.
            dispatch(
                assignmentsResponse = jsonResponse(radicalAssignmentsJson()),
                subjectsResponse = jsonResponse(radicalSubjectsJson())
            )
            viewModel.loadOrResume()

            state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading) || (state.phase as? ReviewUiState.Phase.Error)?.message != null) state = awaitItem()
            assertThat((state.phase as? ReviewUiState.Phase.Error)?.message).isNull()
            assertThat((state.phase as ReviewUiState.Phase.Active).totalCount).isAtLeast(1)
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
