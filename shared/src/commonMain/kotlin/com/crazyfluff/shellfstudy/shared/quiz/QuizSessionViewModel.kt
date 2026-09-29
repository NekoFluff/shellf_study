package com.crazyfluff.shellfstudy.shared.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.data.AppSettings
import com.crazyfluff.shellfstudy.shared.data.model.QuizDisplayItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * The half of a quiz session that is the same for lessons and reviews: keeping the current question's
 * state up to date, answering it, revealing a gated answer, and stopping the clock when the screen
 * goes away.
 *
 * It is shaped around [QuizSessionPhase]/[QuizSessionState] rather than around a session-wide state
 * machine, because that is where the duplication actually was: the two features' *policies* differ
 * (a lesson submission is sent when the item is done, a review's waits for Continue; a review caps
 * items in flight, a lesson works through batches), and those stay in the subclasses. What does not
 * differ is how a question is read out of the state and written back.
 *
 * Subclasses keep their own state flow and hand it to the base, so nothing about how a screen
 * observes its ViewModel changes.
 */
abstract class QuizSessionViewModel<
    T : QuizDisplayItem,
    P : QuizSessionPhase<T, P>,
    S : QuizSessionState<S, P, T>,
    > : ViewModel() {

    /** The session's clock — paused by [onCleared]. */
    protected abstract val sessionTiming: QuizSessionTiming

    /** Stopped by [onCleared], so leaving mid-question does not keep playing an answer's audio. */
    protected abstract val pronunciationAudioPlayer: PronunciationAudioPlayer

    /**
     * The screen's state, owned by the subclass so that how a screen observes its ViewModel is
     * unchanged by this base class. Named as the subclasses already named it.
     */
    protected abstract val _uiState: MutableStateFlow<S>

    /**
     * The settings the per-answer paths read, kept warm by a collector in each subclass's `init`.
     * A fresh `settingsRepository.settings.first()` on this path measurably janked the post-submit
     * animation, which is why it is a field rather than a read.
     */
    protected abstract val latestSettings: AppSettings

    /** The grading guard, one per session — see [QuizGradingGuard] for why a second concurrent
     *  submission is dropped rather than queued. */
    protected val gradingGuard = QuizGradingGuard(viewModelScope)

    /** The question currently on screen, or null when the screen is not asking one. */
    protected val quizPhase: P? get() = _uiState.value.quizPhase

    /** Applies [transform] to the current question, or does nothing when there is none. */
    protected fun updateQuiz(transform: (P) -> P) {
        _uiState.update { state ->
            val phase = state.quizPhase ?: return@update state
            state.withQuizPhase(transform(phase))
        }
    }

    protected fun updateQuizTiming(transform: (QuizTimingUiState) -> QuizTimingUiState) {
        updateQuiz { it.withTiming(transform(it.timing)) }
    }

    open fun onAnswerInputChange(value: String) {
        updateQuiz { it.withAnswerInput(value) }
    }

    /** A real flip, driven by the swipe handle and the gesture settle. */
    open fun toggleDetails() {
        updateQuiz { it.withDetailsExpanded(!it.isDetailsExpanded) }
    }

    /**
     * The definitively-directional close used by the scrim tap, the close button and the back handler.
     * Those always mean "close", never "toggle", so this must not reopen a sheet that is already
     * collapsed.
     */
    open fun closeDetails() {
        updateQuiz { it.withDetailsExpanded(false) }
    }

    /**
     * Applies a grade to the feature's own bookkeeping — the lesson's started-item submission, the
     * review's deferred pending submission and its rank-change prediction — and publishes the
     * feedback state. Deliberately the extension point: what happens *after* a grade is the part the
     * two features genuinely do differently.
     */
    protected abstract suspend fun gradeAnswer(
        item: T,
        type: QuestionType,
        isCorrect: Boolean,
        candidates: List<String>,
        wasCloseMatch: Boolean,
        isGiveUp: Boolean
    )

    /** Publishes the reading hint and autoplay audio for a just-graded (and, if gated, now revealed)
     *  reading question. Called only when the grade is visible. */
    protected abstract fun publishReadingRevealEffects(
        item: T,
        type: QuestionType,
        candidates: List<String>,
        settings: AppSettings
    )

    /**
     * Grades whatever is typed in. The guard drops a second submission while one is in flight: a fast
     * double-tap, or an IME action landing twice, must not grade the same question twice.
     */
    open fun submitAnswer() {
        val phase = quizPhase ?: return
        if (phase.feedback != null) return
        if (phase.answerInput.isBlank()) return
        val item = phase.currentItem
        val type = phase.currentQuestionType

        gradingGuard.launchIfIdle {
            val candidates = candidatesFor(item.meanings, item.auxiliaryMeanings, item.readings, type)
            val outcome = evaluateAnswer(
                phase.answerInput, type, item.meanings, item.auxiliaryMeanings, item.readings,
                closeEnoughEnabled = latestSettings.closeEnoughAnswersEnabled
            )
            when (outcome) {
                AnswerOutcome.TypeMismatch ->
                    updateQuiz { it.withAnswerTypeMismatchCount(it.answerTypeMismatchCount + 1) }
                is AnswerOutcome.Graded ->
                    gradeAnswer(
                        item, type, outcome.isCorrect, candidates,
                        wasCloseMatch = outcome.wasCloseMatch, isGiveUp = false
                    )
            }
        }
    }

    /** Gives up on the current question — graded as a miss without requiring a typed guess. */
    open fun dontKnowAnswer() {
        val phase = quizPhase ?: return
        if (phase.feedback != null) return
        val item = phase.currentItem
        val type = phase.currentQuestionType
        val candidates = candidatesFor(item.meanings, item.auxiliaryMeanings, item.readings, type)

        gradingGuard.launchIfIdle {
            gradeAnswer(item, type, isCorrect = false, candidates, wasCloseMatch = false, isGiveUp = true)
        }
    }

    /**
     * Reveals a gated wrong answer's correct text. No guard: this mutates display state, not grading
     * or SRS state — but the reading question's audio and hint were themselves withheld at grading
     * time, so revealing now is what triggers them.
     */
    open fun revealAnswer() {
        val phase = quizPhase ?: return
        if (phase.feedback == null || phase.answerRevealed) return
        updateQuiz { it.withAnswerRevealed(true) }
        val item = phase.currentItem
        val type = phase.currentQuestionType
        val candidates = candidatesFor(item.meanings, item.auxiliaryMeanings, item.readings, type)
        publishReadingRevealEffects(item, type, candidates, latestSettings)
    }

    /**
     * Pauses the session's clock and stops any audio still playing.
     *
     * Only [sessionTiming] is paused, matching what both ViewModels did: the question clock's
     * accumulated segments are frozen into the persisted snapshot at each grade, so leaving
     * mid-question has nothing left to stop there.
     */
    override fun onCleared() {
        super.onCleared()
        sessionTiming.pause()
        pronunciationAudioPlayer.stop()
    }
}
