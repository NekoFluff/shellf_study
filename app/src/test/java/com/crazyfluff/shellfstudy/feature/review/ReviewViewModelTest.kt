package com.crazyfluff.shellfstudy.feature.review

import com.crazyfluff.shellfstudy.shared.feature.review.ReviewUiState
import com.crazyfluff.shellfstudy.shared.feature.review.ReviewViewModel
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyKind
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.LastSessionKind
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.PitchAccentRepository
import com.crazyfluff.shellfstudy.shared.data.ReviewSessionRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.model.RankChange
import com.crazyfluff.shellfstudy.shared.data.model.ReviewGrade
import com.crazyfluff.shellfstudy.shared.data.model.ReviewItem
import com.crazyfluff.shellfstudy.shared.data.model.ReviewPriority
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.database.LevelProgressionEntity
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentUiState
import com.crazyfluff.shellfstudy.shared.lifecycle.AppForegroundTracker
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType
import com.crazyfluff.shellfstudy.shared.session.ReviewSessionController
import com.crazyfluff.shellfstudy.fakes.FakeStudyTimeDao
import com.crazyfluff.shellfstudy.fakes.buildTestStudyTimeRepository
import com.crazyfluff.shellfstudy.fakes.emptyCollectionJson
import com.crazyfluff.shellfstudy.fakes.FakeSessionDao
import com.crazyfluff.shellfstudy.fakes.FakeLifecycleOwner
import com.crazyfluff.shellfstudy.fakes.FakePronunciationAudioPlayer
import com.crazyfluff.shellfstudy.fakes.TestRepositories
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.fakes.jsonResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
import com.crazyfluff.shellfstudy.fakes.AssignmentFixture
import com.crazyfluff.shellfstudy.fakes.FIXTURE_INSTANT
import com.crazyfluff.shellfstudy.fakes.RADICAL_SUBJECT
import com.crazyfluff.shellfstudy.fakes.SubjectFixture
import com.crazyfluff.shellfstudy.fakes.VOCAB_SUBJECT
import com.crazyfluff.shellfstudy.fakes.waniKaniAssignmentsJson
import com.crazyfluff.shellfstudy.fakes.waniKaniSubjectsJson
import com.crazyfluff.shellfstudy.fakes.MIZU_AUDIO
import com.crazyfluff.shellfstudy.fakes.waniKaniCollectionDispatcher
import app.cash.turbine.ReceiveTurbine
import com.crazyfluff.shellfstudy.feature.quiz.QuizQuestionView
import com.crazyfluff.shellfstudy.feature.quiz.QuizQueueFixtures
import com.crazyfluff.shellfstudy.feature.quiz.QuizSessionContractTest
import com.crazyfluff.shellfstudy.feature.quiz.QuizSessionSubject

class ReviewViewModelTest : QuizSessionContractTest<ReviewUiState>() {

    // The rules, the mock server, the repository graph and the base wiring come from the harness;
    // what follows is what a review has that a lesson does not.

    private lateinit var statsRepository: StatsRepository
    private lateinit var reviewSessionRepository: ReviewSessionRepository


    // ------------------------------------------------------------------ harness hooks

    override val radicalQueue = QuizQueueFixtures(
        assignments = radicalAssignmentsJson(),
        subjects = radicalSubjectsJson()
    )

    override val vocabQueue = QuizQueueFixtures(
        assignments = vocabAssignmentsJson(),
        subjects = vocabSubjectsJson()
    )

    override val kanjiQueue = QuizQueueFixtures(
        assignments = kanjiAssignmentsJson(),
        subjects = kanjiSubjectsJson()
    )

    /** The review's kanji fixtures already carry pronunciation audio. */
    override val kanjiAudioQueue = kanjiQueue

    override fun createSubject(scope: TestScope): QuizSessionSubject<ReviewUiState> =
        scope.createViewModel().asSubject()

    override fun sessionFinished(state: ReviewUiState): Boolean =
        state.phase is ReviewUiState.Phase.Complete

    override fun isAbandoned(state: ReviewUiState): Boolean = state.isAbandoned

    override fun loadError(state: ReviewUiState): String? =
        (state.phase as? ReviewUiState.Phase.Error)?.message

    override fun dispatchSessionFetches(assignments: MockResponse, subjects: MockResponse) =
        dispatch(assignments, subjects)

    /** The question a review is asking, or null while it is loading or showing its summary. */
    override fun question(state: ReviewUiState): QuizQuestionView? {
        val active = state.phase as? ReviewUiState.Phase.Active ?: return null
        return QuizQuestionView(
            questionType = active.question.type,
            answers = when (active.question.type) {
                QuestionType.MEANING -> active.question.item.meanings
                QuestionType.READING -> active.question.item.readings
            },
            feedbackPresent = active.question.feedback != null,
            feedbackIsCorrect = active.question.feedback?.isCorrect,
            answerRevealed = active.question.grade?.answerRevealed,
            answerHint = active.question.grade?.answerHint,
            // A review folds the live pitch accents into the hint itself.
            pitchAccents = active.question.grade?.answerHint?.pitchAccents,
            remainingCount = active.remainingCount,
            answerTypeMismatchCount = active.question.answerTypeMismatchCount,
            answerInput = active.question.answerInput,
            timing = active.question.timing
        )
    }

    /** A review is asking its first question as soon as the queue loads. */
    override suspend fun startSession(
        subject: QuizSessionSubject<ReviewUiState>,
        states: ReceiveTurbine<ReviewUiState>
    ): ReviewUiState? = null

    /** Adapts the ViewModel to the harness's action set — production code carries no test interface. */
    private class ReviewSubject(val viewModel: ReviewViewModel) : QuizSessionSubject<ReviewUiState> {
        override val uiState get() = viewModel.uiState
        override fun onAnswerInputChange(value: String) = viewModel.onAnswerInputChange(value)
        override fun submitAnswer() = viewModel.submitAnswer()
        override fun dontKnowAnswer() = viewModel.dontKnowAnswer()
        override fun onContinue() = viewModel.onContinue()
        override fun revealAnswer() = viewModel.revealAnswer()
        override fun undoLastAnswer() = viewModel.undoLastAnswer()
        override fun toggleDetails() = viewModel.toggleDetails()
        override fun closeDetails() = viewModel.closeDetails()
        override fun abandonSession() = viewModel.abandonSession()
    }

    private fun ReviewViewModel.asSubject() = ReviewSubject(this)

    @Before
    fun setUp() {
        startHarness()
        statsRepository = repositories.statsRepository
        reviewSessionRepository = ReviewSessionRepository(FakeSessionDao(), dataStore, Json { ignoreUnknownKeys = true })

        // A separate DataStore from the session one: a review's settings are read through their own
        // store so a session write cannot re-emit every settings collector.
        settingsRepository = SettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(mainDispatcherRule.dispatcher + SupervisorJob()),
                produceFile = { tempFolder.newFile("settings.preferences_pb") }
            )
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    /** Where the session clock's stretches land — see StudyTimeRepository.record. */
    private val studyTimeDao = FakeStudyTimeDao()

