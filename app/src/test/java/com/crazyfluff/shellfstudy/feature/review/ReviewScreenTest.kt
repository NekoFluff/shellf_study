package com.crazyfluff.shellfstudy.feature.review

import com.crazyfluff.shellfstudy.shared.feature.review.ReviewUiState
import com.crazyfluff.shellfstudy.shared.feature.review.ReviewScreen
import com.crazyfluff.shellfstudy.shared.feature.review.ReviewScreenEvent
import com.crazyfluff.shellfstudy.shared.feature.review.ReviewScreenTestTags
import com.crazyfluff.shellfstudy.shared.quiz.SlowAnswer
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.crazyfluff.shellfstudy.shared.data.model.RankChange
import com.crazyfluff.shellfstudy.shared.data.model.ReviewItem
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.formatElapsedClock
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.crazyfluff.shellfstudy.shared.quiz.AnswerFeedback
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType
import com.crazyfluff.shellfstudy.shared.quiz.QuizTimingUiState
import com.crazyfluff.shellfstudy.shared.feature.search.SearchOverlayTestTags
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Runs under Robolectric (JVM) — this screen is driven purely by state, no device features needed.
 * Moved here from the instrumented suite, which this project keeps for genuinely device-bound
 * tests only (Keystore, system back gesture). Pinned to SDK 35: Robolectric 4.15.1 doesn't yet
 * have shadows for this project's targetSdk (37).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ReviewScreenTest {

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

    /** Builds a [ReviewUiState] in the [ReviewUiState.Phase.Active] phase — the dominant fixture
     *  shape in this file, since most screen behavior is exercised while a question is on screen. */
    private fun activeState(
        item: ReviewItem = sampleItem,
        questionType: QuestionType = QuestionType.MEANING,
        answerInput: String = "",
        feedback: AnswerFeedback? = null,
        rankChange: RankChange? = null,
        undoCounter: Int = 0,
        isDetailsExpanded: Boolean = false,
        answerTypeMismatchCount: Int = 0,
        totalCount: Int = 0,
        remainingCount: Int = 0,
        isWrappingUp: Boolean = false,
        timing: QuizTimingUiState = QuizTimingUiState(),
        settings: ReviewUiState.DisplaySettings = ReviewUiState.DisplaySettings()
    ) = ReviewUiState(
        phase = ReviewUiState.Phase.Active(
            currentItem = item,
            currentQuestionType = questionType,
            answerInput = answerInput,
            feedback = feedback,
            rankChange = rankChange,
            undoCounter = undoCounter,
            isDetailsExpanded = isDetailsExpanded,
            answerTypeMismatchCount = answerTypeMismatchCount,
            totalCount = totalCount,
            remainingCount = remainingCount,
            isWrappingUp = isWrappingUp,
            timing = timing
        ),
        settings = settings
    )

    /** Builds a [ReviewUiState] in the [ReviewUiState.Phase.Complete] phase. */
    private fun completeState(
        sessionItemsReviewed: Int = 0,
        sessionItemsCorrectFirstTry: Int = 0,
        sessionMissedItems: List<ReviewItem> = emptyList(),
        sessionTotalElapsedMs: Long = 0L,
        sessionAverageTimePerItemMs: Long = 0L,
        sessionSlowestAnswers: List<SlowAnswer<ReviewItem>> = emptyList()
    ) = ReviewUiState(
        phase = ReviewUiState.Phase.Complete(
            sessionItemsReviewed = sessionItemsReviewed,
            sessionItemsCorrectFirstTry = sessionItemsCorrectFirstTry,
            sessionMissedItems = sessionMissedItems,
            sessionTotalElapsedMs = sessionTotalElapsedMs,
            sessionAverageTimePerItemMs = sessionAverageTimePerItemMs,
            sessionSlowestAnswers = sessionSlowestAnswers
        )
    )

    private fun setScreen(
        uiState: ReviewUiState,
        onAnswerInputChange: (String) -> Unit = {},
        onSubmit: () -> Unit = {},
        onDontKnow: () -> Unit = {},
        onContinue: () -> Unit = {},
        onUndo: () -> Unit = {},
        onToggleDetails: () -> Unit = {},
        onCloseDetails: () -> Unit = {},
        onRetry: () -> Unit = {},
        onWrapUp: () -> Unit = {},
        onAbandon: () -> Unit = {},
        onDone: () -> Unit = {},
        onBack: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            ReviewScreen(
                uiState = uiState,
                onEvent = { event ->
                    when (event) {
                        is ReviewScreenEvent.AnswerInputChange -> onAnswerInputChange(event.value)
                        ReviewScreenEvent.Submit -> onSubmit()
                        ReviewScreenEvent.DontKnow -> onDontKnow()
                        ReviewScreenEvent.Continue -> onContinue()
                        ReviewScreenEvent.Undo -> onUndo()
                        ReviewScreenEvent.ToggleDetails -> onToggleDetails()
                        ReviewScreenEvent.CloseDetails -> onCloseDetails()
                        ReviewScreenEvent.Retry -> onRetry()
                        ReviewScreenEvent.WrapUp -> onWrapUp()
                        ReviewScreenEvent.Abandon -> onAbandon()
                        ReviewScreenEvent.Done -> onDone()
                        ReviewScreenEvent.Back -> onBack()
                        is ReviewScreenEvent.SearchQueryChange -> Unit
                    }
                }
            )
        }
    }

    @Test
    fun showsCharacterAndQuestionLabel_forMeaningQuestion() {
        setScreen(activeState(totalCount = 2, remainingCount = 2))

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.CHARACTERS).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReviewScreenTestTags.ANSWER_FIELD).assertIsDisplayed()
    }

    @Test
    fun submitButton_disabledWhenAnswerBlank() {
        setScreen(activeState(totalCount = 1, remainingCount = 1, answerInput = ""))

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun typingAnswer_invokesCallback() {
        var typed = ""
        setScreen(
            activeState(totalCount = 1, remainingCount = 1),
            onAnswerInputChange = { typed = it }
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.ANSWER_FIELD).performTextInput("Water")
        // TextFieldState pushes edits up via a LaunchedEffect/snapshotFlow, one dispatch removed
        // from performTextInput itself — wait for that to land before reading the callback value.
        composeTestRule.waitForIdle()
        assert(typed == "Water")
    }

    @Test
    fun submittingAnswer_invokesOnSubmit() {
        var submitted = false
        setScreen(
            activeState(totalCount = 1, remainingCount = 1, answerInput = "Water"),
            onSubmit = { submitted = true }
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.SUBMIT_BUTTON).performClick()
        assert(submitted)
    }

    @Test
    fun feedback_showsCorrectAnswerText_andContinueAdvances() {
        var continued = false
        setScreen(
            activeState(
                totalCount = 1, remainingCount = 1,
                feedback = AnswerFeedback(isCorrect = false, correctAnswer = "Water")
            ),
            onContinue = { continued = true }
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.FEEDBACK_TEXT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReviewScreenTestTags.CONTINUE_BUTTON).performClick()
        assert(continued)
    }

    @Test
    fun subjectTypeLabel_shownWhenSettingEnabled() {
        setScreen(
            activeState(
                totalCount = 1, remainingCount = 1,
                settings = ReviewUiState.DisplaySettings(showSubjectTypeLabel = true)
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.SUBJECT_TYPE_LABEL).assertIsDisplayed()
        composeTestRule.onNodeWithText("Kanji").assertIsDisplayed()
    }

    @Test
    fun subjectTypeLabel_absentWhenSettingDisabled() {
        setScreen(
            activeState(
                totalCount = 1, remainingCount = 1,
                settings = ReviewUiState.DisplaySettings(showSubjectTypeLabel = false)
            )
        )

        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.SUBJECT_TYPE_LABEL).assertCountEquals(0)
    }

    @Test
    fun totalTimer_shownWhenSettingEnabledAndSessionInProgress() {
        setScreen(
            activeState(
                totalCount = 1, remainingCount = 1,
                settings = ReviewUiState.DisplaySettings(showTotalTimer = true),
                timing = QuizTimingUiState(sessionActiveSegmentStartMs = System.currentTimeMillis())
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.TOTAL_TIMER_TEXT).assertIsDisplayed()
    }

    @Test
    fun totalTimer_absentWhenSettingDisabled() {
        setScreen(
            activeState(
                totalCount = 1, remainingCount = 1,
                settings = ReviewUiState.DisplaySettings(showTotalTimer = false),
                timing = QuizTimingUiState(sessionActiveSegmentStartMs = System.currentTimeMillis())
            )
        )

        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.TOTAL_TIMER_TEXT).assertCountEquals(0)
    }

    @Test
    fun totalTimer_freezesWhilePaused_notTickingWithoutAnActiveSegment() {
        // sessionActiveSegmentStartMs is null (as if the app were backgrounded, or navigated away
        // and back) — the timer must show the frozen base, not restart from "0:00".
        setScreen(
            activeState(
                totalCount = 1, remainingCount = 1,
                settings = ReviewUiState.DisplaySettings(showTotalTimer = true),
                timing = QuizTimingUiState(sessionActiveElapsedMs = 65_000L, sessionActiveSegmentStartMs = null)
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.TOTAL_TIMER_TEXT).assertTextEquals("1:05")
    }

    @Test
    fun questionTimer_shownWhenSettingEnabledAndSessionInProgress() {
        setScreen(
            activeState(
                totalCount = 1, remainingCount = 1,
                settings = ReviewUiState.DisplaySettings(showQuestionTimer = true),
                timing = QuizTimingUiState(questionActiveSegmentStartMs = System.currentTimeMillis())
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.QUESTION_TIMER_TEXT).assertIsDisplayed()
    }

    @Test
    fun questionTimer_absentWhenSettingDisabled() {
        setScreen(
            activeState(
                totalCount = 1, remainingCount = 1,
                settings = ReviewUiState.DisplaySettings(showQuestionTimer = false),
                timing = QuizTimingUiState(questionActiveSegmentStartMs = System.currentTimeMillis())
            )
        )

        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.QUESTION_TIMER_TEXT).assertCountEquals(0)
    }

    @Test
    fun questionTimer_freezesAtAnsweredElapsedTime_onceFeedbackIsShown() {
        // questionActiveSegmentStartMs is a full minute in the past — if the timer were still
        // live-ticking from it, it would show "1:00". The frozen questionElapsedMs must win instead.
        setScreen(
            activeState(
                totalCount = 1, remainingCount = 1,
                settings = ReviewUiState.DisplaySettings(showQuestionTimer = true),
                timing = QuizTimingUiState(
                    questionActiveSegmentStartMs = System.currentTimeMillis() - 60_000,
                    questionElapsedMs = 5_000L
                ),
                feedback = AnswerFeedback(isCorrect = true, correctAnswer = "Water")
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.QUESTION_TIMER_TEXT).assertTextEquals(formatElapsedClock(5_000L))
    }

    @Test
    fun questionTimer_freezesWhilePaused_notTickingWithoutAnActiveSegment() {
        // questionActiveSegmentStartMs is null (as if the app were backgrounded mid-question) — the
        // timer must show the frozen base, not restart from "0:00" or keep ticking through the gap.
        setScreen(
            activeState(
                totalCount = 1, remainingCount = 1,
                settings = ReviewUiState.DisplaySettings(showQuestionTimer = true),
                timing = QuizTimingUiState(questionActiveElapsedMs = 5_000L, questionActiveSegmentStartMs = null)
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.QUESTION_TIMER_TEXT).assertTextEquals("0:05")
    }

    @Test
    fun continueButton_disabledBrieflyAfterIncorrectAnswer_thenEnables() {
        composeTestRule.mainClock.autoAdvance = false
        setScreen(
            activeState(
                totalCount = 1, remainingCount = 1,
                feedback = AnswerFeedback(isCorrect = false, correctAnswer = "Water")
            )
        )

        composeTestRule.mainClock.advanceTimeBy(50)
        composeTestRule.onNodeWithTag(ReviewScreenTestTags.CONTINUE_BUTTON).assertIsNotEnabled()

        composeTestRule.mainClock.advanceTimeBy(1300)
        composeTestRule.onNodeWithTag(ReviewScreenTestTags.CONTINUE_BUTTON).assertIsEnabled()
    }

    @Test
    fun continueButton_enabledImmediately_afterCorrectAnswer() {
        setScreen(
            activeState(
                totalCount = 1, remainingCount = 1,
                feedback = AnswerFeedback(isCorrect = true, correctAnswer = "Water")
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.CONTINUE_BUTTON).assertIsEnabled()
    }

    @Test
    fun undoIcon_enabledOnIncorrectFeedback_andInvokesCallback() {
        var undone = false
        setScreen(
            activeState(
                totalCount = 1, remainingCount = 1,
                feedback = AnswerFeedback(isCorrect = false, correctAnswer = "Water")
            ),
            onUndo = { undone = true }
        )

        // Lives on the answer field itself now, not the overflow menu.
        composeTestRule.onNodeWithTag(ReviewScreenTestTags.UNDO_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReviewScreenTestTags.UNDO_BUTTON).performClick()
        assert(undone)
    }

    @Test
    fun undoIcon_enabledOnCorrectFeedback_andInvokesCallback() {
        // Review defers submitting a correct answer to WaniKani until Continue is pressed, so it
        // can still be undone up to that point (unlike Lesson, which has no such window).
        var undone = false
        setScreen(
            activeState(
                totalCount = 1, remainingCount = 1,
                feedback = AnswerFeedback(isCorrect = true, correctAnswer = "Water")
            ),
            onUndo = { undone = true }
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.UNDO_BUTTON).assertIsEnabled()
        composeTestRule.onNodeWithTag(ReviewScreenTestTags.UNDO_BUTTON).performClick()
        assert(undone)
    }

    @Test
    fun undoIcon_absentBeforeAnswering() {
        setScreen(activeState(totalCount = 1, remainingCount = 1, feedback = null))

        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.UNDO_BUTTON).assertCountEquals(0)
    }

    @Test
    fun typeMismatchWarning_showsExpectingMeaning_forMeaningQuestion() {
        setScreen(activeState(totalCount = 1, remainingCount = 1, answerTypeMismatchCount = 1))

        // OutlinedTextField sets MergeDescendants on its root node, so the supportingText's own tag
        // collapses into it in the default merged tree — needs the unmerged tree to be individually
        // queryable.
        composeTestRule.onNodeWithTag(ReviewScreenTestTags.TYPE_MISMATCH_TEXT, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Expecting the meaning", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun typeMismatchWarning_showsExpectingReading_forReadingQuestion() {
        setScreen(
            activeState(
                totalCount = 1, remainingCount = 1,
                questionType = QuestionType.READING, answerTypeMismatchCount = 1
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.TYPE_MISMATCH_TEXT, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Expecting the reading", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun typeMismatchWarning_absentBeforeAnyMismatch() {
        setScreen(activeState(totalCount = 1, remainingCount = 1))

        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.TYPE_MISMATCH_TEXT, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun typeMismatchWarning_clearsOnceUserEditsTheAnswer() {
        setScreen(activeState(totalCount = 1, remainingCount = 1, answerTypeMismatchCount = 1))

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.TYPE_MISMATCH_TEXT, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReviewScreenTestTags.ANSWER_FIELD).performTextInput("W")
        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.TYPE_MISMATCH_TEXT, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun detailsToggle_absentBeforeTheQuestionIsAnswered() {
        // Nothing to toggle yet — the handle isn't just disabled, it isn't composed at all.
        setScreen(
            activeState(
                totalCount = 1, remainingCount = 1,
                questionType = QuestionType.READING, feedback = null
            )
        )

        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.DETAILS_TOGGLE).assertCountEquals(0)
    }

    @Test
    fun detailsToggle_enabledAndInvokesCallback_onceAnswered() {
        var toggled = false
        setScreen(
            activeState(
                totalCount = 1, remainingCount = 1,
                questionType = QuestionType.READING,
                feedback = AnswerFeedback(isCorrect = true, correctAnswer = "みず")
            ),
            onToggleDetails = { toggled = true }
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.DETAILS_TOGGLE).performClick()
        assert(toggled)
    }

    @Test
    fun dontKnowButton_isDisplayedBeforeAnswering_andInvokesCallback() {
        var dontKnow = false
        setScreen(
            activeState(totalCount = 1, remainingCount = 1),
            onDontKnow = { dontKnow = true }
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.DONT_KNOW_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReviewScreenTestTags.DONT_KNOW_BUTTON).performClick()
        assert(dontKnow)
    }

    @Test
    fun dontKnowButton_hiddenAfterFeedbackIsShown() {
        setScreen(
            activeState(
                totalCount = 1, remainingCount = 1,
                feedback = AnswerFeedback(isCorrect = false, correctAnswer = "Water")
            )
        )

        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.DONT_KNOW_BUTTON).assertCountEquals(0)
    }

    // Coverage for what the expanded-details panel actually shows moved to
    // SubjectDetailContentTest — once expanded, ReviewScreen delegates to the shared
    // SubjectDetailSheet, which requires a Hilt-injected ViewModel to render (this test class
    // isn't Hilt-aware, so it can no longer render the sheet's content directly).

    @Test
    fun progressCount_reflectsAnsweredVsTotal() {
        setScreen(activeState(totalCount = 5, remainingCount = 3))

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.PROGRESS_COUNT).assertIsDisplayed()
        composeTestRule.onNodeWithText("2 / 5").assertIsDisplayed()
    }

    @Test
    fun searchButton_opensInlineSearchOverlay() {
        setScreen(activeState(totalCount = 1, remainingCount = 1))

        composeTestRule.onAllNodesWithTag(SearchOverlayTestTags.QUERY_FIELD).assertCountEquals(0)

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.SEARCH_BUTTON).performClick()
        composeTestRule.onNodeWithTag(SearchOverlayTestTags.QUERY_FIELD).assertIsDisplayed()
    }

    @Test
    fun backButton_invokesCallback() {
        var wentBack = false
        setScreen(
            activeState(totalCount = 1, remainingCount = 1),
            onBack = { wentBack = true }
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.BACK_BUTTON).performClick()
        assert(wentBack)
    }

    @Test
    fun noReviewsAvailable_showsMessageAndDoneButton() {
        var done = false
        setScreen(
            ReviewUiState(phase = ReviewUiState.Phase.NoReviewsAvailable),
            onDone = { done = true }
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.NO_REVIEWS_TEXT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReviewScreenTestTags.NO_REVIEWS_DONE_BUTTON).performClick()
        assert(done)
    }

    @Test
    fun overflowMenu_isAbsent_whenNoReviewsAvailable() {
        setScreen(ReviewUiState(phase = ReviewUiState.Phase.NoReviewsAvailable))

        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.OVERFLOW_MENU).assertCountEquals(0)
    }

    @Test
    fun sessionComplete_showsDoneButtonAndInvokesCallback() {
        var done = false
        setScreen(completeState(), onDone = { done = true })

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.SESSION_COMPLETE).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReviewScreenTestTags.DONE_BUTTON).performClick()
        assert(done)
    }

    @Test
    fun sessionComplete_showsOverviewCardWithCounts() {
        setScreen(completeState(sessionItemsReviewed = 5, sessionItemsCorrectFirstTry = 3))

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.SESSION_OVERVIEW_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithText("Items reviewed: 5").assertIsDisplayed()
        composeTestRule.onNodeWithText("Correct on first try: 3 of 5 (60%)").assertIsDisplayed()
    }

    @Test
    fun sessionComplete_hidesCardsWhenNothingWasReviewed() {
        setScreen(completeState(sessionItemsReviewed = 0))

        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.SESSION_OVERVIEW_CARD).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.SESSION_TIMING_CARD).assertCountEquals(0)
    }

    @Test
    fun sessionComplete_showsTimingCard() {
        setScreen(
            completeState(
                sessionItemsReviewed = 3, sessionItemsCorrectFirstTry = 3,
                sessionTotalElapsedMs = 125_000L, sessionAverageTimePerItemMs = 4_500L
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.SESSION_TIMING_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithText("Total time: 2:05").assertIsDisplayed()
        composeTestRule.onNodeWithText("Avg. time per item reviewed: 4s").assertIsDisplayed()
    }

    @Test
    fun sessionComplete_showsSlowestAnswersCard_whenPresent() {
        setScreen(
            completeState(
                sessionItemsReviewed = 1, sessionItemsCorrectFirstTry = 1,
                sessionSlowestAnswers = listOf(
                    SlowAnswer(sampleItem, QuestionType.MEANING, 12_000L, isCorrect = true)
                )
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.SESSION_SLOWEST_CARD).assertIsDisplayed()
    }

    @Test
    fun sessionComplete_hidesSlowestAnswersCard_whenEmpty() {
        setScreen(
            completeState(
                sessionItemsReviewed = 1, sessionItemsCorrectFirstTry = 1,
                sessionSlowestAnswers = emptyList()
            )
        )

        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.SESSION_SLOWEST_CARD).assertCountEquals(0)
    }

    @Test
    fun sessionComplete_showsMissedItemsCard_whenPresent() {
        setScreen(
            completeState(
                sessionItemsReviewed = 2, sessionItemsCorrectFirstTry = 1,
                sessionMissedItems = listOf(sampleItem)
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.SESSION_MISSED_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithText("水").assertIsDisplayed()
    }

    @Test
    fun sessionComplete_hidesMissedItemsCard_whenEmpty() {
        setScreen(
            completeState(
                sessionItemsReviewed = 1, sessionItemsCorrectFirstTry = 1,
                sessionMissedItems = emptyList()
            )
        )

        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.SESSION_MISSED_CARD).assertCountEquals(0)
    }

    @Test
    fun sessionComplete_neverShowsSwipeUpDetailsHandle() {
        // Regression test, updated for the sealed Phase design: Phase.Complete has no `feedback`
        // field at all (unlike the old flat ReviewUiState, where a stale feedback value from the
        // last-answered question could leak into the completed state and keep this dead handle
        // visible with nothing for it to reveal). That leak is now impossible by construction —
        // this just re-asserts the visible behavior still holds.
        setScreen(completeState())

        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.DETAILS_TOGGLE).assertCountEquals(0)
    }

    @Test
    fun errorState_showsErrorText() {
        setScreen(ReviewUiState(phase = ReviewUiState.Phase.Error(message = "Network error")))

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.ERROR_TEXT).assertIsDisplayed()
    }
}
