package com.crazyfluff.shellfstudy.feature.quiz

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.crazyfluff.shellfstudy.fakes.FakePronunciationAudioPlayer
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.lifecycle.AppForegroundTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.fakes.TestRepositories
import com.crazyfluff.shellfstudy.fakes.jsonResponse
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.AnswerReadingHint
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType

/** The two responses a session start needs, as the suite's own fixtures render them. */
data class QuizQueueFixtures(val assignments: String, val subjects: String)

/**
 * A quiz session under test, seen through the actions the shared scenarios drive.
 *
 * The ViewModels satisfy this structurally rather than nominally — each suite adapts its own
 * ViewModel, so production code carries no interface that exists only for tests.
 */
interface QuizSessionSubject<STATE : Any> {
    val uiState: StateFlow<STATE>
    fun onAnswerInputChange(value: String)
    fun submitAnswer()
    fun dontKnowAnswer()
    fun onContinue()
    fun revealAnswer()
    fun undoLastAnswer()
    fun toggleDetails()
    fun closeDetails()
}

/** What a scenario asserts on: the question on screen, read out of a feature's own phase. */
data class QuizQuestionView(
    val questionType: QuestionType,
    /** The meanings/readings an answer is graded against, for scenarios that answer correctly. */
    val answers: List<String>,
    val feedbackPresent: Boolean,
    val feedbackIsCorrect: Boolean?,
    val answerRevealed: Boolean,
    val answerHint: AnswerReadingHint?,
    val remainingCount: Int,
    val answerTypeMismatchCount: Int
)

/**
 * The behaviours a quiz session has whether it is teaching or reviewing — the gating rules, giving up,
 * the answer-type rejection, the timers, undo. Each was written twice, once in `LessonViewModelTest`
 * and once in `ReviewViewModelTest`, as the same steps against two different state types: measured
 * across the twenty-two scenarios the two suites share, the copies differ by the phase they cast to
 * (179 lines), the steps that bring a lesson to its first question (42), one counter's name (7), local
 * variable names and await shapes — and only a handful of genuine behavioural differences.
 *
 * This class holds each scenario once. A suite supplies three things: how to adapt its ViewModel, how
 * to dispatch a session start's fetches, and how to read a question out of its own state. Scenarios
 * stay methods rather than inherited `@Test`s so each suite keeps its test names — they read like the
 * feature they describe, and a failure names the feature's suite.
 */
abstract class QuizSessionContractTest<STATE : Any> {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    protected lateinit var server: MockWebServer
    protected lateinit var repositories: TestRepositories
    protected lateinit var dataStore: DataStore<Preferences>
    protected lateinit var settingsRepository: SettingsRepository
    protected lateinit var assignmentRepository: AssignmentRepository
    protected lateinit var outboxRepository: OutboxRepository
    protected lateinit var lastSessionSummaryRepository: LastSessionSummaryRepository
    protected lateinit var pronunciationAudioPlayer: FakePronunciationAudioPlayer
    protected lateinit var appForegroundTracker: AppForegroundTracker

    /**
     * Stands up the parts both suites wire identically: the mock server, a temp-file DataStore, the
     * repository graph over it, and the audio and foreground fakes. Each suite then adds what its
     * feature needs — its own session repository, and a settings repository over either this DataStore
     * or a separate one.
     */
    protected fun startHarness() {
        server = MockWebServer()
        server.start()
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(mainDispatcherRule.dispatcher + SupervisorJob()),
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        repositories = buildTestRepositories(server.url("/").toString(), defaultDispatcher = mainDispatcherRule.dispatcher)
        assignmentRepository = repositories.assignmentRepository
        outboxRepository = OutboxRepository(repositories.outboxDao, repositories.outboxSyncScheduler, dataStore)
        lastSessionSummaryRepository = LastSessionSummaryRepository(dataStore, Json { ignoreUnknownKeys = true })
        pronunciationAudioPlayer = FakePronunciationAudioPlayer()
        appForegroundTracker = AppForegroundTracker()
    }

    /** The suite's fixtures for a queue of radicals — see [QuizQueueFixtures]. */
    protected abstract val radicalQueue: QuizQueueFixtures

    /** The suite's fixtures for a vocabulary queue, whose reading question can be answered wrong. */
    protected abstract val vocabQueue: QuizQueueFixtures

    /** The suite's fixtures for a kanji queue, where a reading question has no hint to show. */
    protected abstract val kanjiQueue: QuizQueueFixtures

    protected abstract fun createSubject(scope: TestScope): QuizSessionSubject<STATE>

    /** Routes the fetches a session start performs. Named apart from each suite's own `dispatch`,
     *  which takes extra per-feature responses and so is not the same signature. */
    protected abstract fun dispatchSessionFetches(assignments: MockResponse, subjects: MockResponse)

    /** The question on screen, or null when this state is showing something else — a lesson studying a
     *  card, a review admitting its next item, a summary. */
    protected abstract fun question(state: STATE): QuizQuestionView?

    /**
     * Brings a session to its first live question. A review is there as soon as its queue loads; a
     * lesson must start a session and step past its first study card.
     */
    protected abstract suspend fun startSession(
        subject: QuizSessionSubject<STATE>,
        states: ReceiveTurbine<STATE>
    )