    private fun TestScope.createViewModel(
        pitchAccentRepository: PitchAccentRepository = repositories.pitchAccentRepository
    ) = ReviewViewModel(
        assignmentRepository, outboxRepository, statsRepository,
        ReviewSessionController(backgroundScope, reviewSessionRepository), lastSessionSummaryRepository,
        pronunciationAudioPlayer, settingsRepository, pitchAccentRepository, appForegroundTracker, backgroundScope,
        repositories.syncOrchestrator,
        buildTestStudyTimeRepository(
            studyTimeDao, dataStore, backgroundScope, mainDispatcherRule.dispatcher, settingsRepository
        )
    )


    /** Routes by path — refreshing the review queue now syncs subjects and assignments, in either order. */
    private fun dispatch(assignmentsResponse: MockResponse, subjectsResponse: MockResponse, reviewResponse: MockResponse? = null) {
        server.dispatcher = waniKaniCollectionDispatcher { request ->
            val path = request.target.orEmpty()
            when {
                request.method == "POST" && path.startsWith("/reviews") -> reviewResponse ?: jsonResponse(reviewResultJson())
                path.startsWith("/assignments") -> assignmentsResponse
                path.startsWith("/subjects") -> subjectsResponse
                else -> null
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
            assertThat(state.question!!.type).isEqualTo(QuestionType.MEANING)

            viewModel.onAnswerInputChange("Rain")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem()
            assertThat(feedbackState.question!!.feedback?.isCorrect).isTrue()

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
            assertThat(state.question!!.type).isEqualTo(QuestionType.MEANING)

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val feedbackState = awaitItem()
            assertThat(feedbackState.question!!.feedback?.isCorrect).isTrue()

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
            assertThat(state.question!!.grade?.rankChange).isNull()

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            // The rank change arrives asynchronously — the optimistic patch runs in its own
            // coroutine, not strictly ordered against the feedback update, so wait until both have
            // landed rather than assuming a fixed number of emissions.
            var settled = awaitItem()
            while (settled.question!!.grade?.rankChange == null) settled = awaitItem()
            assertThat(settled.question!!.feedback?.isCorrect).isTrue()
            // radicalAssignmentsJson fixes the cached assignment at srs_stage 1 (Apprentice I); the
            // optimistic local prediction is one stage up on a correct answer.
            assertThat(settled.question!!.grade?.rankChange)
                .isEqualTo(RankChange(SrsStage.APPRENTICE_1, SrsStage.APPRENTICE_2))

            viewModel.onContinue()
            val finalState = awaitItem()
            // The rank change (an Active-only concept) can't survive into the completed state —
            // that's a structural guarantee now, since Phase.Complete has no rankChange field at all,
            // rather than something to assert by reading a field back as null.
            assertThat(finalState.phase).isInstanceOf(ReviewUiState.Phase.Complete::class.java)
        }
    }

    @Test
    fun `one continued review marks today as a study day without finishing the session`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            var settled = awaitItem()
            while (settled.question!!.feedback == null) settled = awaitItem()

            // Still undoable, so not yet a study day.
            assertThat(repositories.statsRepository.observeStudyStreak().first().isActiveToday).isFalse()

            viewModel.onContinue()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(repositories.statsRepository.observeStudyStreak().first().isActiveToday).isTrue()
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
            while (settled.question!!.feedback == null) settled = awaitItem()

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
        assertThat(server.requestCount).isAtMost(3) // just the queue sync: SRS systems, subjects, assignments
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
            gradedItemId = active.question.item.assignmentId
            gradedType = active.question.type
            val answer = when {
                gradedItemId == 101L -> "Mouth"
                gradedType == QuestionType.MEANING -> "Water"
                else -> "mizu"
            }
            viewModel.onAnswerInputChange(answer)
            awaitItem()
            viewModel.submitAnswer()
            var settled = awaitItem()
            while (settled.question!!.feedback == null) settled = awaitItem()

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
            assertThat(feedbackState.question!!.feedback?.isCorrect).isFalse()
            assertThat((feedbackState.phase as ReviewUiState.Phase.Active).remainingCount).isEqualTo(1)
            val questionSequenceBeforeRequeue = feedbackState.question!!.sequence

            viewModel.onContinue()
            val requeuedState = awaitItem()
            assertThat((requeuedState.phase is ReviewUiState.Phase.Complete)).isFalse()
            assertThat(requeuedState.question!!.type).isEqualTo(QuestionType.MEANING)
            assertThat(requeuedState.question!!.feedback).isNull()
            // Regression: the same item/type reappearing must still clear the answer field and
            // force the answer field to reset — questionSequence has to change even though nothing
            // else about the requeued question's identity did.
            assertThat(requeuedState.question!!.answerInput).isEqualTo("")
            assertThat(requeuedState.question!!.sequence)
                .isNotEqualTo(questionSequenceBeforeRequeue)

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val correctState = awaitItem()
            assertThat(correctState.question!!.feedback?.isCorrect).isTrue()

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
    fun `submitting a reading into a meaning question rejects it instead of grading a miss`() = wrongTypeAnswerIsRejectedNotGraded(
        fixtures = radicalQueue, target = QuestionType.MEANING,
        answerOfTheWrongType = "くち", correctAnswer = "Mouth"
    )

    @Test
    fun `a refused answer does not carry its count into the next question`() = aRefusedAnswerDoesNotCarryIntoTheNextQuestion()

    @Test
    fun `submitting a romaji reading into a meaning question rejects it instead of grading a miss`() = wrongTypeAnswerIsRejectedNotGraded(
        fixtures = kanjiQueue, target = QuestionType.MEANING,
        answerOfTheWrongType = "mizu", correctAnswer = "Water"
    )

    @Test
    fun `submitting a meaning into a reading question rejects it instead of grading a miss`() = wrongTypeAnswerIsRejectedNotGraded(
        fixtures = kanjiQueue, target = QuestionType.READING,
        answerOfTheWrongType = "Water", correctAnswer = "mizu"
    )

    @Test
    fun `dontKnowAnswer grades as incorrect and requeues, without auto-expanding details`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            assertThat(state.question!!.isDetailsExpanded).isFalse()

            viewModel.dontKnowAnswer()
            val feedbackState = awaitItem()
            assertThat(feedbackState.question!!.feedback?.isCorrect).isFalse()
            assertThat(feedbackState.question!!.feedback?.correctAnswer).isEqualTo("Mouth")
            // "I don't know" shouldn't force the detail sheet open — same as a regular wrong answer.
            assertThat(feedbackState.question!!.isDetailsExpanded).isFalse()
            // Requeued, not dropped — remaining count is unchanged, still one question to answer.
            assertThat((feedbackState.phase as ReviewUiState.Phase.Active).remainingCount).isEqualTo(1)

            viewModel.onContinue()
            val requeuedState = awaitItem()
            assertThat((requeuedState.phase is ReviewUiState.Phase.Complete)).isFalse()

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val correctState = awaitItem()
            assertThat(correctState.question!!.feedback?.isCorrect).isTrue()

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
            assertThat(state.question!!.isDetailsExpanded).isFalse()

            viewModel.toggleDetails()
            assertThat((awaitItem().phase as ReviewUiState.Phase.Active).question.isDetailsExpanded).isTrue()

            viewModel.toggleDetails()
            assertThat((awaitItem().phase as ReviewUiState.Phase.Active).question.isDetailsExpanded).isFalse()

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
            assertThat(feedbackState.question!!.feedback).isNotNull()

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
                val answer = if (current.question!!.type == QuestionType.MEANING) "Water" else "mizu"
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
    fun `answering a reading question autoplays the correct pronunciation when the setting is enabled`() = answeringAReadingQuestionAutoplaysItsPronunciation()

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
            assertThat(state.question!!.type).isEqualTo(QuestionType.MEANING)

            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            awaitItem()
        }

