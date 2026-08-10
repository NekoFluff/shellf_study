package com.crazyfluff.shellfstudy.feature.lesson

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.crazyfluff.shellfstudy.core.data.model.LessonItem
import com.crazyfluff.shellfstudy.core.network.SubjectType
import org.junit.Rule
import org.junit.Test

class LessonScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val radicalItem = LessonItem(
        assignmentId = 1,
        subjectId = 1,
        subjectType = SubjectType.RADICAL,
        characters = "口",
        level = 1,
        meanings = listOf("Mouth"),
        readings = emptyList(),
        meaningMnemonic = "Looks like an open mouth.",
        readingMnemonic = null
    )

    private fun setScreen(
        uiState: LessonUiState,
        onNextStudyCard: () -> Unit = {},
        onPreviousStudyCard: () -> Unit = {},
        onAnswerInputChange: (String) -> Unit = {},
        onSubmit: () -> Unit = {},
        onDontKnow: () -> Unit = {},
        onContinue: () -> Unit = {},
        onRetry: () -> Unit = {},
        onDone: () -> Unit = {},
        onBack: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            LessonScreen(
                uiState = uiState,
                onNextStudyCard = onNextStudyCard,
                onPreviousStudyCard = onPreviousStudyCard,
                onAnswerInputChange = onAnswerInputChange,
                onSubmit = onSubmit,
                onDontKnow = onDontKnow,
                onContinue = onContinue,
                onRetry = onRetry,
                onDone = onDone,
                onBack = onBack
            )
        }
    }

    @Test
    fun studyPhase_showsCharacterAndMnemonic() {
        setScreen(
            LessonUiState(isLoading = false, phase = LessonPhase.STUDY, studyItems = listOf(radicalItem), studyIndex = 0)
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.STUDY_CHARACTERS).assertIsDisplayed()
    }

    @Test
    fun studyPhase_previousDisabledOnFirstCard_nextLabelledStartQuizOnLastCard() {
        setScreen(
            LessonUiState(isLoading = false, phase = LessonPhase.STUDY, studyItems = listOf(radicalItem), studyIndex = 0)
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.STUDY_PREVIOUS_BUTTON).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.START_QUIZ_BUTTON).assertIsDisplayed()
    }

    @Test
    fun studyPhase_nextButton_invokesCallback() {
        var advanced = false
        setScreen(
            LessonUiState(isLoading = false, phase = LessonPhase.STUDY, studyItems = listOf(radicalItem), studyIndex = 0),
            onNextStudyCard = { advanced = true }
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.START_QUIZ_BUTTON).performClick()
        assert(advanced)
    }

    @Test
    fun quizPhase_submittingAnswer_invokesOnSubmit() {
        var submitted = false
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, totalQuizCount = 1, remainingQuizCount = 1,
                currentQuizItem = radicalItem, currentQuestionType = LessonQuestionType.MEANING,
                answerInput = "Mouth"
            ),
            onSubmit = { submitted = true }
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.SUBMIT_BUTTON).performClick()
        assert(submitted)
    }

    @Test
    fun quizPhase_typingAnswer_invokesCallback() {
        var typed = ""
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, totalQuizCount = 1, remainingQuizCount = 1,
                currentQuizItem = radicalItem, currentQuestionType = LessonQuestionType.MEANING
            ),
            onAnswerInputChange = { typed = it }
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.ANSWER_FIELD).performTextInput("Mouth")
        assert(typed == "Mouth")
    }

    @Test
    fun quizPhase_feedback_showsContinueButton() {
        var continued = false
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, totalQuizCount = 1, remainingQuizCount = 1,
                currentQuizItem = radicalItem, currentQuestionType = LessonQuestionType.MEANING,
                feedback = LessonAnswerFeedback(isCorrect = true, correctAnswer = "Mouth")
            ),
            onContinue = { continued = true }
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.FEEDBACK_TEXT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.CONTINUE_BUTTON).performClick()
        assert(continued)
    }

    @Test
    fun noLessonsAvailable_showsMessageAndDoneButton() {
        var done = false
        setScreen(
            LessonUiState(isLoading = false, hasNoLessonsAvailable = true),
            onDone = { done = true }
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.NO_LESSONS_TEXT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.NO_LESSONS_DONE_BUTTON).performClick()
        assert(done)
    }

    @Test
    fun sessionComplete_showsDoneButtonAndInvokesCallback() {
        var done = false
        setScreen(
            LessonUiState(isLoading = false, isSessionComplete = true),
            onDone = { done = true }
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.SESSION_COMPLETE).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.DONE_BUTTON).performClick()
        assert(done)
    }

    @Test
    fun errorState_showsErrorTextAndRetry() {
        var retried = false
        setScreen(
            LessonUiState(isLoading = false, errorMessage = "Network error"),
            onRetry = { retried = true }
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.ERROR_TEXT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.RETRY_BUTTON).performClick()
        assert(retried)
    }

    @Test
    fun backButton_invokesCallback() {
        var wentBack = false
        setScreen(
            LessonUiState(isLoading = false, phase = LessonPhase.STUDY, studyItems = listOf(radicalItem)),
            onBack = { wentBack = true }
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.BACK_BUTTON).performClick()
        assert(wentBack)
    }
}