    /**
     * Runs [scenario] with a session over [fixtures] started and its first question on screen.
     * [beforeSession] runs first, for a scenario that needs a display setting in place before the
     * ViewModel loads — its settings collector reads the stored value as the session starts.
     */
    protected fun quizSession(
        fixtures: QuizQueueFixtures,
        beforeSession: suspend () -> Unit = {},
        scenario: suspend QuizScenario<STATE>.() -> Unit
    ) = runTest(mainDispatcherRule.dispatcher) {
        beforeSession()
        dispatchSessionFetches(jsonResponse(fixtures.assignments), jsonResponse(fixtures.subjects))
        val subject = createSubject(this)

        subject.uiState.test {
            startSession(subject, this)
            val scenarioBody = QuizScenario(subject, this, ::question)
            // A scenario starts with a live question on screen: a lesson's start leaves it on a study
            // card and a review emits a few queue states, and typing into either would be dropped —
            // the shared flow no-ops when no question is showing.
            scenarioBody.awaitQuestion().also { scenarioBody.startFrom(it) }
            scenarioBody.scenario()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The session plus its state stream, with the awaits the scenarios need. Every mutation below
     * emits at least one state, and the scenarios that assert after one read the next question rather
     * than assuming how many emissions it took — a lesson and a review emit different numbers, which
     * is most of why the two copies drifted apart in the first place.
     */
    class QuizScenario<STATE : Any>(
        private val subject: QuizSessionSubject<STATE>,
        private val states: ReceiveTurbine<STATE>,
        private val question: (STATE) -> QuizQuestionView?
    ) {
        /** The question the scenario is looking at — the one the harness waited for before starting. */
        private lateinit var current: QuizQuestionView

        internal fun startFrom(view: QuizQuestionView) {
            current = view
        }

        /** Types [text] into the answer field. */
        suspend fun type(text: String) {
            subject.onAnswerInputChange(text)
            states.awaitItem()
        }

        suspend fun submit() = subject.submitAnswer()

        suspend fun giveUp() = subject.dontKnowAnswer()

        suspend fun reveal() = subject.revealAnswer()

        suspend fun undo() = subject.undoLastAnswer()

        suspend fun continueToNextQuestion() {
            subject.onContinue()
        }

        /** The next state that is a question, whatever the ones in between were. */
        suspend fun awaitQuestion(matching: (QuizQuestionView) -> Boolean = { true }): QuizQuestionView {
            while (true) {
                val view = states.awaitItem().let(question)
                if (view != null && matching(view)) {
                    current = view
                    return view
                }
            }
        }

        /** Answers correctly until a question of [type] is on screen, and returns it — starting from
         *  the question already showing, which may be the one asked for. */
        suspend fun awaitQuestionOfType(type: QuestionType): QuizQuestionView {
            var view = current
            var guard = 0
            while (view.questionType != type && guard++ < 20) {
                type(view.answers.first())
                submit()
                awaitQuestion { it.feedbackPresent }
                continueToNextQuestion()
                view = awaitQuestion()
            }
            check(view.questionType == type) { "no $type question arrived within $guard answers" }
            return view
        }

        /** The next state that is a question with feedback showing — a grade has landed. */
        suspend fun awaitGraded(): QuizQuestionView = awaitQuestion { it.feedbackPresent }
    }

    // ------------------------------------------------------------------ scenarios

    /**
     * A wrong answer's text waits for a reveal tap when the requirement is on, and revealing is what
     * lifts the gate. The reading hint and audio are withheld with it — see
     * [wrongReadingHintWaitsForReveal].
     */
    protected fun wrongAnswerWaitsForReveal() = quizSession(
        fixtures = radicalQueue,
        beforeSession = { settingsRepository.setRequireTapToRevealMeaningAnswer(true) }
    ) {
        type("wrong")
        submit()
        val graded = awaitGraded()
        assertThat(graded.feedbackIsCorrect).isFalse()
        assertThat(graded.answerRevealed).isFalse()

        reveal()
        assertThat(awaitQuestion().answerRevealed).isTrue()
    }

    /**
     * A close match is graded correct, so it is never gated — the requirement is about wrong answers.
     */
    protected fun closeMatchIsNeverGated() = quizSession(
        fixtures = radicalQueue,
        beforeSession = { settingsRepository.setRequireTapToRevealMeaningAnswer(true) }
    ) {
        // "Mouth" (the correct answer) with its last two letters transposed — still correct with
        // closeEnoughAnswersEnabled at its default (true).
        type("Mouht")
        submit()
        val graded = awaitGraded()
        assertThat(graded.feedbackIsCorrect).isTrue()
        assertThat(graded.answerRevealed).isTrue()
    }

    /** Giving up reveals the answer whatever the setting says — there was no guess to protect. */
    protected fun giveUpAlwaysReveals() = quizSession(
        fixtures = radicalQueue,
        beforeSession = { settingsRepository.setRequireTapToRevealMeaningAnswer(true) }
    ) {
        giveUp()
        val graded = awaitGraded()
        assertThat(graded.feedbackIsCorrect).isFalse()
        assertThat(graded.answerRevealed).isTrue()
    }

    /**
     * A wrong reading question's hint and audio are withheld until the reveal, which is the half of
     * the gating rule a meaning question cannot show: the answer text itself is the other half, and
     * it is visible in the state either way.
     */
    protected fun wrongReadingWaitsForRevealBeforeShowingItsHint() = quizSession(
        fixtures = vocabQueue,
        beforeSession = {
            settingsRepository.setShowAnswerReadingPitchAccent(true)
            settingsRepository.setRequireTapToRevealReadingAnswer(true)
        }
    ) {
        awaitQuestionOfType(QuestionType.READING)

        // A genuine miss, not a typo — "けんい" against a reading of "けんあ".
        type("けんい")
        submit()
        val graded = awaitGraded()
        assertThat(graded.feedbackIsCorrect).isFalse()
        assertThat(graded.answerRevealed).isFalse()
        assertThat(graded.answerHint).isNull()

        reveal()
        val revealed = awaitQuestion()
        assertThat(revealed.answerRevealed).isTrue()
        assertThat(revealed.answerHint?.reading).isEqualTo("けんあ")
    }
}
