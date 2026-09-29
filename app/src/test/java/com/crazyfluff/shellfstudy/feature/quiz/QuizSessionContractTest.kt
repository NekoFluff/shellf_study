package com.crazyfluff.shellfstudy.feature.quiz

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.crazyfluff.shellfstudy.fakes.FakeLifecycleOwner
import com.crazyfluff.shellfstudy.fakes.FakePronunciationAudioPlayer
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.PlaybackState
import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
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
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentUiState
import com.crazyfluff.shellfstudy.shared.quiz.QuizTimingUiState
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
    fun abandonSession()
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
    /** The pitch accents rendered alongside the hint. A lesson keeps them in a top-level map its hint
     *  folds in at render time; a review folds them into the hint itself. */
    val pitchAccents: PitchAccentUiState?,
    val remainingCount: Int,
    val answerTypeMismatchCount: Int,
    /** What the learner has typed for this question so far — an undo clears it. */
    val answerInput: String,
    /** The pause-aware timers, shared by both features as [QuizTimingUiState]. */
    val timing: QuizTimingUiState
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

    /** The suite's fixtures for a kanji queue whose subjects carry pronunciation audio. */
    protected abstract val kanjiAudioQueue: QuizQueueFixtures

    protected abstract fun createSubject(scope: TestScope): QuizSessionSubject<STATE>

    /** Routes the fetches a session start performs. Named apart from each suite's own `dispatch`,
     *  which takes extra per-feature responses and so is not the same signature. */
    protected abstract fun dispatchSessionFetches(assignments: MockResponse, subjects: MockResponse)

    /** The question on screen, or null when this state is showing something else — a lesson studying a
     *  card, a review admitting its next item, a summary. */
    protected abstract fun question(state: STATE): QuizQuestionView?

    /** Whether this state is the report a finished session ends on. */
    protected abstract fun sessionFinished(state: STATE): Boolean

    /** Whether this state is an abandoned session. Both features keep this flag beside the phase
     *  rather than in it — the screen has to navigate away whatever else the phase says — but spell it
     *  differently: a lesson an exit request, a review a boolean. */
    protected abstract fun isAbandoned(state: STATE): Boolean

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
     * [afterSession] runs once the state collection has ended, for what the session left behind in a
     * repository: a lesson's summary is saved from a scope of its own, so it is only reliably there
     * after the scenario stopped collecting.
     */
    protected fun quizSession(
        fixtures: QuizQueueFixtures,
        beforeSession: suspend () -> Unit = {},
        afterSession: suspend () -> Unit = {},
        scenario: suspend QuizScenario<STATE>.() -> Unit
    ) = runTest(mainDispatcherRule.dispatcher) {
        beforeSession()
        dispatchSessionFetches(jsonResponse(fixtures.assignments), jsonResponse(fixtures.subjects))
        val subject = createSubject(this)

        subject.uiState.test {
            startSession(subject, this)
            val scenarioBody = QuizScenario(subject, this, ::question, ::sessionFinished, ::isAbandoned)
            // A scenario starts with a live question on screen: a lesson's start leaves it on a study
            // card and a review emits a few queue states, and typing into either would be dropped —
            // the shared flow no-ops when no question is showing.
            scenarioBody.awaitQuestion().also { scenarioBody.startFrom(it) }
            scenarioBody.scenario()
            cancelAndIgnoreRemainingEvents()
        }
        afterSession()
    }

    /**
     * Waits for the player to be handed audio and returns it. Whether a reading answer plays its
     * pronunciation is decided by a display setting, and a real DataStore read settles on its own IO
     * dispatcher — after the Main-dispatcher ViewModel work a scenario's awaits have already run
     * through — so reading `playedAudios` straight after a grade would race the setting. The player's
     * state flow is the arrival signal; a player that is never asked to play times out instead of
     * passing.
     */
    protected suspend fun awaitPlayedAudio(): PronunciationAudio {
        pronunciationAudioPlayer.state.test {
            while (awaitItem() != PlaybackState.PLAYING) {
                // Nothing to do but wait for the side effect to land.
            }
            cancelAndIgnoreRemainingEvents()
        }
        return pronunciationAudioPlayer.playedAudios.last()
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
        private val question: (STATE) -> QuizQuestionView?,
        private val sessionFinished: (STATE) -> Boolean,
        private val isAbandoned: (STATE) -> Boolean
    ) {
        /** The question the scenario is looking at — the one the harness waited for before starting. */
        private lateinit var current: QuizQuestionView

        /** The question on screen right now, without waiting for a new state. */
        val questionOnScreen: QuizQuestionView get() = current

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

        suspend fun abandonSession() = subject.abandonSession()

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

        /** Waits out whatever else the session emits until its report is on screen. */
        suspend fun awaitSessionFinished(): STATE = awaitState(sessionFinished)

        /** Waits out whatever else the session emits until it is marked abandoned. */
        suspend fun awaitAbandoned(): STATE = awaitState(isAbandoned)

        private suspend fun awaitState(matching: (STATE) -> Boolean): STATE {
            while (true) {
                val state = states.awaitItem()
                if (matching(state)) return state
            }
        }
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

    /**
     * An answer of the wrong type — a reading typed into a meaning question, or the reverse — is
     * rejected outright rather than graded as a miss: the mismatch is counted, no feedback appears,
     * and the question stays put with the queue untouched. Three scenarios across the two suites were
     * this same body, differing only in which type was asked for and which answers were typed.
     */
    protected fun wrongTypeAnswerIsRejectedNotGraded(
        fixtures: QuizQueueFixtures,
        target: QuestionType,
        answerOfTheWrongType: String,
        correctAnswer: String
    ) = quizSession(fixtures) {
        val question = awaitQuestionOfType(target)
        val remainingBeforeMismatch = question.remainingCount

        type(answerOfTheWrongType)
        submit()
        val mismatch = awaitQuestion()
        assertThat(mismatch.answerTypeMismatchCount).isEqualTo(1)
        assertThat(mismatch.feedbackPresent).isFalse()
        assertThat(mismatch.remainingCount).isEqualTo(remainingBeforeMismatch)

        type(correctAnswer)
        submit()
        assertThat(awaitGraded().feedbackIsCorrect).isTrue()
    }

    /**
     * A correct reading answer surfaces the reading for the hint when the setting is on — and no pitch
     * pattern with it, because the fixture is a fabricated word the bundled dictionary cannot know.
     */
    protected fun readingHintShowsTheReading() = quizSession(
        fixtures = vocabQueue,
        beforeSession = { settingsRepository.setShowAnswerReadingPitchAccent(true) }
    ) {
        awaitQuestionOfType(QuestionType.READING)
        type("けんあ")
        submit()
        val graded = awaitGraded()
        assertThat(graded.feedbackIsCorrect).isTrue()
        assertThat(graded.answerHint?.reading).isEqualTo("けんあ")
        // "件亜" is fabricated — guaranteed absent from the real bundled pitch-accent dictionary, so
        // the reading still surfaces but with no pitch pattern alongside it.
        assertThat(graded.pitchAccents).isEqualTo(PitchAccentUiState.Unavailable)
    }

    /** With the setting off, the reading is not published at all. */
    protected fun readingHintStaysEmptyWithTheSettingOff() = quizSession(vocabQueue) {
        awaitQuestionOfType(QuestionType.READING)
        type("けんあ")
        submit()
        val graded = awaitGraded()
        assertThat(graded.feedbackIsCorrect).isTrue()
        assertThat(graded.answerHint).isNull()
    }

    /**
     * Only a reading question gets a hint. The setting is on in both of these, so a meaning question
     * publishing one would be the bug they exist to catch.
     */
    protected fun meaningQuestionNeverShowsAReadingHint() = quizSession(
        fixtures = vocabQueue,
        beforeSession = { settingsRepository.setShowAnswerReadingPitchAccent(true) }
    ) {
        awaitQuestionOfType(QuestionType.MEANING)
        type("Testword")
        submit()
        val graded = awaitGraded()
        assertThat(graded.feedbackIsCorrect).isTrue()
        assertThat(graded.answerHint).isNull()
    }

    /**
     * Pitch accent is a word-level concept and the bundled source is keyed by whole headwords, so a
     * kanji's reading question never gets a hint even with the setting on.
     */
    protected fun kanjiReadingQuestionNeverShowsAHint() = quizSession(
        fixtures = kanjiQueue,
        beforeSession = { settingsRepository.setShowAnswerReadingPitchAccent(true) }
    ) {
        awaitQuestionOfType(QuestionType.READING)
        type("みず")
        submit()
        val graded = awaitGraded()
        assertThat(graded.feedbackIsCorrect).isTrue()
        assertThat(graded.answerHint).isNull()
    }

    /**
     * The two question types are gated independently: requiring a reveal tap for one must not gate the
     * other. Both halves are the same scenario with the requirement and the asked-for type swapped.
     */
    protected fun gatingOneQuestionTypeDoesNotGateTheOther(
        gatedType: QuestionType,
        askedType: QuestionType
    ) = quizSession(
        fixtures = kanjiAudioQueue,
        beforeSession = {
            if (gatedType == QuestionType.MEANING) {
                settingsRepository.setRequireTapToRevealMeaningAnswer(true)
            } else {
                settingsRepository.setRequireTapToRevealReadingAnswer(true)
            }
        }
    ) {
        awaitQuestionOfType(askedType)

        type("wrong")
        submit()
        val graded = awaitGraded()
        assertThat(graded.feedbackIsCorrect).isFalse()
        assertThat(graded.answerRevealed).isTrue()
    }

    /** A correct reading answer plays its pronunciation — autoplay is on unless turned off. */
    protected fun answeringAReadingQuestionAutoplaysItsPronunciation() = quizSession(kanjiAudioQueue) {
        awaitQuestionOfType(QuestionType.READING)

        type("mizu")
        submit()
        assertThat(awaitGraded().feedbackIsCorrect).isTrue()

        assertThat(awaitPlayedAudio().url).isEqualTo(KANJI_AUDIO_URL)
    }

    /**
     * A wrong reading answer's audio waits with its text and its hint: nothing plays until the reveal
     * tap, which plays it. The size check after the reveal is what makes the emptiness before it mean
     * something — a second, wrongly-timed play would show up as two entries.
     */
    protected fun wrongReadingWithholdsItsAudioUntilRevealed() = quizSession(
        fixtures = kanjiAudioQueue,
        beforeSession = { settingsRepository.setRequireTapToRevealReadingAnswer(true) }
    ) {
        awaitQuestionOfType(QuestionType.READING)

        type("wrong")
        submit()
        assertThat(awaitGraded().feedbackIsCorrect).isFalse()
        assertThat(pronunciationAudioPlayer.playedAudios).isEmpty()

        reveal()
        assertThat(awaitPlayedAudio().url).isEqualTo(KANJI_AUDIO_URL)
        assertThat(pronunciationAudioPlayer.playedAudios).hasSize(1)
    }

    /**
     * Backgrounding pauses the session's total timer and returning resumes it: the time spent away is
     * not folded in as active study time, and the accumulated total is carried across rather than
     * restarted.
     */
    protected fun backgroundingPausesTheTotalTimer() = quizSession(radicalQueue) {
        assertThat(questionOnScreen.timing.sessionActiveSegmentStartMs).isNotNull()

        appForegroundTracker.onStop(FakeLifecycleOwner)
        val paused = awaitQuestion()
        assertThat(paused.timing.sessionActiveSegmentStartMs).isNull()
        val elapsedWhilePaused = paused.timing.sessionActiveElapsedMs

        appForegroundTracker.onStart(FakeLifecycleOwner)
        val resumed = awaitQuestion()
        assertThat(resumed.timing.sessionActiveSegmentStartMs).isNotNull()
        assertThat(resumed.timing.sessionActiveElapsedMs).isEqualTo(elapsedWhilePaused)
    }

    /**
     * The per-question timer pauses and resumes exactly like the session one — a regression guard: it
     * used to be plain wall-clock (now minus a stored "question shown at") with no connection to the
     * foreground tracker, so backgrounding mid-question inflated both the live display and the
     * elapsedMs recorded for "slowest answers".
     */
    protected fun backgroundingPausesThePerQuestionTimer() = quizSession(radicalQueue) {
        assertThat(questionOnScreen.timing.questionActiveSegmentStartMs).isNotNull()

        appForegroundTracker.onStop(FakeLifecycleOwner)
        val paused = awaitQuestion()
        assertThat(paused.timing.questionActiveSegmentStartMs).isNull()
        val elapsedWhilePaused = paused.timing.questionActiveElapsedMs

        appForegroundTracker.onStart(FakeLifecycleOwner)
        val resumed = awaitQuestion()
        assertThat(resumed.timing.questionActiveSegmentStartMs).isNotNull()
        assertThat(resumed.timing.questionActiveElapsedMs).isEqualTo(elapsedWhilePaused)

        // Grading now must record an elapsedMs built on that same paused-and-resumed total, not a
        // fresh wall-clock read from when the question first appeared.
        type(resumed.answers.first())
        submit()
        assertThat(awaitGraded().timing.questionElapsedMs).isAtLeast(elapsedWhilePaused)
    }

    /**
     * Undoing a wrong reading answer takes the surfaced reading and its pitch accents down with the
     * feedback: a typo's hint must not linger behind the retry.
     */
    protected fun undoingAReadingAnswerClearsItsHint() = quizSession(
        fixtures = vocabQueue,
        beforeSession = { settingsRepository.setShowAnswerReadingPitchAccent(true) }
    ) {
        awaitQuestionOfType(QuestionType.READING)

        // A typo, not a genuine miss — undoable.
        type("けんい")
        submit()
        assertThat(awaitGraded().feedbackIsCorrect).isFalse()

        undo()
        val undone = awaitQuestion()
        assertThat(undone.feedbackPresent).isFalse()
        assertThat(undone.answerHint).isNull()
    }

    /**
     * Undoing a wrong answer takes it out of the session's record: the correct retry is the answer
     * that counts. What "counts" means is the one thing the two features keep differently — a lesson
     * writes a summary when the session ends, a review keeps a remaining-questions count on the state
     * — so each supplies its own bookkeeping check: [afterSession] once the session's states have
     * stopped being collected, or [verifyUndo] on the undone state.
     */
    protected fun undoingAMissKeepsItOutOfTheRecord(
        afterSession: suspend () -> Unit = {},
        verifyUndo: (QuizQuestionView) -> Unit = {}
    ) = quizSession(radicalQueue, afterSession = afterSession) {
        type("wrong")
        submit()
        assertThat(awaitGraded().feedbackIsCorrect).isFalse()

        undo()
        val undone = awaitQuestion()
        assertThat(undone.feedbackPresent).isFalse()
        assertThat(undone.answerInput).isEmpty()
        verifyUndo(undone)

        type(undone.answers.first())
        submit()
        assertThat(awaitGraded().feedbackIsCorrect).isTrue()

        continueToNextQuestion()
        awaitSessionFinished()
    }

    /**
     * Abandoning a session leaves nothing behind: the state says it was abandoned, and the queue it
     * was holding is gone from wherever that feature persists it — [loadPersistedSession] returns the
     * feature's own session type or null.
     */
    protected fun abandoningASessionClearsItsPersistedState(
        loadPersistedSession: suspend () -> Any?
    ) = quizSession(radicalQueue) {
        // There has to be something to clear: the harness only gets here with a live question on
        // screen, which is past the session's first persist.
        assertThat(loadPersistedSession()).isNotNull()

        abandonSession()
        awaitAbandoned()

        assertThat(loadPersistedSession()).isNull()
    }

    /**
     * The per-question clock freezes at the value it had when the answer was graded — what the
     * feedback screen keeps showing instead of ticking on — and the next question starts from zero
     * rather than inheriting it. The kanji fixtures give both features a genuine next question: a
     * meaning question followed by the same item's reading question.
     */
    protected fun gradingFreezesTheQuestionClockAndTheNextQuestionResetsIt() = quizSession(kanjiQueue) {
        assertThat(questionOnScreen.timing.questionElapsedMs).isNull()

        type(questionOnScreen.answers.first())
        submit()
        assertThat(awaitGraded().timing.questionElapsedMs).isNotNull()

        continueToNextQuestion()
        assertThat(awaitQuestion().timing.questionElapsedMs).isNull()
    }

    private companion object {
        /** The pronunciation the kanji in the audio-carrying fixtures carries. */
        const val KANJI_AUDIO_URL = "https://api.wanikani.com/audio/mizu.mp3"
    }
}
