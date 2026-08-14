package com.crazyfluff.shellfstudy.feature.review

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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.crazyfluff.shellfstudy.core.data.model.ReviewItem
import com.crazyfluff.shellfstudy.core.designsystem.quiz.formatElapsedClock
import com.crazyfluff.shellfstudy.core.network.SubjectType
import com.crazyfluff.shellfstudy.core.quiz.AnswerFeedback
import com.crazyfluff.shellfstudy.core.quiz.QuestionType
import com.crazyfluff.shellfstudy.feature.search.SearchOverlayTestTags
import org.junit.Rule
import org.junit.Test

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

    private fun setScreen(
        uiState: ReviewUiState,
        onAnswerInputChange: (String) -> Unit = {},
        onSubmit: () -> Unit = {},
        onDontKnow: () -> Unit = {},
        onContinue: () -> Unit = {},
        onUndo: () -> Unit = {},
        onToggleDetails: () -> Unit = {},
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
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 2, remainingCount = 2,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.CHARACTERS).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReviewScreenTestTags.ANSWER_FIELD).assertIsDisplayed()
    }

    @Test
    fun submitButton_disabledWhenAnswerBlank() {
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING, answerInput = ""
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun typingAnswer_invokesCallback() {
        var typed = ""
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING
            ),
            onAnswerInputChange = { typed = it }
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.ANSWER_FIELD).performTextInput("Water")
        assert(typed == "Water")
    }

    @Test
    fun submittingAnswer_invokesOnSubmit() {
        var submitted = false
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING, answerInput = "Water"
            ),
            onSubmit = { submitted = true }
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.SUBMIT_BUTTON).performClick()
        assert(submitted)
    }

    @Test
    fun feedback_showsCorrectAnswerText_andContinueAdvances() {
        var continued = false
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING,
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
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                showSubjectTypeLabel = true
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.SUBJECT_TYPE_LABEL).assertIsDisplayed()
        composeTestRule.onNodeWithText("Kanji").assertIsDisplayed()
    }

    @Test
    fun subjectTypeLabel_absentWhenSettingDisabled() {
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                showSubjectTypeLabel = false
            )
        )

        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.SUBJECT_TYPE_LABEL).assertCountEquals(0)
    }

    @Test
    fun totalTimer_shownWhenSettingEnabledAndSessionInProgress() {
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                showTotalTimer = true, sessionActiveSegmentStartMs = System.currentTimeMillis()
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.TOTAL_TIMER_TEXT).assertIsDisplayed()
    }

    @Test
    fun totalTimer_absentWhenSettingDisabled() {
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                showTotalTimer = false, sessionActiveSegmentStartMs = System.currentTimeMillis()
            )
        )

        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.TOTAL_TIMER_TEXT).assertCountEquals(0)
    }

    @Test
    fun totalTimer_freezesWhilePaused_notTickingWithoutAnActiveSegment() {
        // sessionActiveSegmentStartMs is null (as if the app were backgrounded, or navigated away
        // and back) — the timer must show the frozen base, not restart from "0:00".
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                showTotalTimer = true, sessionActiveElapsedMs = 65_000L, sessionActiveSegmentStartMs = null
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.TOTAL_TIMER_TEXT).assertTextEquals("1:05")
    }

    @Test
    fun questionTimer_shownWhenSettingEnabledAndSessionInProgress() {
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                showQuestionTimer = true, questionStartTimeMs = System.currentTimeMillis()
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.QUESTION_TIMER_TEXT).assertIsDisplayed()
    }

    @Test
    fun questionTimer_absentWhenSettingDisabled() {
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                showQuestionTimer = false, questionStartTimeMs = System.currentTimeMillis()
            )
        )

        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.QUESTION_TIMER_TEXT).assertCountEquals(0)
    }

    @Test
    fun questionTimer_freezesAtAnsweredElapsedTime_onceFeedbackIsShown() {
        // questionStartTimeMs is a full minute in the past — if the timer were still live-ticking
        // from it, it would show "1:00". The frozen questionElapsedMs must win instead.
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                showQuestionTimer = true,
                questionStartTimeMs = System.currentTimeMillis() - 60_000,
                questionElapsedMs = 5_000L,
                feedback = AnswerFeedback(isCorrect = true, correctAnswer = "Water")
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.QUESTION_TIMER_TEXT).assertTextEquals(formatElapsedClock(5_000L))
    }

    @Test
    fun continueButton_disabledBrieflyAfterIncorrectAnswer_thenEnables() {
        composeTestRule.mainClock.autoAdvance = false
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING,
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
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                feedback = AnswerFeedback(isCorrect = true, correctAnswer = "Water")
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.CONTINUE_BUTTON).assertIsEnabled()
    }

    @Test
    fun undoIcon_enabledOnIncorrectFeedback_andInvokesCallback() {
        var undone = false
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING,
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
    fun undoIcon_disabledWithoutIncorrectFeedback() {
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                feedback = AnswerFeedback(isCorrect = true, correctAnswer = "Water")
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.UNDO_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun undoIcon_absentBeforeAnswering() {
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING, feedback = null
            )
        )

        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.UNDO_BUTTON).assertCountEquals(0)
    }

    @Test
    fun typeMismatchWarning_showsExpectingMeaning_forMeaningQuestion() {
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                answerTypeMismatchCount = 1
            )
        )

        // OutlinedTextField sets MergeDescendants on its root node, so the supportingText's own tag
        // collapses into it in the default merged tree — needs the unmerged tree to be individually
        // queryable.
        composeTestRule.onNodeWithTag(ReviewScreenTestTags.TYPE_MISMATCH_TEXT, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Expecting the meaning", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun typeMismatchWarning_showsExpectingReading_forReadingQuestion() {
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.READING,
                answerTypeMismatchCount = 1
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.TYPE_MISMATCH_TEXT, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Expecting the reading", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun typeMismatchWarning_absentBeforeAnyMismatch() {
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING
            )
        )

        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.TYPE_MISMATCH_TEXT, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun typeMismatchWarning_clearsOnceUserEditsTheAnswer() {
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                answerTypeMismatchCount = 1
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.TYPE_MISMATCH_TEXT, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReviewScreenTestTags.ANSWER_FIELD).performTextInput("W")
        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.TYPE_MISMATCH_TEXT, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun detailsToggle_absentBeforeTheQuestionIsAnswered() {
        // Nothing to toggle yet — the handle isn't just disabled, it isn't composed at all.
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.READING, feedback = null
            )
        )

        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.DETAILS_TOGGLE).assertCountEquals(0)
    }

    @Test
    fun detailsToggle_enabledAndInvokesCallback_onceAnswered() {
        var toggled = false
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.READING,
                feedback = AnswerFeedback(isCorrect = true, correctAnswer = "みず")
            ),
            onToggleDetails = { toggled = true }
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.DETAILS_TOGGLE).performClick()
        assert(toggled)
    }

    @Test
    fun detailsHandle_swipingUp_invokesCallback_onceAnswered() {
        var toggled = false
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.READING,
                feedback = AnswerFeedback(isCorrect = true, correctAnswer = "みず")
            ),
            onToggleDetails = { toggled = true }
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.DETAILS_TOGGLE).performTouchInput { swipeUp() }
        assert(toggled)
    }


    @Test
    fun dontKnowButton_isDisplayedBeforeAnswering_andInvokesCallback() {
        var dontKnow = false
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING
            ),
            onDontKnow = { dontKnow = true }
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.DONT_KNOW_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReviewScreenTestTags.DONT_KNOW_BUTTON).performClick()
        assert(dontKnow)
    }

    @Test
    fun dontKnowButton_hiddenAfterFeedbackIsShown() {
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING,
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
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 5, remainingCount = 3,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.PROGRESS_COUNT).assertIsDisplayed()
        composeTestRule.onNodeWithText("2 / 5").assertIsDisplayed()
    }

    @Test
    fun searchButton_opensInlineSearchOverlay() {
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING
            )
        )

        composeTestRule.onAllNodesWithTag(SearchOverlayTestTags.QUERY_FIELD).assertCountEquals(0)

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.SEARCH_BUTTON).performClick()
        composeTestRule.onNodeWithTag(SearchOverlayTestTags.QUERY_FIELD).assertIsDisplayed()
    }

    @Test
    fun backButton_invokesCallback() {
        var wentBack = false
        setScreen(
            ReviewUiState(
                isLoading = false, totalCount = 1, remainingCount = 1,
                currentItem = sampleItem, currentQuestionType = QuestionType.MEANING
            ),
            onBack = { wentBack = true }
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.BACK_BUTTON).performClick()
        assert(wentBack)
    }

    @Test
    fun sessionComplete_showsDoneButtonAndInvokesCallback() {
        var done = false
        setScreen(
            ReviewUiState(isLoading = false, isSessionComplete = true),
            onDone = { done = true }
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.SESSION_COMPLETE).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReviewScreenTestTags.DONE_BUTTON).performClick()
        assert(done)
    }

    @Test
    fun sessionComplete_showsOverviewCardWithCounts() {
        setScreen(
            ReviewUiState(
                isLoading = false, isSessionComplete = true,
                sessionItemsReviewed = 5, sessionItemsCorrectFirstTry = 3
            )
        )

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.SESSION_OVERVIEW_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithText("Items reviewed: 5").assertIsDisplayed()
        composeTestRule.onNodeWithText("Correct on first try: 3 of 5 (60%)").assertIsDisplayed()
    }

    @Test
    fun sessionComplete_hidesCardsWhenNothingWasReviewed() {
        setScreen(ReviewUiState(isLoading = false, isSessionComplete = true, sessionItemsReviewed = 0))

        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.SESSION_OVERVIEW_CARD).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.SESSION_TIMING_CARD).assertCountEquals(0)
    }

    @Test
    fun sessionComplete_showsTimingCard() {
        setScreen(
            ReviewUiState(
                isLoading = false, isSessionComplete = true,
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
            ReviewUiState(
                isLoading = false, isSessionComplete = true,
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
            ReviewUiState(
                isLoading = false, isSessionComplete = true,
                sessionItemsReviewed = 1, sessionItemsCorrectFirstTry = 1,
                sessionSlowestAnswers = emptyList()
            )
        )

        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.SESSION_SLOWEST_CARD).assertCountEquals(0)
    }

    @Test
    fun sessionComplete_showsMissedItemsCard_whenPresent() {
        setScreen(
            ReviewUiState(
                isLoading = false, isSessionComplete = true,
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
            ReviewUiState(
                isLoading = false, isSessionComplete = true,
                sessionItemsReviewed = 1, sessionItemsCorrectFirstTry = 1,
                sessionMissedItems = emptyList()
            )
        )

        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.SESSION_MISSED_CARD).assertCountEquals(0)
    }

    @Test
    fun sessionComplete_neverShowsSwipeUpDetailsHandle_evenWithStaleFeedback() {
        // Regression test: feedback from the last-answered question used to leak into the
        // completed state and kept this dead handle visible with nothing for it to reveal.
        setScreen(
            ReviewUiState(
                isLoading = false, isSessionComplete = true,
                feedback = AnswerFeedback(isCorrect = true, correctAnswer = "Water")
            )
        )

        composeTestRule.onAllNodesWithTag(ReviewScreenTestTags.DETAILS_TOGGLE).assertCountEquals(0)
    }

    @Test
    fun errorState_showsErrorText() {
        setScreen(ReviewUiState(isLoading = false, errorMessage = "Network error"))

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.ERROR_TEXT).assertIsDisplayed()
    }
}
