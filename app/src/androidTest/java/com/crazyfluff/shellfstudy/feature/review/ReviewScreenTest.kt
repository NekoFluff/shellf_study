package com.crazyfluff.shellfstudy.feature.review

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.crazyfluff.shellfstudy.core.data.model.ReviewItem
import com.crazyfluff.shellfstudy.core.network.SubjectType
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
                onAnswerInputChange = onAnswerInputChange,
                onSubmit = onSubmit,
                onDontKnow = onDontKnow,
                onContinue = onContinue,
                onUndo = onUndo,
                onToggleDetails = onToggleDetails,
                onRetry = onRetry,
                onWrapUp = onWrapUp,
                onAbandon = onAbandon,
                onDone = onDone,
                onBack = onBack
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
    fun errorState_showsErrorText() {
        setScreen(ReviewUiState(isLoading = false, errorMessage = "Network error"))

        composeTestRule.onNodeWithTag(ReviewScreenTestTags.ERROR_TEXT).assertIsDisplayed()
    }
}
