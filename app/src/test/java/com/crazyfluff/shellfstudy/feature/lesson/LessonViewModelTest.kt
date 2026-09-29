package com.crazyfluff.shellfstudy.feature.lesson

import com.crazyfluff.shellfstudy.shared.data.PersistedLessonPhase
import com.crazyfluff.shellfstudy.shared.data.PersistedLessonSession
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonSort
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonUiState
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonViewModel
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.LastSessionKind
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import com.crazyfluff.shellfstudy.shared.data.LessonSessionRepository
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.PitchAccentRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.SubjectRepository
import com.crazyfluff.shellfstudy.shared.session.LessonSessionController
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.data.model.RankChange
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.data.model.StrokeOrderStroke
import com.crazyfluff.shellfstudy.shared.data.StrokeOrderRepository
import com.crazyfluff.shellfstudy.shared.database.SubjectEntity
import com.crazyfluff.shellfstudy.shared.designsystem.strokeorder.StrokeOrderUiState
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentUiState
import com.crazyfluff.shellfstudy.shared.network.MeaningData
import com.crazyfluff.shellfstudy.shared.lifecycle.AppForegroundTracker
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType
import com.crazyfluff.shellfstudy.fakes.emptyCollectionJson
import com.crazyfluff.shellfstudy.fakes.FakeSessionDao
import com.crazyfluff.shellfstudy.fakes.FakeLifecycleOwner
import com.crazyfluff.shellfstudy.fakes.FakePitchAccentBundledSource
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
import com.crazyfluff.shellfstudy.fakes.AssignmentFixture
import com.crazyfluff.shellfstudy.fakes.FIXTURE_INSTANT
import com.crazyfluff.shellfstudy.fakes.KANJI_SUBJECT
import com.crazyfluff.shellfstudy.fakes.RADICAL_SUBJECT
import com.crazyfluff.shellfstudy.fakes.VOCAB_SUBJECT
import com.crazyfluff.shellfstudy.fakes.waniKaniAssignmentsJson
import com.crazyfluff.shellfstudy.fakes.waniKaniSubjectsJson
import com.crazyfluff.shellfstudy.fakes.MIZU_AUDIO
import com.crazyfluff.shellfstudy.fakes.waniKaniCollectionDispatcher
import com.crazyfluff.shellfstudy.feature.quiz.QuizQuestionView
import com.crazyfluff.shellfstudy.feature.quiz.QuizQueueFixtures
import com.crazyfluff.shellfstudy.feature.quiz.QuizSessionContractTest
import com.crazyfluff.shellfstudy.feature.quiz.QuizSessionSubject

class LessonViewModelTest : QuizSessionContractTest<LessonUiState>() {

    // The rules, the mock server, the repository graph and the settings repository come from the
    // harness; what follows is what a lesson has that a review does not.

    private lateinit var lessonSessionRepository: LessonSessionRepository
    private lateinit var pitchAccentRepository: PitchAccentRepository
    private lateinit var subjectRepository: SubjectRepository
    private var strokeOrderRepository: StrokeOrderRepository = FakeStrokeOrderRepository()


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

    override val kanjiAudioQueue = QuizQueueFixtures(
        assignments = kanjiAssignmentsJson(),
        subjects = kanjiSubjectsJsonWithAudio()
    )

    override fun createSubject(scope: TestScope): QuizSessionSubject<LessonUiState> =
        scope.createViewModel().asSubject()

    /** The question a lesson is asking, or null while it is loading, selecting or studying. */
    override fun question(state: LessonUiState): QuizQuestionView? {
        val quiz = state.phase as? LessonUiState.Phase.Quiz ?: return null
        return QuizQuestionView(
            questionType = quiz.currentQuestionType,
            answers = when (quiz.currentQuestionType) {
                QuestionType.MEANING -> quiz.currentItem.meanings
                QuestionType.READING -> quiz.currentItem.readings
            },
            feedbackPresent = quiz.feedback != null,
            feedbackIsCorrect = quiz.feedback?.isCorrect,
            answerRevealed = quiz.answerRevealed,
            answerHint = quiz.answerHint,
            // The lesson keeps pitch accents in a top-level map and folds them into the hint at render
            // time, so this is the same value the screen would show.
            pitchAccents = state.pitchAccentsBySubjectId[quiz.currentItem.subjectId],
            remainingCount = quiz.remainingQuizCount,
            answerTypeMismatchCount = quiz.answerTypeMismatchCount,
            answerInput = quiz.answerInput,
            timing = quiz.timing
        )
    }

    override fun sessionFinished(state: LessonUiState): Boolean =
        state.phase is LessonUiState.Phase.Complete

    override fun isAbandoned(state: LessonUiState): Boolean =
        state.exit == LessonUiState.ExitRequest.Abandoned