        assertThat(pronunciationAudioPlayer.playedAudios).isEmpty()
    }

    @Test
    fun `toggleAudioMuted persists the mute and mirrors it into the UI state`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(kanjiAssignmentsJson()), jsonResponse(kanjiSubjectsJson()))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.toggleAudioMuted()
            var state = awaitItem()
            while (!state.isAudioMuted) state = awaitItem()
            assertThat(settingsRepository.settings.first().audioMuted).isTrue()

            viewModel.toggleAudioMuted()
            while (state.isAudioMuted) state = awaitItem()
            assertThat(settingsRepository.settings.first().audioMuted).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `autoplay is skipped once the setting is turned off`() = runTest(mainDispatcherRule.dispatcher) {
        settingsRepository.setAutoplayPronunciationAudio(false)
        dispatch(jsonResponse(kanjiAssignmentsJson()), jsonResponse(kanjiSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            while (state.question!!.type != QuestionType.READING) {
                viewModel.onAnswerInputChange(if (state.question!!.type == QuestionType.MEANING) "Water" else "mizu")
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
            while (state.question!!.type != QuestionType.READING) {
                viewModel.onAnswerInputChange(if (state.question!!.type == QuestionType.MEANING) "Water" else "mizu")
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
    fun `undo reverts an incorrect answer so it doesn't count as a miss`() = undoingAMissKeepsItOutOfTheRecord { undone ->
        // Undo doesn't requeue a duplicate — remaining count is back to exactly one question.
        assertThat(undone.remainingCount).isEqualTo(1)
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
            // The rank change arrives asynchronously — the optimistic patch runs in its own
            // coroutine, not strictly ordered against the feedback update, so wait until both have
            // landed rather than assuming a fixed number of emissions.
            var correctState = awaitItem()
            while (correctState.question!!.grade?.rankChange == null) correctState = awaitItem()
            assertThat(correctState.question!!.feedback?.isCorrect).isTrue()
            assertThat(correctState.question!!.grade?.rankChange).isNotNull()
            assertThat(reviewSessionRepository.load()?.pendingSubmissionAssignmentId).isEqualTo(101L)

            viewModel.undoLastAnswer()
            val undoneState = awaitItem()
            assertThat(undoneState.question!!.feedback).isNull()
            assertThat(undoneState.question!!.answerInput).isEmpty()
            // The rank-change chip predicted a promotion that this undo just retracted — it must
            // disappear along with the feedback, not linger stale into the retried attempt.
            assertThat(undoneState.question!!.grade?.rankChange).isNull()
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
            assertThat(correctAgainState.question!!.feedback?.isCorrect).isTrue()

            viewModel.onContinue()
            val finalState = awaitItem()
            assertThat((finalState.phase is ReviewUiState.Phase.Complete)).isTrue()
        }

        assertThat(repositories.outboxDao.allReviewSubmissions()).hasSize(1)
    }

    @Test
    fun `undoing a second wrong attempt before answering correctly still submits the item as incorrect`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression for: fail, retry and fail again, undo that second miss, then answer correctly.
        // undoLastIncorrectAnswer unconditionally clears hadIncorrectMeaning to false — correct for
        // undoing the *only* wrong attempt, but wrong here because it also erases the memory of the
        // first, never-undone miss. The item must still be reported to WaniKani as having had an
        // incorrect attempt.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            // First wrong attempt — committed via Continue, never undone.
            viewModel.onAnswerInputChange("typo one")
            awaitItem()
            viewModel.submitAnswer()
            val firstMissState = awaitItem()
            assertThat(firstMissState.question!!.feedback?.isCorrect).isFalse()

            viewModel.onContinue()
            val requeuedState = awaitItem()
            assertThat(requeuedState.question!!.feedback).isNull()
            assertThat(requeuedState.question!!.type).isEqualTo(QuestionType.MEANING)

            // Second wrong attempt on the retry — this one gets undone.
            viewModel.onAnswerInputChange("typo two")
            awaitItem()
            viewModel.submitAnswer()
            val secondMissState = awaitItem()
            assertThat(secondMissState.question!!.feedback?.isCorrect).isFalse()

            viewModel.undoLastAnswer()
            val undoneState = awaitItem()
            assertThat(undoneState.question!!.feedback).isNull()
            assertThat((undoneState.phase as ReviewUiState.Phase.Active).remainingCount).isEqualTo(1)

            // Now answer correctly.
            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            val correctState = awaitItem()
            assertThat(correctState.question!!.feedback?.isCorrect).isTrue()

            viewModel.onContinue()
            val finalState = awaitItem()
            assertThat((finalState.phase is ReviewUiState.Phase.Complete)).isTrue()
        }

        val queued = repositories.outboxDao.allReviewSubmissions()
        assertThat(queued).hasSize(1)
        // The item genuinely had a miss earlier in the session (never undone) — undoing the later,
        // separate miss must not erase that.
        assertThat(queued.first().incorrectMeaningAnswers).isEqualTo(1)
    }

    @Test
    fun `missing the same question twice is reported to WaniKani as two wrong answers, not one`() = runTest(mainDispatcherRule.dispatcher) {
        // WaniKani's demotion rule is ceil(incorrect / 2) * penalty, so the count itself decides the
        // ending stage. The app used to flatten every miss to a single 0/1 before submitting, which
        // under-reported a repeatedly-missed item (and, for the optimistic prediction, showed too
        // shallow a drop). This walks the real session flow — miss, Continue, miss again, then pass —
        // and asserts the count that actually reaches the outbox.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            // First miss, committed via Continue — never undone.
            viewModel.onAnswerInputChange("typo one")
            awaitItem()
            viewModel.submitAnswer()
            assertThat((awaitItem().phase as ReviewUiState.Phase.Active).question.feedback?.isCorrect).isFalse()
            viewModel.onContinue()
            awaitItem()

            // Second miss on the retry.
            viewModel.onAnswerInputChange("typo two")
            awaitItem()
            viewModel.submitAnswer()
            assertThat((awaitItem().phase as ReviewUiState.Phase.Active).question.feedback?.isCorrect).isFalse()
            viewModel.onContinue()
            awaitItem()

            // Finally correct — this is what hands the item to WaniKani.
            viewModel.onAnswerInputChange("Mouth")
            awaitItem()
            viewModel.submitAnswer()
            assertThat((awaitItem().phase as ReviewUiState.Phase.Active).question.feedback?.isCorrect).isTrue()

            viewModel.onContinue()
            awaitItem()
        }

        val queued = repositories.outboxDao.allReviewSubmissions()
        assertThat(queued).hasSize(1)
        assertThat(queued.first().incorrectMeaningAnswers).isEqualTo(2)
        assertThat(queued.first().incorrectReadingAnswers).isEqualTo(0)
    }

    @Test
    fun `a repeated miss on a Guru item drops the rank change by the real penalty, not a flattened one`() = runTest(mainDispatcherRule.dispatcher) {
        // Same divergence, seen through the chip: at Guru I two misses cost 2 stages
        // (ceil(2/2) * 2), where a flattened single miss would have cost the same but a three-miss
        // item would have been under-reported. Uses the repository's synchronous prediction — the
        // exact value `gradeAnswer` feeds to RankChangeChip.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))
        assignmentRepository.warmSrsSystemCache()

        val item = ReviewItem(
            assignmentId = 101, subjectId = 1, subjectType = SubjectType.RADICAL, characters = "口",
            level = 1, srsStage = SrsStage.GURU_1.raw, meanings = listOf("Mouth"), readings = emptyList(),
            srsSystemId = 0
        )

        val twoMisses = assignmentRepository.computeReviewRankChange(
            item,
            ReviewGrade(meaningCorrect = false, readingCorrect = false, incorrectMeaning = 2, incorrectReading = 0)
        )
        assertThat(twoMisses?.to).isEqualTo(SrsStage.APPRENTICE_3)

        val threeMisses = assignmentRepository.computeReviewRankChange(
            item,
            ReviewGrade(meaningCorrect = false, readingCorrect = false, incorrectMeaning = 3, incorrectReading = 0)
        )
        assertThat(threeMisses?.to).isEqualTo(SrsStage.APPRENTICE_1)
    }

    @Test
    fun `abandonSession clears persisted state and marks the session abandoned`() = abandoningASessionClearsItsPersistedState { reviewSessionRepository.load() }

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
            assertThat(feedbackState.question!!.feedback?.isCorrect).isTrue()
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
            assertThat(state.question!!.item.characters).isEqualTo("口")
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
            assertThat(correctState.question!!.feedback?.isCorrect).isTrue()
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
    fun `a miss committed before pausing survives undoing a second miss after resuming`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression for the pause/resume path of the same bug as "undoing a second wrong attempt
        // before answering correctly still submits the item as incorrect": the persisted snapshot's
        // attempt counts survive the resume, and the boolean flags floor them at 1 for snapshots
        // written before the counts were persisted. A second miss made after resuming must increment
        // off that floor, and undoing it must land back on the floor — never below it — so the
        // pre-pause miss is never erased.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val firstViewModel = createViewModel()
        firstViewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            // First miss — committed via Continue before the (simulated) pause.
            firstViewModel.onAnswerInputChange("typo one")
            awaitItem()
            firstViewModel.submitAnswer()
            val firstMissState = awaitItem()
            assertThat(firstMissState.question!!.feedback?.isCorrect).isFalse()

            firstViewModel.onContinue()
            val requeuedState = awaitItem()
            assertThat(requeuedState.question!!.feedback).isNull()
        }
        assertThat(reviewSessionRepository.load()?.progress?.single()?.hadIncorrectMeaning).isTrue()
        assertThat(reviewSessionRepository.load()?.progress?.single()?.incorrectMeaningCount).isEqualTo(1)

        // Simulate leaving and coming back: a fresh ViewModel resumes the persisted session instead
        // of starting a new one.
        val secondViewModel = createViewModel()
        secondViewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            assertThat(state.question!!.type).isEqualTo(QuestionType.MEANING)
            assertThat(state.question!!.feedback).isNull()

            // Second miss on the retry, after resuming — then undone.
            secondViewModel.onAnswerInputChange("typo two")
            awaitItem()
            secondViewModel.submitAnswer()
            val secondMissState = awaitItem()
            assertThat(secondMissState.question!!.feedback?.isCorrect).isFalse()

            secondViewModel.undoLastAnswer()
            val undoneState = awaitItem()
            assertThat(undoneState.question!!.feedback).isNull()

            // Now answer correctly.
            secondViewModel.onAnswerInputChange("Mouth")
            awaitItem()
            secondViewModel.submitAnswer()
            val correctState = awaitItem()
            assertThat(correctState.question!!.feedback?.isCorrect).isTrue()

            secondViewModel.onContinue()
            val finalState = awaitItem()
            assertThat((finalState.phase is ReviewUiState.Phase.Complete)).isTrue()
        }

        val queued = repositories.outboxDao.allReviewSubmissions()
        assertThat(queued).hasSize(1)
        // The pre-pause miss must still count, even though the only miss undone was the one made
        // after resuming.
        assertThat(queued.first().incorrectMeaningAnswers).isEqualTo(1)
    }

    @Test
    fun `a repeated miss survives a pause and resume without being under-reported`() = runTest(mainDispatcherRule.dispatcher) {
        // The count, not just the "was wrong" flag, has to survive the persisted snapshot: WaniKani's
        // demotion rule is ceil(incorrect / 2) * penalty, so losing the second miss on resume would
        // re-introduce the under-report this fix removes.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))

        val firstViewModel = createViewModel()
        firstViewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            repeat(2) {
                firstViewModel.onAnswerInputChange("typo")
                awaitItem()
                firstViewModel.submitAnswer()
                assertThat((awaitItem().phase as ReviewUiState.Phase.Active).question.feedback?.isCorrect).isFalse()
                firstViewModel.onContinue()
                awaitItem()
            }
        }
        assertThat(reviewSessionRepository.load()?.progress?.single()?.incorrectMeaningCount).isEqualTo(2)

        val secondViewModel = createViewModel()
        secondViewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            secondViewModel.onAnswerInputChange("Mouth")
            awaitItem()
            secondViewModel.submitAnswer()
            assertThat((awaitItem().phase as ReviewUiState.Phase.Active).question.feedback?.isCorrect).isTrue()

            secondViewModel.onContinue()
            awaitItem()
        }

        val queued = repositories.outboxDao.allReviewSubmissions()
        assertThat(queued).hasSize(1)
        assertThat(queued.first().incorrectMeaningAnswers).isEqualTo(2)
    }

    @Test
    fun `answering a reading question with the setting on surfaces the reading for the hint`() = readingHintShowsTheReading()

    @Test
    fun `answering a reading question with the setting off leaves the hint fields empty`() = readingHintStaysEmptyWithTheSettingOff()

    @Test
    fun `answering a meaning question never surfaces the reading hint even with the setting on`() = meaningQuestionNeverShowsAReadingHint()

    @Test
    fun `a kanji reading question never surfaces the hint even with the setting on`() = kanjiReadingQuestionNeverShowsAHint()

    @Test
    fun `undoing a reading answer clears the surfaced reading and pitch accents`() = undoingAReadingAnswerClearsItsHint()

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
            assertThat(feedbackState.question!!.feedback?.isCorrect).isFalse()
        }
    }

    @Test
    fun `require tap to reveal answer gates a wrong answer's text until revealAnswer is called`() = wrongAnswerWaitsForReveal()

    @Test
    fun `giving up always reveals the answer regardless of the require-tap setting`() = giveUpAlwaysReveals()

    @Test
    fun `a correct close-match answer is never gated by the require-tap setting`() = closeMatchIsNeverGated()

    @Test
    fun `require tap to reveal answer withholds a wrong reading question's pronunciation audio until revealed`() = wrongReadingWithholdsItsAudioUntilRevealed()

    @Test
    fun `require tap to reveal answer withholds a wrong reading question's answer hint until revealed`() = wrongReadingWaitsForRevealBeforeShowingItsHint()

    @Test
    fun `require tap to reveal meaning answer does not gate a wrong reading answer`() = gatingOneQuestionTypeDoesNotGateTheOther(
        gatedType = QuestionType.MEANING, askedType = QuestionType.READING
    )

    @Test
    fun `require tap to reveal reading answer does not gate a wrong meaning answer`() = gatingOneQuestionTypeDoesNotGateTheOther(
        gatedType = QuestionType.READING, askedType = QuestionType.MEANING
    )

    @Test
    fun `session summary reports missed items, slowest answers capped at five, and non-negative timing`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(kanjiAssignmentsJson()), jsonResponse(kanjiSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            assertThat(state.question!!.timing.sessionActiveSegmentStartMs).isNotNull()
            assertThat(state.question!!.timing.questionActiveSegmentStartMs).isNotNull()

            // Miss the first question drawn (whichever type it is), then work through both question
            // types until the session completes.
            viewModel.onAnswerInputChange("wrong")
            awaitItem()
            viewModel.submitAnswer()
            val missedState = awaitItem()
            assertThat(missedState.question!!.feedback?.isCorrect).isFalse()

            viewModel.onContinue()
            state = awaitItem()

            var isComplete = false
            var safetyCounter = 0
            while (!isComplete && safetyCounter < 10) {
                safetyCounter++
                val answer = if (state.question!!.type == QuestionType.MEANING) "Water" else "mizu"
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
    fun `submitting an answer freezes questionElapsedMs, and advancing to the next question resets it`() = gradingFreezesTheQuestionClockAndTheNextQuestionResetsIt()

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
            assertThat(feedbackState.question!!.timing.questionElapsedMs).isNotNull()

            viewModel.undoLastAnswer()
            val undoneState = awaitItem()
            assertThat(undoneState.question!!.timing.questionElapsedMs).isNull()
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
                if (state.question!!.item.assignmentId == 101L) {
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
            assertThat(state.question!!.item.assignmentId).isEqualTo(555L)
            // The real assertion: totalCount must still reflect the original 3-question session
            // (1 radical + 2 kanji), not a recomputed 2 (only the kanji still due) — which is what
            // a silent fetchFreshQueue() fallback would produce.
            assertThat((state.phase as ReviewUiState.Phase.Active).totalCount).isEqualTo(3)

            // Finish the session and confirm the graduated radical (101), answered before the
            // pause, still contributes to the final tally instead of silently vanishing from it.
            while (!(state.phase is ReviewUiState.Phase.Complete)) {
                val answer = if (state.question!!.type == QuestionType.MEANING) "Water" else "mizu"
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
            assertThat(state.question!!.timing.sessionActiveElapsedMs).isEqualTo(fakeAccumulatedElapsedMs)
            assertThat(state.question!!.timing.sessionActiveSegmentStartMs).isNotNull()

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
    fun `backgrounding the app pauses the total timer, and returning to it resumes without resetting the accumulated time`() = backgroundingPausesTheTotalTimer()

    @Test
    fun `backgrounding the app pauses the per-question timer, and returning to it resumes without resetting the accumulated time`() = backgroundingPausesThePerQuestionTimer()

    // --- Study time ---

    /** Skips emissions until [predicate] holds — the study-time tests care where the session ends
     *  up, not how many timing updates it published on the way. */
    private suspend fun ReceiveTurbine<ReviewUiState>.awaitUntil(predicate: (ReviewUiState) -> Boolean): ReviewUiState {
        var state = awaitItem()
        while (!predicate(state)) state = awaitItem()
        return state
    }

    @Test
    fun `completing a session records one review stretch counting the item it finished`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitUntil { it.phase is ReviewUiState.Phase.Active }
            viewModel.onAnswerInputChange("Mouth")
            viewModel.submitAnswer()
            awaitUntil { it.question?.feedback != null }
            viewModel.onContinue()
            awaitUntil { it.phase is ReviewUiState.Phase.Complete }
            cancelAndIgnoreRemainingEvents()
        }
        testScheduler.advanceUntilIdle()

        val recorded = studyTimeDao.all.single()
        assertThat(recorded.kind).isEqualTo(StudyKind.REVIEW.name)
        assertThat(recorded.itemsAnswered).isEqualTo(1)
    }

    @Test
    fun `returning to the app on the completion screen does not restart the study clock`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitUntil { it.phase is ReviewUiState.Phase.Active }
            viewModel.onAnswerInputChange("Mouth")
            viewModel.submitAnswer()
            awaitUntil { it.question?.feedback != null }
            viewModel.onContinue()
            awaitUntil { it.phase is ReviewUiState.Phase.Complete }
            cancelAndIgnoreRemainingEvents()
        }
        testScheduler.advanceUntilIdle()

        // Reading the summary is not study: a background/foreground round trip here must neither
        // resume the frozen clock nor record a second stretch when the screen is left.
        appForegroundTracker.onStop(FakeLifecycleOwner)
        appForegroundTracker.onStart(FakeLifecycleOwner)
        appForegroundTracker.onStop(FakeLifecycleOwner)
        testScheduler.advanceUntilIdle()

        assertThat(studyTimeDao.all).hasSize(1)
    }

    @Test
    fun `backgrounding mid-session records the stretch so far and the next one starts on return`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitUntil { it.phase is ReviewUiState.Phase.Active }
            appForegroundTracker.onStop(FakeLifecycleOwner)
            awaitUntil { it.question?.timing?.sessionActiveSegmentStartMs == null }
            appForegroundTracker.onStart(FakeLifecycleOwner)
            awaitUntil { it.question?.timing?.sessionActiveSegmentStartMs != null }
            viewModel.onAnswerInputChange("Mouth")
            viewModel.submitAnswer()
            awaitUntil { it.question?.feedback != null }
            viewModel.onContinue()
            awaitUntil { it.phase is ReviewUiState.Phase.Complete }
            cancelAndIgnoreRemainingEvents()
        }
        testScheduler.advanceUntilIdle()

        // Before backgrounding nothing was finished; the item belongs to the stretch after it.
        assertThat(studyTimeDao.all.map { it.itemsAnswered }).containsExactly(0, 1).inOrder()
    }

    @Test
    fun `undoing a correct answer takes its item back out of the stretch`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitUntil { it.phase is ReviewUiState.Phase.Active }
            viewModel.onAnswerInputChange("Mouth")
            viewModel.submitAnswer()
            awaitUntil { it.question?.feedback?.isCorrect == true }
            viewModel.undoLastAnswer()
            awaitUntil { it.question?.feedback == null }
            appForegroundTracker.onStop(FakeLifecycleOwner)
            awaitUntil { it.question?.timing?.sessionActiveSegmentStartMs == null }
            cancelAndIgnoreRemainingEvents()
        }
        testScheduler.advanceUntilIdle()

        assertThat(studyTimeDao.all.single().itemsAnswered).isEqualTo(0)
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
            assertThat(feedbackState.question!!.feedback?.isCorrect).isTrue()

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
            assertThat(feedbackState.question!!.feedback?.isCorrect).isTrue()
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
            viewModel.onAnswerInputChange(state.question!!.item.meanings.first())
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
            viewModel.onAnswerInputChange(wrappedState.question!!.item.meanings.first())
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
    fun `no more than 10 distinct items are ever in flight at once`() = runTest(mainDispatcherRule.dispatcher) {
        // 12 meaning-only radicals, always answered wrong so none of them ever completes and frees
        // its slot — the queue's draw order is shuffled, so this drives enough questions to be sure
        // every item would have been introduced by now if nothing were capping them.
        val itemCount = 12
        dispatch(jsonResponse(manyRadicalAssignmentsJson(itemCount)), jsonResponse(manyRadicalSubjectsJson(itemCount)))

        val viewModel = createViewModel()
        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            val seenAssignmentIds = mutableSetOf<Long>()
            repeat(60) {
                seenAssignmentIds.add(state.question!!.item.assignmentId)
                viewModel.dontKnowAnswer()
                awaitItem()
                viewModel.onContinue()
                state = awaitItem()
            }

            // Never-finishing items keep the in-flight set permanently full, so exactly the cap's
            // worth of distinct items should ever have been drawn — never all 12.
            assertThat(seenAssignmentIds).hasSize(10)
        }
    }

    @Test
    fun `finishing an in-flight item admits a new one instead of staying capped`() = runTest(mainDispatcherRule.dispatcher) {
        val itemCount = 11
        dispatch(jsonResponse(manyRadicalAssignmentsJson(itemCount)), jsonResponse(manyRadicalSubjectsJson(itemCount)))

        val viewModel = createViewModel()
        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            val seenAssignmentIds = mutableSetOf<Long>()
            // Saturate the cap by always answering wrong, stopping the moment a 10th distinct item
            // has been drawn (any further draws must be one of those same 10 while capped).
            while (seenAssignmentIds.size < 10) {
                seenAssignmentIds.add(state.question!!.item.assignmentId)
                viewModel.dontKnowAnswer()
                awaitItem()
                viewModel.onContinue()
                state = awaitItem()
            }
            assertThat(seenAssignmentIds).hasSize(10)

            // Whichever item is current now must be one of the 10 in-flight ones (the cap holds) —
            // answer it correctly to finish it and free its slot. Its subject_id is its assignment id
            // minus 100 (see manyRadicalSubjectsJson), and "Meaning<N>" is its only accepted meaning.
            val finishedSubjectIndex = state.question!!.item.assignmentId - 100
            viewModel.onAnswerInputChange("Meaning$finishedSubjectIndex")
            awaitItem()
            viewModel.submitAnswer()
            awaitItem()
            viewModel.onContinue()
            state = awaitItem()
            seenAssignmentIds.add(state.question!!.item.assignmentId)

            // Finishing one in-flight item frees its slot — the 11th item (never yet seen while the
            // cap held at exactly 10) must eventually be introduced.
            var safetyCounter = 0
            while (seenAssignmentIds.size < 11 && safetyCounter < 30) {
                safetyCounter++
                viewModel.dontKnowAnswer()
                awaitItem()
                viewModel.onContinue()
                state = awaitItem()
                seenAssignmentIds.add(state.question!!.item.assignmentId)
            }
            assertThat(seenAssignmentIds).hasSize(11)
        }
    }

    @Test
    fun `rank-up review priority admits the current level's not-yet-Guru kanji ahead of older due vocabulary, which only backfills`() = runTest(mainDispatcherRule.dispatcher) {
        seedLevel(3)
        dispatch(
            jsonResponse(manyVocabAndKanjiAssignmentsJson(vocabCount = 12, kanjiCount = 2)),
            jsonResponse(manyVocabAndKanjiSubjectsJson(vocabCount = 12, kanjiCount = 2, kanjiLevel = 3))
        )
        settingsRepository.setReviewPriority(ReviewPriority.RANK_UP)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            // Read off the persisted session rather than inferred from draws, so the shuffle inside
            // the working set can't make this flaky. Both level-3 kanji take slots ahead of the
            // vocabulary that has been due longer — the vocabulary can only lose those slots to a
            // priority rule that ignores due order.
            val session = reviewSessionRepository.load()!!
            val inFlight = session.queue.map { it.assignmentId }.toSet()
            assertThat(inFlight).containsAtLeast(1012L, 1013L)
            // While a kanji is unfinished, vocabulary only backfills the working set up to
            // RANK_UP_BACKFILL_ITEMS — the three oldest, in due order — rather than filling all ten.
            assertThat(inFlight).hasSize(5)
            assertThat(inFlight - setOf(1012L, 1013L)).containsExactly(1000L, 1001L, 1002L)
            // Everything else waits in reserve, in due order.
            assertThat(session.reserve.map { it.assignmentId }.distinct())
                .containsExactly(1003L, 1004L, 1005L, 1006L, 1007L, 1008L, 1009L, 1010L, 1011L).inOrder()
            assertThat(session.priorityAssignmentIds).containsExactly(1012L, 1013L)

            // And the session can actually draw them.
            val admitted = drawInFlightItems(viewModel, poolSize = 5) { awaitItem() }
            assertThat(admitted).containsAtLeast(1012L, 1013L)
        }
    }

    @Test
    fun `default review priority keeps the pre-setting ten-item batch and holds the rest in reserve`() = runTest(mainDispatcherRule.dispatcher) {
        seedLevel(3)
        dispatch(
            jsonResponse(manyVocabAndKanjiAssignmentsJson(vocabCount = 12, kanjiCount = 2)),
            jsonResponse(manyVocabAndKanjiSubjectsJson(vocabCount = 12, kanjiCount = 2, kanjiLevel = 3))
        )
        // No setReviewPriority call: an untouched preference must reproduce the pre-setting behavior.
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()

            assertThat((state.phase as ReviewUiState.Phase.Active).totalCount).isEqualTo(28)

            // DEFAULT keeps the pre-setting selection exactly: the ten in-flight slots are filled
            // from a shuffle of the whole due queue, *not* by due order, so which ten are admitted is
            // an arbitrary draw and must not be asserted item-by-item here. What is guaranteed is the
            // shape — ten distinct items in flight, the remaining four held in reserve, all still in
            // the session. (That DEFAULT ignores tiers even when a level is known is pinned
            // deterministically at the queue level, in QuizQueueTest.)
            val session = reviewSessionRepository.load()!!
            assertThat(session.queue.map { it.assignmentId }.toSet()).hasSize(10)
            assertThat(session.reserve.map { it.assignmentId }.toSet()).hasSize(4)
        }
    }

    @Test
    fun `a resumed rank-up session keeps its priority items even after the setting changes`() = runTest(mainDispatcherRule.dispatcher) {
        seedLevel(3)
        dispatch(
            jsonResponse(manyVocabAndKanjiAssignmentsJson(vocabCount = 12, kanjiCount = 2)),
            jsonResponse(manyVocabAndKanjiSubjectsJson(vocabCount = 12, kanjiCount = 2, kanjiLevel = 3))
        )
        settingsRepository.setReviewPriority(ReviewPriority.RANK_UP)

        createViewModel().uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
        }
        // The split is fixed when the session is built: switching back to DEFAULT (or levelling up)
        // before resuming must not re-sort a reserve that was sorted for rank-up.
        settingsRepository.setReviewPriority(ReviewPriority.DEFAULT)
        seedLevel(4)

        val resumed = createViewModel()
        resumed.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            resumed.dontKnowAnswer()
            awaitItem()
            resumed.onContinue()
            awaitItem()
        }

        val snapshot = reviewSessionRepository.load()!!
        assertThat(snapshot.priorityAssignmentIds).containsExactly(1012L, 1013L)
        assertThat(snapshot.queue.map { it.assignmentId }.toSet()).hasSize(5)
    }

    @Test
    fun `a session persisted before priority ids existed resumes with no priority applied`() = runTest(mainDispatcherRule.dispatcher) {
        seedLevel(3)
        dispatch(
            jsonResponse(manyVocabAndKanjiAssignmentsJson(vocabCount = 12, kanjiCount = 2)),
            jsonResponse(manyVocabAndKanjiSubjectsJson(vocabCount = 12, kanjiCount = 2, kanjiLevel = 3))
        )
        settingsRepository.setReviewPriority(ReviewPriority.RANK_UP)
        createViewModel().uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
        }
        val persisted = reviewSessionRepository.load()!!
        reviewSessionRepository.save(persisted.copy(priorityAssignmentIds = emptyList()))

        val resumed = createViewModel()
        resumed.uiState.test {
            var state = awaitItem()
            while ((state.phase is ReviewUiState.Phase.Loading)) state = awaitItem()
            assertThat(state.question).isNotNull()
            resumed.dontKnowAnswer()
            awaitItem()
        }
        assertThat(reviewSessionRepository.load()!!.priorityAssignmentIds).isEmpty()
    }

    /** Seeds the level the review-priority rule reads via `ReviewPrioritizer.priorityIds`. */
    private suspend fun seedLevel(level: Int) {
        repositories.levelProgressionDao.upsertAll(
            listOf(
                LevelProgressionEntity(
                    id = 1, level = level, createdAt = "2026-01-01T00:00:00Z",
                    unlockedAt = null, startedAt = null, passedAt = null, completedAt = null, abandonedAt = null
                )
            )
        )
    }

    /**
     * Answers wrong until every item the session has admitted has been drawn — wrong answers
     * re-queue their question, so a fixed number of draws can revisit the same item. Always-wrong
     * means nothing ever completes, which is what keeps the in-flight pool (and therefore the result
     * set) fixed at the cap for the whole loop. [awaitNextState] is the caller's Turbine `awaitItem`,
     * threaded through so the same collection drives both this loop and the test's assertions.
     */
    private suspend fun drawInFlightItems(
        viewModel: ReviewViewModel,
        poolSize: Int = 10,
        awaitNextState: suspend () -> ReviewUiState
    ): Set<Long> {
        val seen = mutableSetOf<Long>()
        var state = viewModel.uiState.value
        // [poolSize] distinct items drawn uniformly with replacement needs ~29 draws on average for
        // 10 (coupon collector) and can in principle run long — the pool is fixed because nothing
        // ever completes, so this always terminates, and the bound only guards against a regression
        // that let the pool grow.
        var safetyCounter = 0
        while (seen.size < poolSize && safetyCounter < 400) {
            safetyCounter++
            seen += state.question!!.item.assignmentId
            viewModel.dontKnowAnswer()
            awaitNextState()
            viewModel.onContinue()
            state = awaitNextState()
        }
        return seen
    }

    @Test
    fun `an auth error during load sets an error message and clears the loading state`() = authErrorDuringLoadSurfacesAnError()

    @Test
    fun `retrying loadOrResume after an auth error clears the error and shows the queue`() = runTest(mainDispatcherRule.dispatcher) {
        // Use a 401 so the initial load lands in Phase.Error (non-auth errors auto-fall back).
        dispatch(
            assignmentsResponse = jsonResponse(radicalAssignmentsJson()),
            subjectsResponse = jsonResponse("{}", 401)
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

    @Test
    fun `a non-auth network error during load auto-falls back to cached review queue`() = runTest(mainDispatcherRule.dispatcher) {
        // Warm the local cache with one successful sync, then abandon so no persisted session is
        // left behind to short-circuit the next viewModel's loadOrResume() into resuming it instead
        // of attempting (and failing) a fresh fetch.
        dispatch(
            assignmentsResponse = jsonResponse(radicalAssignmentsJson()),
            subjectsResponse = jsonResponse(radicalSubjectsJson())
        )
        val firstViewModel = createViewModel()
        firstViewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is ReviewUiState.Phase.Loading) state = awaitItem()
            assertThat((state.phase as ReviewUiState.Phase.Active).totalCount).isAtLeast(1)

            firstViewModel.abandonSession()
            var abandonedState = awaitItem()
            while (!abandonedState.isAbandoned) abandonedState = awaitItem()
        }

        // Force the next fetchFreshQueue() to actually attempt the network rather than skip it as
        // "still fresh" — otherwise the staleness gate in AssignmentRepository.refreshQueue would
        // silently no-op and never reach the failing subjects endpoint below.
        repositories.syncStateDao.clearAll()

        // The device is offline: the subjects sync that gates a fresh fetch fails.
        dispatch(
            assignmentsResponse = jsonResponse(radicalAssignmentsJson()),
            subjectsResponse = jsonResponse("{}", 500)
        )
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is ReviewUiState.Phase.Loading) state = awaitItem()
            // Non-auth error auto-falls back to the cached data from the first sync.
            assertThat((state.phase as ReviewUiState.Phase.Active).totalCount).isAtLeast(1)
        }
    }

    private fun threeRadicalAssignmentsJson() = waniKaniAssignmentsJson(
        AssignmentFixture(id = 101, subjectId = 1, subjectType = "radical", srsStage = 1, availableAt = FIXTURE_INSTANT),
        AssignmentFixture(id = 102, subjectId = 2, subjectType = "radical", srsStage = 1, availableAt = FIXTURE_INSTANT),
        AssignmentFixture(id = 103, subjectId = 3, subjectType = "radical", srsStage = 1, availableAt = FIXTURE_INSTANT),
    )

    private fun threeRadicalSubjectsJson() = waniKaniSubjectsJson(
        RADICAL_SUBJECT,
        RADICAL_SUBJECT.copy(id = 2, slug = "ground", characters = "一", meaning = "Ground"),
        RADICAL_SUBJECT.copy(id = 3, slug = "tree", characters = "木", meaning = "Tree"),
    )

    /** [count] distinct radical assignments — meaning-only, single-question items, so each one's
     *  own completeness is controlled by a single answer. Used to exercise the in-flight cap, which
     *  needs enough distinct items in the queue at once to actually saturate it. */
    private fun manyRadicalAssignmentsJson(count: Int): String {
        val entries = (0 until count).joinToString(",\n") { i ->
            """
            {
              "id": ${100 + i}, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/${100 + i}",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": $i, "subject_type": "radical",
                "srs_stage": 1, "available_at": "2026-01-01T00:00:00.000000Z", "hidden": false
              }
            }
            """.trimIndent()
        }
        return """
            {
              "object": "collection", "url": "https://api.wanikani.com/v2/assignments", "total_count": $count,
              "data": [$entries]
            }
        """.trimIndent()
    }

    private fun manyRadicalSubjectsJson(count: Int): String {
        val entries = (0 until count).joinToString(",\n") { i ->
            """
            {
              "id": $i, "object": "radical", "url": "https://api.wanikani.com/v2/subjects/$i",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2020-01-01T00:00:00.000000Z", "level": 1, "slug": "radical-$i",
                "characters": "$i",
                "meanings": [{"meaning": "Meaning$i", "primary": true, "accepted_meaning": true}],
                "readings": []
              }
            }
            """.trimIndent()
        }
        return """
            {
              "object": "collection", "url": "https://api.wanikani.com/v2/subjects", "total_count": $count,
              "data": [$entries]
            }
        """.trimIndent()
    }

    /** [vocabCount] vocabulary items due *earlier* than [kanjiCount] kanji, so which items a session
     *  admits first is decided purely by the review-priority setting rather than by due time. Both
     *  subject types here need a reading, giving every item exactly two questions. */
    private fun manyVocabAndKanjiAssignmentsJson(vocabCount: Int, kanjiCount: Int): String {
        fun entry(index: Int, subjectType: String, day: String) = """
            {
              "id": ${1000 + index}, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/${1000 + index}",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": ${5000 + index}, "subject_type": "$subjectType",
                "srs_stage": 3, "available_at": "${day}T00:00:00.000000Z", "hidden": false
              }
            }
        """.trimIndent()

        val entries = (0 until vocabCount).map { entry(it, "vocabulary", "2026-01-${(it + 1).toString().padStart(2, '0')}") } +
            (0 until kanjiCount).map { entry(vocabCount + it, "kanji", "2026-02-0${it + 1}") }
        return """
            {
              "object": "collection", "url": "https://api.wanikani.com/v2/assignments", "total_count": ${vocabCount + kanjiCount},
              "data": [${entries.joinToString(",\n")}]
            }
        """.trimIndent()
    }

    private fun manyVocabAndKanjiSubjectsJson(vocabCount: Int, kanjiCount: Int, kanjiLevel: Int): String {
        fun entry(index: Int, objectType: String, level: Int, characters: String, reading: String) = """
            {
              "id": ${5000 + index}, "object": "$objectType", "url": "https://api.wanikani.com/v2/subjects/${5000 + index}",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2020-01-01T00:00:00.000000Z", "level": $level, "slug": "subject-$index",
                "characters": "$characters",
                "meanings": [{"meaning": "Meaning$index", "primary": true, "accepted_meaning": true}],
                "readings": [{"reading": "$reading", "primary": true, "accepted_reading": true}]
              }
            }
        """.trimIndent()

        val entries = (0 until vocabCount).map { entry(it, "vocabulary", 1, "語$it", "ご$it") } +
            (0 until kanjiCount).map { entry(vocabCount + it, "kanji", kanjiLevel, if (it == 0) "水" else "火", "みず$it") }
        return """
            {
              "object": "collection", "url": "https://api.wanikani.com/v2/subjects", "total_count": ${vocabCount + kanjiCount},
              "data": [${entries.joinToString(",\n")}]
            }
        """.trimIndent()
    }

    private fun radicalAssignmentsJson() = waniKaniAssignmentsJson(
        AssignmentFixture(id = 101, subjectId = 1, subjectType = "radical", srsStage = 1, availableAt = FIXTURE_INSTANT)
    )

    private fun radicalSubjectsJson() = waniKaniSubjectsJson(RADICAL_SUBJECT)

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

    private fun kanjiAssignmentsJson() = waniKaniAssignmentsJson(
        AssignmentFixture(id = 555, subjectId = 440, subjectType = "kanji", srsStage = 3, availableAt = FIXTURE_INSTANT)
    )

    private fun kanjiSubjectsJson() = waniKaniSubjectsJson(
        SubjectFixture(
            id = 440, subjectType = "kanji", level = 3, slug = "water", characters = "水",
            meaning = "Water", reading = "みず", audio = MIZU_AUDIO
        )
    )

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

    private fun vocabAssignmentsJson() = waniKaniAssignmentsJson(
        AssignmentFixture(id = 606, subjectId = 8001, subjectType = "vocabulary", srsStage = 3, availableAt = FIXTURE_INSTANT)
    )
    private fun vocabSubjectsJson() = waniKaniSubjectsJson(VOCAB_SUBJECT)

    /** Two fabricated vocabulary words — used where a test needs the question to advance on to a
     *  genuinely different item rather than completing the session. */
    private fun twoVocabAssignmentsJson() = waniKaniAssignmentsJson(
        AssignmentFixture(id = 606, subjectId = 8001, subjectType = "vocabulary", srsStage = 3, availableAt = FIXTURE_INSTANT),
        AssignmentFixture(id = 607, subjectId = 8002, subjectType = "vocabulary", srsStage = 3, availableAt = FIXTURE_INSTANT),
    )

    private fun twoVocabSubjectsJson() = waniKaniSubjectsJson(
        VOCAB_SUBJECT,
        VOCAB_SUBJECT.copy(id = 8002, slug = "tuesday", characters = "火曜", meaning = "Tuesday", reading = "かよう")
    )


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
