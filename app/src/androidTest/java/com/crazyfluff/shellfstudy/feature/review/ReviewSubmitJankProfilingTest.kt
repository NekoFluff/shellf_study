package com.crazyfluff.shellfstudy.feature.review

import com.crazyfluff.shellfstudy.shared.feature.review.ReviewUiState
import com.crazyfluff.shellfstudy.shared.feature.review.ReviewScreen
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.crazyfluff.shellfstudy.shared.data.model.RankChange
import com.crazyfluff.shellfstudy.shared.data.model.ReviewItem
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.crazyfluff.shellfstudy.shared.quiz.AnswerFeedback
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.system.measureNanoTime

/**
 * Diagnostic, not a regression gate. Reproduces the exact `ReviewUiState` transition
 * `ReviewViewModel.gradeAnswer` produces on Submit — `feedback`/`rankChange` going non-null,
 * which flips `SubjectDetailSheet.active` true and starts `RankChangeChip`'s enter animation —
 * using the same stateless `ReviewScreen` harness as [ReviewScreenTest], with no ViewModel/Room/
 * DataStore/Hilt involved. Steps the Compose test clock frame-by-frame and logs each frame's
 * measured wall-clock cost.
 *
 * Must run on a device/emulator: Robolectric's Compose clock is a JVM shadow of frame production,
 * not real GPU/CPU cost — this is a deliberate exception to this project's usual "screen tests
 * default to Robolectric" convention (see CLAUDE.md), not an oversight.
 *
 * The per-frame numbers this test logs run noticeably higher than what the device's actual
 * Choreographer reports for the same transition (confirmed by cross-checking against a real
 * Perfetto capture) — `ComposeTestRule.mainClock.advanceTimeByFrame()` bundles in test-harness/
 * instrumentation synchronization overhead alongside real recomposition cost. Treat the logged
 * numbers as relative (did this change get cheaper or more expensive, especially question-over-
 * question in [submitAcrossTwoQuestions_secondSubmitFrameCostIsLogged]), not as absolute frame
 * times — for the latter, capture a real System Trace instead. Assertions are accordingly a loose
 * ceiling only (catching an actual hang/deadlock), not a tight frame budget.
 */
class ReviewSubmitJankProfilingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleItem = ReviewItem(
        assignmentId = 1,
        subjectId = 440,
        subjectType = SubjectType.KANJI,
        characters = "水",
        level = 3,
        srsStage = 3,
        meanings = listOf("Water"),
        readings = listOf("みず")
    )

    private val preSubmitState = ReviewUiState(
        isLoading = false,
        totalCount = 1,
        remainingCount = 1,
        currentItem = sampleItem,
        currentQuestionType = QuestionType.MEANING,
        answerInput = "Water"
    )

    private fun logFrames(label: String, frameCount: Int = 15): Double {
        var maxMs = 0.0
        repeat(frameCount) { i ->
            val elapsedNanos = measureNanoTime { composeTestRule.mainClock.advanceTimeByFrame() }
            val ms = elapsedNanos / 1_000_000.0
            maxMs = maxOf(maxMs, ms)
            Log.d(TAG, "$label frame $i: ${"%.2f".format(ms)} ms")
        }
        Log.d(TAG, "$label max frame: ${"%.2f".format(maxMs)} ms")
        return maxMs
    }

    @Test
    fun submitTransition_fullFeedbackAndRankChange_frameTimingsAreLogged() {
        composeTestRule.mainClock.autoAdvance = false
        var uiState by mutableStateOf(preSubmitState)
        composeTestRule.setContent { ReviewScreen(uiState = uiState, onEvent = {}) }
        composeTestRule.mainClock.advanceTimeByFrame()

        // Mirrors ReviewViewModel.gradeAnswer's single _uiState.update — feedback, remainingCount,
        // and rankChange all flip together, exactly as they do on a real Submit tap.
        uiState = uiState.copy(
            feedback = AnswerFeedback(isCorrect = true, correctAnswer = "Water"),
            remainingCount = 0,
            rankChange = RankChange(from = SrsStage.APPRENTICE_1, to = SrsStage.APPRENTICE_2)
        )
        val maxMs = logFrames("fullTransition")
        assertTrue(
            "A frame took implausibly long ($maxMs ms) during the full submit transition — investigate",
            maxMs < 500.0
        )
    }

    @Test
    fun subjectDetailSheetActivating_alone_frameTimingsAreLogged() {
        composeTestRule.mainClock.autoAdvance = false
        var uiState by mutableStateOf(preSubmitState)
        composeTestRule.setContent { ReviewScreen(uiState = uiState, onEvent = {}) }
        composeTestRule.mainClock.advanceTimeByFrame()

        // feedback alone flips SubjectDetailSheet.active (see ReviewScreen.kt); rankChange stays
        // null so RankChangeChip's AnimatedVisibility never triggers — isolates the sheet's cost.
        uiState = uiState.copy(
            feedback = AnswerFeedback(isCorrect = true, correctAnswer = "Water"),
            remainingCount = 0
        )
        val maxMs = logFrames("subjectDetailSheetAlone")
        assertTrue(
            "A frame took implausibly long ($maxMs ms) activating SubjectDetailSheet alone — investigate",
            maxMs < 500.0
        )
    }

    @Test
    fun rankChangeChipAppearing_alone_frameTimingsAreLogged() {
        composeTestRule.mainClock.autoAdvance = false
        var uiState by mutableStateOf(preSubmitState)
        composeTestRule.setContent { ReviewScreen(uiState = uiState, onEvent = {}) }
        composeTestRule.mainClock.advanceTimeByFrame()

        // rankChange alone drives RankChangeChip's AnimatedVisibility; feedback stays null so
        // SubjectDetailSheet.active never flips — isolates the chip's animation cost.
        uiState = uiState.copy(
            rankChange = RankChange(from = SrsStage.APPRENTICE_1, to = SrsStage.APPRENTICE_2)
        )
        val maxMs = logFrames("rankChangeChipAlone")
        assertTrue(
            "A frame took implausibly long ($maxMs ms) showing RankChangeChip alone — investigate",
            maxMs < 500.0
        )
    }

    @Test
    fun submitAcrossTwoQuestions_secondSubmitFrameCostIsLogged() {
        // ReviewViewModel.advanceToNextQuestion resets feedback/rankChange to null every question
        // (see ReviewViewModel.kt around line 436/455), so within one real session `active` and
        // `rankChange` cycle false->true->false->true repeatedly, not just once. The single-flip
        // tests above only ever measure a FIRST-EVER flip in a fresh composition — they can't tell
        // whether a fix actually stopped the cost from recurring on question 2 onward. This test
        // submits twice in the same composition and logs both, so the two are directly comparable.
        // (A real Perfetto capture of this exact scenario confirmed question 2 is genuinely cheap —
        // ~6ms max vs. ~32ms for question 1 — even though this test's own harness-noise-inflated
        // numbers don't show that gap as cleanly; see the class doc comment.)
        composeTestRule.mainClock.autoAdvance = false
        var uiState by mutableStateOf(preSubmitState)
        composeTestRule.setContent { ReviewScreen(uiState = uiState, onEvent = {}) }
        composeTestRule.mainClock.advanceTimeByFrame()

        uiState = uiState.copy(
            feedback = AnswerFeedback(isCorrect = true, correctAnswer = "Water"),
            remainingCount = 0,
            rankChange = RankChange(from = SrsStage.APPRENTICE_1, to = SrsStage.APPRENTICE_2)
        )
        val firstSubmitMaxMs = logFrames("question1Submit")

        // Mirrors advanceToNextQuestion: feedback/rankChange reset to null, a new item loads.
        uiState = uiState.copy(feedback = null, rankChange = null, remainingCount = 1)
        repeat(15) { composeTestRule.mainClock.advanceTimeByFrame() }

        uiState = uiState.copy(
            feedback = AnswerFeedback(isCorrect = true, correctAnswer = "Water"),
            remainingCount = 0,
            rankChange = RankChange(from = SrsStage.APPRENTICE_1, to = SrsStage.APPRENTICE_2)
        )
        val secondSubmitMaxMs = logFrames("question2Submit")

        Log.d(TAG, "question1Submit max=$firstSubmitMaxMs ms vs question2Submit max=$secondSubmitMaxMs ms")
        assertTrue(
            "A frame took implausibly long ($secondSubmitMaxMs ms) on the second submit — investigate",
            secondSubmitMaxMs < 500.0
        )
    }

    private companion object {
        const val TAG = "ReviewSubmitJankProfiling"
    }
}