    override fun dispatchSessionFetches(assignments: MockResponse, subjects: MockResponse) =
        dispatch(assignments, subjects)

    /** A lesson starts at the selection screen and studies its first card before it asks anything. */
    override suspend fun startSession(
        subject: QuizSessionSubject<LessonUiState>,
        states: ReceiveTurbine<LessonUiState>
    ) {
        val viewModel = (subject as LessonSubject).viewModel
        var state = states.awaitItem()
        while (state.phase is LessonUiState.Phase.Loading) state = states.awaitItem()

        viewModel.startSelectedLessons()
        states.awaitItem()
        viewModel.nextStudyCard()
    }

    /** Adapts the ViewModel to the harness's action set — production code carries no test interface. */
    private class LessonSubject(val viewModel: LessonViewModel) : QuizSessionSubject<LessonUiState> {
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

    private fun LessonViewModel.asSubject() = LessonSubject(this)

    @Before
    fun setUp() {
        startHarness()
        settingsRepository = SettingsRepository(dataStore)
        lessonSessionRepository = LessonSessionRepository(FakeSessionDao(), dataStore, Json { ignoreUnknownKeys = true })
        pitchAccentRepository = repositories.pitchAccentRepository
        subjectRepository = repositories.subjectRepository
        strokeOrderRepository = FakeStrokeOrderRepository()
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

    /** Waits for the session summary, which is where the last batch's questions lead directly. */
    private suspend fun ReceiveTurbine<LessonUiState>.awaitSessionSummary(): LessonUiState.Phase.Complete =
        (awaitItem().phase as LessonUiState.Phase.Complete)

    /**
     * Answers every question of the batch the session is currently quizzed on, correctly, and returns
     * the state the queue empties into — a batch checkpoint, or the summary when that was the last
     * batch and nothing was missed.
     */
    private suspend fun ReceiveTurbine<LessonUiState>.answerEveryQuestionInBatch(
        initialState: LessonUiState,
        viewModel: LessonViewModel
    ): LessonUiState {
        var state = initialState
        var guard = 0
        while (state.phase is LessonUiState.Phase.Quiz && guard++ < 20) {
            val quiz = state.phase as LessonUiState.Phase.Quiz
            val answer = when (quiz.currentQuestionType) {
                QuestionType.MEANING -> quiz.currentItem.meanings.first()
                QuestionType.READING -> quiz.currentItem.readings.first()
            }
            viewModel.onAnswerInputChange(answer)
            awaitItem()
            viewModel.submitAnswer()
            awaitItem()
            viewModel.onContinue()
            state = awaitItem()
        }
        return state
    }

    /** Routes by path — refreshing the lesson queue now syncs subjects and assignments, in either order. */
    private fun dispatch(
        assignmentsResponse: MockResponse,
        subjectsResponse: MockResponse,
        startResponse: MockResponse? = null
    ) {
        server.dispatcher = waniKaniCollectionDispatcher { request ->
            val path = request.path.orEmpty()
            when {
                path.contains("/start") -> startResponse ?: jsonResponse(startAssignmentResultJson())
                path.startsWith("/assignments") -> assignmentsResponse
                path.startsWith("/subjects") -> subjectsResponse
                else -> null
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
    fun `require tap to reveal answer withholds a wrong reading question's answer hint until revealed`() = wrongReadingWaitsForRevealBeforeShowingItsHint()

    @Test
    fun `require tap to reveal answer withholds a wrong reading question's pronunciation audio until revealed`() = wrongReadingWithholdsItsAudioUntilRevealed()

    @Test
    fun `require tap to reveal meaning answer does not gate a wrong reading answer`() = gatingOneQuestionTypeDoesNotGateTheOther(
        gatedType = QuestionType.MEANING, askedType = QuestionType.READING
    )

    @Test
    fun `require tap to reveal reading answer does not gate a wrong meaning answer`() = gatingOneQuestionTypeDoesNotGateTheOther(
        gatedType = QuestionType.READING, askedType = QuestionType.MEANING
    )

    @Test
    fun `resuming a persisted quiz session still resolves the answer hint's pitch accents`() = runTest(mainDispatcherRule.dispatcher) {
        settingsRepository.setShowAnswerReadingPitchAccent(true)
        // buildTestRepositories' PitchAccentRepository is backed by a FakePitchAccentBundledSource,
        // not the real (composeResources-loaded) bundled dictionary — this test needs a genuine
        // match to distinguish "the word resolved" from "it never did", so it seeds one directly
        // rather than relying on the real dictionary's actual contents.
        pitchAccentRepository = PitchAccentRepository(
            FakePitchAccentBundledSource(mapOf("水" to listOf(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0))))
        )
        dispatch(jsonResponse(vocabWithRealPitchAccentAssignmentsJson()), jsonResponse(vocabWithRealPitchAccentSubjectsJson()))

        val firstViewModel = createViewModel()
        firstViewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            firstViewModel.startSelectedLessons()
            awaitItem()

            firstViewModel.nextStudyCard()
            awaitItem() // quiz begins, persisted
        }

        // Simulate leaving and coming back mid-quiz — resumeQuizPhase skips Phase.Study entirely, so
        // it has to point the live observation at the queue's own items for the hint to have any data
        // to show once a reading is answered.
        val secondViewModel = createViewModel()
        secondViewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            while ((state.phase as LessonUiState.Phase.Quiz).currentQuestionType != QuestionType.READING) {
                secondViewModel.onAnswerInputChange("Water")
                awaitItem()
                secondViewModel.submitAnswer()
                awaitItem()
                secondViewModel.onContinue()
                state = awaitItem()
            }

            secondViewModel.onAnswerInputChange("みず")
            awaitItem()
            secondViewModel.submitAnswer()
            var graded = awaitItem()
            val feedbackState = graded.phase as LessonUiState.Phase.Quiz
            assertThat(feedbackState.feedback?.isCorrect).isTrue()
            assertThat(feedbackState.answerHint?.reading).isEqualTo("みず")
            // Only comes back populated if resumeQuizPhase pointed the observation at the seeded
            // item, since this ViewModel instance never went through Phase.Study.
            val expected = PitchAccentUiState.Available(
                listOf(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0))
            )
            while (graded.pitchAccentsBySubjectId[feedbackState.currentItem.subjectId] != expected) graded = awaitItem()
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
    fun `related subjects are observed live — a cache write reaches the study card with no new batch`() =
        runTest(mainDispatcherRule.dispatcher) {
            dispatch(jsonResponse(vocabAssignmentsJson()), jsonResponse(vocabWithAmalgamationSubjectJson()))

            val viewModel = createViewModel()

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

                viewModel.startSelectedLessons()
                state = awaitItem()
                // Not cached yet — the related word (id 9001) was never synced, only referenced.
                assertThat(state.relatedSubjectsById[9001L]).isNull()

                repositories.subjectDao.upsertAll(
                    listOf(
                        SubjectEntity(
                            id = 9001L,
                            subjectType = "vocabulary",
                            level = 1,
                            slug = "otherword",
                            characters = "他語",
                            meanings = listOf(MeaningData(meaning = "Other word", primary = true)),
                            readings = emptyList(),
                            documentUrl = null
                        )
                    )
                )

                while (state.relatedSubjectsById[9001L] == null) state = awaitItem()
                assertThat(state.relatedSubjectsById[9001L]?.meanings).containsExactly("Other word")
                assertThat((state.phase as LessonUiState.Phase.Study).batchIndex).isEqualTo(0)
            }
        }

    // Same word as vocabSubjectsJson(), but "used in" a second, uncached word (9001) — so a later
    // cache write for that id can be observed reaching the study card live.
    private fun vocabWithAmalgamationSubjectJson() = """
        {
          "object": "collection", "url": "https://api.wanikani.com/v2/subjects", "total_count": 1,
          "data": [{
            "id": 8001, "object": "vocabulary", "url": "https://api.wanikani.com/v2/subjects/8001",
            "data_updated_at": "2026-01-01T00:00:00.000000Z",
            "data": {
              "created_at": "2020-01-01T00:00:00.000000Z", "level": 1, "slug": "testword",
              "characters": "件亜",
              "meanings": [{"meaning": "Testword", "primary": true, "accepted_meaning": true}],
              "readings": [{"reading": "けんあ", "primary": true, "accepted_reading": true}],
              "amalgamation_subject_ids": [9001]
            }
          }]
        }
    """.trimIndent()

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
    fun `undo reverts an incorrect answer so it doesn't count as a miss`() = undoingAMissKeepsItOutOfTheRecord(
        afterSession = {
            // The undone wrong answer must not count toward the session's missed-item tally — the
            // only item in this session should show as correct-on-first-try.
            val savedSummary = lastSessionSummaryRepository.loadLesson()
            assertThat(savedSummary?.itemsCount).isEqualTo(1)
            assertThat(savedSummary?.correctFirstTry).isEqualTo(1)
        }
    )

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
            val questionSequenceBeforeRequeue = feedbackState.questionSequence

            viewModel.onContinue()
            val requeuedState = awaitItem().phase as LessonUiState.Phase.Quiz
            assertThat(requeuedState.currentQuestionType).isEqualTo(QuestionType.MEANING)
            // Regression: the same item/type reappearing must still clear the answer field and
            // force the answer field to reset — questionSequence has to change even though nothing
            // else about the requeued question's identity did.
            assertThat(requeuedState.answerInput).isEqualTo("")
            assertThat(requeuedState.questionSequence).isNotEqualTo(questionSequenceBeforeRequeue)
        }
    }

    @Test
    fun `submitting an answer freezes questionElapsedMs, and advancing to the next question resets it`() = gradingFreezesTheQuestionClockAndTheNextQuestionResetsIt()

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
    fun `require tap to reveal answer gates a wrong answer's text until revealAnswer is called`() = wrongAnswerWaitsForReveal()

    @Test
    fun `giving up always reveals the answer regardless of the require-tap setting`() = giveUpAlwaysReveals()

    @Test
    fun `a correct close-match answer is never gated by the require-tap setting`() = closeMatchIsNeverGated()

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
    fun `a non-auth network error during load auto-falls back to no lessons available when cache is empty`() = runTest(mainDispatcherRule.dispatcher) {
        // Subjects endpoint returns 500 — refreshLessonQueue returns ApiResult.Error, and since
        // it is not an auth error, fetchFreshQueue auto-falls back to the (empty) cache, landing
        // on NoLessonsAvailable rather than showing an error screen.
        dispatch(
            assignmentsResponse = jsonResponse(radicalAssignmentsJson()),
            subjectsResponse = jsonResponse("{}", 500)
        )

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()
            assertThat(state.phase).isEqualTo(LessonUiState.Phase.NoLessonsAvailable)
        }
    }

    @Test
    fun `an auth error during load sets an error message`() = runTest(mainDispatcherRule.dispatcher) {
        // 401 is an auth error — fetchFreshQueue must not silently swallow it and should surface
        // Phase.Error so the user knows their token is invalid.
        dispatch(
            assignmentsResponse = jsonResponse(radicalAssignmentsJson()),
            subjectsResponse = jsonResponse("{}", 401)
        )

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()
            assertThat((state.phase as? LessonUiState.Phase.Error)?.message).isNotNull()
        }
    }

    @Test
    fun `a non-auth network error during load auto-falls back to cached lesson selection`() = runTest(mainDispatcherRule.dispatcher) {
        // Warm the local cache with one successful sync first.
        dispatch(jsonResponse(radicalAssignmentsJson()), jsonResponse(radicalSubjectsJson()))
        val firstViewModel = createViewModel()
        firstViewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()
            assertThat(state.phase).isInstanceOf(LessonUiState.Phase.Select::class.java)
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
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()
            // Non-auth error auto-falls back to the cached data from the first sync.
            assertThat(state.phase).isInstanceOf(LessonUiState.Phase.Select::class.java)
        }
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
    fun `abandonSession clears persisted state and marks the session abandoned`() = abandoningASessionClearsItsPersistedState { lessonSessionRepository.load() }

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
            val finalState = awaitSessionSummary()

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
                when (val phase = state.phase) {
                    is LessonUiState.Phase.Quiz -> {
                        secondViewModel.onAnswerInputChange(phase.currentItem.meanings.first())
                        awaitItem()
                        secondViewModel.submitAnswer()
                        awaitItem()
                        secondViewModel.onContinue()
                    }
                    else -> error("unexpected phase while waiting for the session summary: $phase")
                }
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
                when (val phase = state.phase) {
                    is LessonUiState.Phase.Quiz -> {
                        secondViewModel.onAnswerInputChange(phase.currentItem.meanings.first())
                        awaitItem()
                        secondViewModel.submitAnswer()
                        awaitItem()
                        secondViewModel.onContinue()
                    }
                    else -> error("unexpected phase while waiting for the session summary: $phase")
                }
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
    fun `backgrounding the app pauses the total timer, and returning to it resumes without resetting the accumulated time`() = backgroundingPausesTheTotalTimer()

    @Test
    fun `backgrounding the app pauses the per-question timer, and returning to it resumes without resetting the accumulated time`() = backgroundingPausesThePerQuestionTimer()

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
    fun `submitting a reading into a meaning question rejects it instead of grading a miss`() = wrongTypeAnswerIsRejectedNotGraded(
        fixtures = radicalQueue, target = QuestionType.MEANING,
        answerOfTheWrongType = "くち", correctAnswer = "Mouth"
    )

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
    fun `an auth error during load sets an error message and clears the loading state`() = runTest(mainDispatcherRule.dispatcher) {
        // 401 is an auth error — fetchFreshQueue surfaces Phase.Error instead of auto-falling back,
        // so loading clears and the error is visible. Loading and Error are disjoint sealed variants.
        dispatch(
            assignmentsResponse = jsonResponse(radicalAssignmentsJson()),
            subjectsResponse = jsonResponse("{}", 401)
        )

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()
            assertThat((state.phase as LessonUiState.Phase.Error).message).isNotEmpty()
        }
    }

    @Test
    fun `retrying load() after an auth error clears the error and shows the lesson select screen`() = runTest(mainDispatcherRule.dispatcher) {
        // Use a 401 so the initial load lands in Phase.Error (non-auth errors auto-fall back).
        dispatch(
            assignmentsResponse = jsonResponse(radicalAssignmentsJson()),
            subjectsResponse = jsonResponse("{}", 401)
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
        // re-wrote the session via pauseActiveSegment() because the abandoned exit was never checked.
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
            // Abandon clears DataStore then marks the exit as Abandoned; wait for both.
            viewModel.abandonSession()
            var s = awaitItem()
            while (s.exit != LessonUiState.ExitRequest.Abandoned) s = awaitItem()
        }

        // The exit is Abandoned now. onCleared() must not re-write the session.
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
    fun `answering a reading question autoplays the correct pronunciation when the setting is enabled`() = answeringAReadingQuestionAutoplaysItsPronunciation()

    @Test
    fun `a selection larger than the batch size is sliced into batches of the configured size`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(threeRadicalAssignmentsJson()), jsonResponse(threeRadicalSubjectsJson()))
        settingsRepository.setLessonBatchSize(2)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            // The picker opens with exactly one batch selected rather than the whole queue, and says
            // how big a batch is.
            val select = state.phase as LessonUiState.Phase.Select
            assertThat(select.batchSize).isEqualTo(2)
            assertThat(select.selectedAssignmentIds).hasSize(2)

            viewModel.selectAll()
            awaitItem()
            viewModel.startSelectedLessons()
            state = awaitItem()

            // Studying starts at the first batch only — the third lesson isn't on the pager at all.
            val firstBatch = state.phase as LessonUiState.Phase.Study
            assertThat(firstBatch.batchIndex).isEqualTo(0)
            assertThat(firstBatch.batchCount).isEqualTo(2)
            assertThat(firstBatch.studyItems.map { it.assignmentId }).containsExactly(101L, 102L).inOrder()

            viewModel.nextStudyCard()
            awaitItem()
            viewModel.nextStudyCard()
            state = awaitItem()

            // ...and the quiz is only about those two, not about every item the learner picked.
            val quiz = state.phase as LessonUiState.Phase.Quiz
            assertThat(quiz.batchIndex).isEqualTo(0)
            assertThat(quiz.batchCount).isEqualTo(2)
            assertThat(quiz.totalQuizCount).isEqualTo(2)
        }
    }

    @Test
    fun `finishing a batch stops at a checkpoint offering the next batch`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(threeRadicalAssignmentsJson()), jsonResponse(threeRadicalSubjectsJson()))
        settingsRepository.setLessonBatchSize(2)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()
            viewModel.selectAll()
            awaitItem()
            viewModel.startSelectedLessons()
            awaitItem() // batch 1 study
            viewModel.nextStudyCard()
            awaitItem()
            viewModel.nextStudyCard()
            state = awaitItem() // batch 1 quiz

            state = answerEveryQuestionInBatch(state, viewModel)
            val checkpoint = state.phase as LessonUiState.Phase.BatchComplete

            assertThat(checkpoint.batchIndex).isEqualTo(0)
            assertThat(checkpoint.batchCount).isEqualTo(2)
            assertThat(checkpoint.itemsLearned).isEqualTo(2)
            assertThat(checkpoint.itemsCorrectFirstTry).isEqualTo(2)
            assertThat(checkpoint.missedItems).isEmpty()
            assertThat(checkpoint.remainingSessionItems).isEqualTo(1)

            viewModel.continueSession()
            val secondBatch = awaitItem().phase as LessonUiState.Phase.Study
            assertThat(secondBatch.batchIndex).isEqualTo(1)
            assertThat(secondBatch.studyItems.map { it.assignmentId }).containsExactly(103L)
        }
    }

    @Test
    fun `finish for now parks the session and resuming continues at the next batch`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(threeRadicalAssignmentsJson()), jsonResponse(threeRadicalSubjectsJson()))
        settingsRepository.setLessonBatchSize(2)

        val firstViewModel = createViewModel()
        firstViewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()
            firstViewModel.selectAll()
            awaitItem()
            firstViewModel.startSelectedLessons()
            awaitItem() // batch 1 study
            firstViewModel.nextStudyCard()
            awaitItem()
            firstViewModel.nextStudyCard()
            state = awaitItem() // batch 1 quiz

            state = answerEveryQuestionInBatch(state, firstViewModel)
            assertThat(state.phase).isInstanceOf(LessonUiState.Phase.BatchComplete::class.java)

            firstViewModel.finishForNow()
            var parked = awaitItem()
            while (parked.exit != LessonUiState.ExitRequest.Parked) parked = awaitItem()
        }

        // Parking keeps the session, pointed at the batch that hasn't been studied yet — which is what
        // the dashboard's "Resume" then walks into.
        val persisted = lessonSessionRepository.load()
        assertThat(persisted).isNotNull()
        assertThat(persisted!!.phase).isEqualTo(PersistedLessonPhase.CHECKPOINT)
        assertThat(persisted.batchIndex).isEqualTo(1)

        val secondViewModel = createViewModel()
        secondViewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            val resumed = state.phase as LessonUiState.Phase.Study
            assertThat(resumed.batchIndex).isEqualTo(1)
            assertThat(resumed.batchCount).isEqualTo(2)
            assertThat(resumed.studyItems.map { it.assignmentId }).containsExactly(103L)
        }
    }

    @Test
    fun `the picker defaults to what is left of the daily lesson goal`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(threeRadicalAssignmentsJson()), jsonResponse(threeRadicalSubjectsJson()))
        settingsRepository.setDailyLessonGoal(2)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            val select = state.phase as LessonUiState.Phase.Select
            assertThat(select.selectedAssignmentIds).hasSize(2)
        }
    }

    @Test
    fun `the picker offers every type in the queue, radicals first`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(mixedAssignmentsJson()), jsonResponse(mixedSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            val select = state.phase as LessonUiState.Phase.Select
            assertThat(select.availableTypes)
                .containsExactly(SubjectType.RADICAL, SubjectType.KANJI, SubjectType.VOCABULARY)
                .inOrder()
            assertThat(select.countOfType(SubjectType.KANJI)).isEqualTo(2)
        }
    }

    @Test
    fun `toggling a type selects every lesson of it`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(mixedAssignmentsJson()), jsonResponse(mixedSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.selectNone()
            awaitItem()
            viewModel.toggleLessonTypeSelection(SubjectType.KANJI)
            val select = awaitItem().phase as LessonUiState.Phase.Select

            assertThat(select.selectedAssignmentIds).containsExactly(102L, 104L)
            // The whole point of the chip's filled state: it only reads as on when all of the type is in.
            assertThat(select.isTypeFullySelected(SubjectType.KANJI)).isTrue()
            assertThat(select.isTypeFullySelected(SubjectType.RADICAL)).isFalse()
        }
    }

    @Test
    fun `toggling a fully selected type again clears it`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(mixedAssignmentsJson()), jsonResponse(mixedSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            // This queue is shorter than the default batch, so everything starts selected — every
            // chip is filled in before the learner touches one.
            val all = state.phase as LessonUiState.Phase.Select
            assertThat(all.isTypeFullySelected(SubjectType.KANJI)).isTrue()

            viewModel.toggleLessonTypeSelection(SubjectType.KANJI)
            val select = awaitItem().phase as LessonUiState.Phase.Select

            assertThat(select.selectedAssignmentIds).containsExactly(101L, 103L)
            assertThat(select.isTypeFullySelected(SubjectType.KANJI)).isFalse()
        }
    }

    @Test
    fun `a partially selected type completes on the first toggle`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(mixedAssignmentsJson()), jsonResponse(mixedSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            // One of the two kanji, so the kanji chip is unfilled — a tap should add the missing one
            // rather than wipe the type out.
            viewModel.selectFirst(2)
            val partial = awaitItem().phase as LessonUiState.Phase.Select
            assertThat(partial.selectedAssignmentIds).containsExactly(101L, 102L)
            assertThat(partial.isTypeFullySelected(SubjectType.KANJI)).isFalse()

            viewModel.toggleLessonTypeSelection(SubjectType.KANJI)
            val completed = awaitItem().phase as LessonUiState.Phase.Select

            assertThat(completed.selectedAssignmentIds).containsExactly(101L, 102L, 104L)
            assertThat(completed.isTypeFullySelected(SubjectType.KANJI)).isTrue()
        }
    }

    @Test
    fun `toggling a type the queue has none of is a no-op`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(mixedAssignmentsJson()), jsonResponse(mixedSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.toggleLessonTypeSelection(SubjectType.KANA_VOCABULARY)
            expectNoEvents()
        }
    }

    @Test
    fun `toggling kanji then starting a session studies only kanji`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(mixedAssignmentsJson()), jsonResponse(mixedSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.selectNone()
            awaitItem()
            viewModel.toggleLessonTypeSelection(SubjectType.KANJI)
            awaitItem()

            viewModel.startSelectedLessons()

            var study = awaitItem().phase
            while (study !is LessonUiState.Phase.Study) study = awaitItem().phase
            assertThat(study.studyItems.map { it.subjectType })
                .containsExactly(SubjectType.KANJI, SubjectType.KANJI)
        }
    }

    @Test
    fun `setLessonSort reorders the queue kanji-first and keeps the selection`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(mixedAssignmentsJson()), jsonResponse(mixedSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()
            val before = state.phase as LessonUiState.Phase.Select
            assertThat(before.sort).isEqualTo(LessonSort.DEFAULT)
            assertThat(before.availableLessons.map { it.assignmentId })
                .containsExactly(101L, 102L, 103L, 104L)
                .inOrder()

            viewModel.setLessonSort(LessonSort.KANJI_FIRST)
            val sorted = awaitItem().phase as LessonUiState.Phase.Select

            assertThat(sorted.sort).isEqualTo(LessonSort.KANJI_FIRST)
            assertThat(sorted.availableLessons.map { it.assignmentId })
                .containsExactly(102L, 104L, 101L, 103L)
                .inOrder()
            // Re-ordering isn't re-selecting: the same lessons stay chosen.
            assertThat(sorted.selectedAssignmentIds).isEqualTo(before.selectedAssignmentIds)
        }
    }

    @Test
    fun `re-selecting the current sort is a no-op`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(mixedAssignmentsJson()), jsonResponse(mixedSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.setLessonSort(LessonSort.DEFAULT)
            expectNoEvents()
        }
    }

    @Test
    fun `selectFirst follows the chosen sort`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(mixedAssignmentsJson()), jsonResponse(mixedSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.setLessonSort(LessonSort.KANJI_FIRST)
            awaitItem()
            viewModel.selectFirst(1)
            val select = awaitItem().phase as LessonUiState.Phase.Select

            // The quick pick's first slot is now the kanji, which is the whole point of sorting.
            assertThat(select.selectedAssignmentIds).containsExactly(102L)
        }
    }

    @Test
    fun `a kanji-first session studies the kanji before anything else`() = runTest(mainDispatcherRule.dispatcher) {
        dispatch(jsonResponse(mixedAssignmentsJson()), jsonResponse(mixedSubjectsJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.phase is LessonUiState.Phase.Loading) state = awaitItem()

            viewModel.setLessonSort(LessonSort.KANJI_FIRST)
            awaitItem()
            viewModel.selectFirst(3)
            awaitItem()

            viewModel.startSelectedLessons()

            var study = awaitItem().phase
            while (study !is LessonUiState.Phase.Study) study = awaitItem().phase
            // The sort is the plan order: the session's first batch is both kanji, ahead of the radical.
            assertThat(study.studyItems.map { it.subjectType })
                .containsExactly(SubjectType.KANJI, SubjectType.KANJI, SubjectType.RADICAL)
                .inOrder()
        }
    }

    // --- Fixtures -----------------------------------------------------------------------------

    private fun kanjiAssignmentsJson() = waniKaniAssignmentsJson(
        AssignmentFixture(id = 101, subjectId = 1, subjectType = "kanji", srsStage = 0, unlockedAt = FIXTURE_INSTANT)
    )

    private fun kanjiSubjectsJson() = waniKaniSubjectsJson(KANJI_SUBJECT)

    private fun kanjiSubjectsJsonWithAudio() = waniKaniSubjectsJson(
        KANJI_SUBJECT.copy(audio = MIZU_AUDIO)
    )

    private fun vocabAssignmentsJson() = waniKaniAssignmentsJson(
        AssignmentFixture(id = 606, subjectId = 8001, subjectType = "vocabulary", srsStage = 0, unlockedAt = FIXTURE_INSTANT)
    )
    private fun vocabSubjectsJson() = waniKaniSubjectsJson(VOCAB_SUBJECT)
    private fun twoVocabAssignmentsJson() = waniKaniAssignmentsJson(
        AssignmentFixture(id = 606, subjectId = 8001, subjectType = "vocabulary", srsStage = 0, unlockedAt = FIXTURE_INSTANT),
        AssignmentFixture(id = 607, subjectId = 8002, subjectType = "vocabulary", srsStage = 0, unlockedAt = FIXTURE_INSTANT),
    )

    private fun twoVocabSubjectsJson() = waniKaniSubjectsJson(
        VOCAB_SUBJECT,
        VOCAB_SUBJECT.copy(id = 8002, slug = "tuesday", characters = "火曜", meaning = "Tuesday", reading = "かよう")
    )
    private fun vocabWithRealPitchAccentAssignmentsJson() = """
        {
          "object": "collection", "url": "https://api.wanikani.com/v2/assignments", "total_count": 1,
          "data": [{
            "id": 707, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/707",
            "data_updated_at": "2026-01-01T00:00:00.000000Z",
            "data": {
              "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": 9002, "subject_type": "vocabulary",
              "srs_stage": 0, "unlocked_at": "2026-01-01T00:00:00.000000Z", "hidden": false
            }
          }]
        }
    """.trimIndent()

    private fun vocabWithRealPitchAccentSubjectsJson() = """
        {
          "object": "collection", "url": "https://api.wanikani.com/v2/subjects", "total_count": 1,
          "data": [{
            "id": 9002, "object": "vocabulary", "url": "https://api.wanikani.com/v2/subjects/9002",
            "data_updated_at": "2026-01-01T00:00:00.000000Z",
            "data": {
              "created_at": "2020-01-01T00:00:00.000000Z", "level": 1, "slug": "water",
              "characters": "水",
              "meanings": [{"meaning": "Water", "primary": true, "accepted_meaning": true}],
              "readings": [{"reading": "みず", "primary": true, "accepted_reading": true}]
            }
          }]
        }
    """.trimIndent()

    private fun radicalAssignmentsJson() = waniKaniAssignmentsJson(
        AssignmentFixture(id = 101, subjectId = 1, subjectType = "radical", srsStage = 0, unlockedAt = FIXTURE_INSTANT)
    )

    private fun radicalSubjectsJson() = waniKaniSubjectsJson(
        RADICAL_SUBJECT.copy(mnemonic = "A stream of water.")
    )

    private fun threeRadicalAssignmentsJson() = waniKaniAssignmentsJson(
        AssignmentFixture(id = 101, subjectId = 1, subjectType = "radical", srsStage = 0, unlockedAt = FIXTURE_INSTANT),
        AssignmentFixture(id = 102, subjectId = 2, subjectType = "radical", srsStage = 0, unlockedAt = FIXTURE_INSTANT),
        AssignmentFixture(id = 103, subjectId = 3, subjectType = "radical", srsStage = 0, unlockedAt = FIXTURE_INSTANT),
    )

    private fun threeRadicalSubjectsJson() = waniKaniSubjectsJson(
        RADICAL_SUBJECT,
        RADICAL_SUBJECT.copy(id = 2, slug = "ground", characters = "一", meaning = "Ground"),
        RADICAL_SUBJECT.copy(id = 3, slug = "two", characters = "二", meaning = "Two"),
    )

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

    /** One radical, two kanji and one vocabulary item, all level 1 — the smallest queue that can
     *  exercise both whole-type selection (a type with more than one lesson, so it can be partially
     *  selected) and the sort orders. Ties in lesson position break by assignment id, so by default
     *  [LessonPrioritizer] orders these 101 (radical) → 102 → 103 (vocab) → 104, and kanji-first
     *  orders them 102 → 104 → 101 → 103. */
    private fun mixedAssignmentsJson() = """
        {
          "object": "collection", "url": "https://api.wanikani.com/v2/assignments", "total_count": 4,
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
                "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": 2, "subject_type": "kanji",
                "srs_stage": 0, "unlocked_at": "2026-01-01T00:00:00.000000Z", "hidden": false
              }
            },
            {
              "id": 103, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/103",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": 3, "subject_type": "vocabulary",
                "srs_stage": 0, "unlocked_at": "2026-01-01T00:00:00.000000Z", "hidden": false
              }
            },
            {
              "id": 104, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/104",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": 4, "subject_type": "kanji",
                "srs_stage": 0, "unlocked_at": "2026-01-01T00:00:00.000000Z", "hidden": false
              }
            }
          ]
        }
    """.trimIndent()

    private fun mixedSubjectsJson() = """
        {
          "object": "collection", "url": "https://api.wanikani.com/v2/subjects", "total_count": 4,
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
              "id": 2, "object": "kanji", "url": "https://api.wanikani.com/v2/subjects/2",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2020-01-01T00:00:00.000000Z", "level": 1, "slug": "water",
                "characters": "水",
                "meanings": [{"meaning": "Water", "primary": true, "accepted_meaning": true}],
                "readings": [{"reading": "みず", "primary": true, "accepted_reading": true}]
              }
            },
            {
              "id": 3, "object": "vocabulary", "url": "https://api.wanikani.com/v2/subjects/3",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2020-01-01T00:00:00.000000Z", "level": 1, "slug": "water-vocab",
                "characters": "水",
                "meanings": [{"meaning": "Water", "primary": true, "accepted_meaning": true}],
                "readings": [{"reading": "みず", "primary": true, "accepted_reading": true}]
              }
            },
            {
              "id": 4, "object": "kanji", "url": "https://api.wanikani.com/v2/subjects/4",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2020-01-01T00:00:00.000000Z", "level": 1, "slug": "fire",
                "characters": "火",
                "meanings": [{"meaning": "Fire", "primary": true, "accepted_meaning": true}],
                "readings": [{"reading": "ひ", "primary": true, "accepted_reading": true}]
              }
            }
          ]
        }
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
