package com.crazyfluff.shellfstudy.feature.lesson

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.crazyfluff.shellfstudy.shared.data.model.LessonItem
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.formatElapsedClock
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonPhase
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonScreen
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonScreenEvent
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonScreenTestTags
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonSlowAnswer
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonUiState
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.crazyfluff.shellfstudy.shared.quiz.AnswerFeedback
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType
import org.junit.Rule
import org.junit.Test

class LessonScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleItem = LessonItem(
        assignmentId = 1,
        subjectId = 440,
        subjectType = SubjectType.KANJI,
        characters = "水",
        level = 3,
        meanings = listOf("Water"),
        readings = listOf("みず"),
        meaningMnemonic = "Think of water.",
        readingMnemonic = null
    )

    private fun setScreen(
        uiState: LessonUiState,
        onEvent: (LessonScreenEvent) -> Unit = {}
    ) {
        composeTestRule.setContent {
            LessonScreen(uiState = uiState, onEvent = onEvent)
        }
    }

    // ── Loading / error ──────────────────────────────────────────────────────

    @Test
    fun loadingState_showsLoadingIndicator() {
        setScreen(LessonUiState(isLoading = true))
        composeTestRule.onNodeWithTag(LessonScreenTestTags.LOADING_INDICATOR).assertIsDisplayed()
    }

    @Test
    fun errorState_showsErrorTextAndRetryButton() {
        setScreen(LessonUiState(isLoading = false, errorMessage = "Network error"))
        composeTestRule.onNodeWithTag(LessonScreenTestTags.ERROR_TEXT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.RETRY_BUTTON).assertIsDisplayed()
    }

    @Test
    fun retryButton_invokesCallback() {
        var retried = false
        setScreen(
            LessonUiState(isLoading = false, errorMessage = "Network error"),
            onEvent = { if (it == LessonScreenEvent.Retry) retried = true }
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.RETRY_BUTTON).performClick()
        assert(retried)
    }

    @Test
    fun noLessonsState_showsNoLessonsTextAndDoneButton() {
        setScreen(LessonUiState(isLoading = false, hasNoLessonsAvailable = true))
        composeTestRule.onNodeWithTag(LessonScreenTestTags.NO_LESSONS_TEXT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.NO_LESSONS_DONE_BUTTON).assertIsDisplayed()
    }

    // ── SELECT phase ─────────────────────────────────────────────────────────

    @Test
    fun selectPhase_showsStartSelectedButton() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.SELECT,
                availableLessons = listOf(sampleItem),
                selectedAssignmentIds = setOf(1L)
            )
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.START_SELECTED_BUTTON).assertIsDisplayed()
    }

    @Test
    fun selectPhase_startSelectedButton_invokesCallback() {
        var started = false
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.SELECT,
                availableLessons = listOf(sampleItem),
                selectedAssignmentIds = setOf(1L)
            ),
            onEvent = { if (it == LessonScreenEvent.StartSelectedLessons) started = true }
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.START_SELECTED_BUTTON).performClick()
        assert(started)
    }

    @Test
    fun selectPhase_selectAllChip_invokesCallback() {
        var selectedAll = false
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.SELECT,
                availableLessons = listOf(sampleItem),
                selectedAssignmentIds = emptySet()
            ),
            onEvent = { if (it == LessonScreenEvent.SelectAll) selectedAll = true }
        )
        // Chips live inside the collapsible "Customize selection" panel — expand it first.
        composeTestRule.onNodeWithTag(LessonScreenTestTags.CUSTOMIZE_TOGGLE).performClick()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.SELECT_ALL_CHIP).performClick()
        assert(selectedAll)
    }

    @Test
    fun selectPhase_selectNoneChip_invokesCallback() {
        var selectedNone = false
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.SELECT,
                availableLessons = listOf(sampleItem),
                selectedAssignmentIds = setOf(1L)
            ),
            onEvent = { if (it == LessonScreenEvent.SelectNone) selectedNone = true }
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.CUSTOMIZE_TOGGLE).performClick()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.SELECT_NONE_CHIP).performClick()
        assert(selectedNone)
    }

    // ── STUDY phase ──────────────────────────────────────────────────────────

    @Test
    fun studyPhase_showsCharactersAndProgressCount() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.STUDY,
                studyItems = listOf(sampleItem), studyIndex = 0
            )
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.STUDY_CHARACTERS).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.STUDY_PROGRESS_COUNT).assertIsDisplayed()
    }

    @Test
    fun studyPhase_nextButton_invokesNextStudyCard() {
        var advancedNext = false
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.STUDY,
                studyItems = listOf(sampleItem, sampleItem.copy(assignmentId = 2)), studyIndex = 0
            ),
            onEvent = { if (it == LessonScreenEvent.NextStudyCard) advancedNext = true }
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.STUDY_NEXT_BUTTON).performClick()
        assert(advancedNext)
    }

    @Test
    fun studyPhase_previousButton_invokesCallback() {
        var wentBack = false
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.STUDY,
                studyItems = listOf(sampleItem, sampleItem.copy(assignmentId = 2)), studyIndex = 1
            ),
            onEvent = { if (it == LessonScreenEvent.PreviousStudyCard) wentBack = true }
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.STUDY_PREVIOUS_BUTTON).performClick()
        assert(wentBack)
    }

    @Test
    fun studyPhase_lastCard_showsStartQuizButtonInsteadOfNext() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.STUDY,
                studyItems = listOf(sampleItem), studyIndex = 0
            )
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.START_QUIZ_BUTTON).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.STUDY_NEXT_BUTTON).assertCountEquals(0)
    }

    @Test
    fun studyPhase_startQuizButton_invokesNextStudyCard() {
        var startedQuiz = false
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.STUDY,
                studyItems = listOf(sampleItem), studyIndex = 0
            ),
            onEvent = { if (it == LessonScreenEvent.NextStudyCard) startedQuiz = true }
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.START_QUIZ_BUTTON).performClick()
        assert(startedQuiz)
    }

    // ── QUIZ phase ───────────────────────────────────────────────────────────

    @Test
    fun quizPhase_showsCharactersAndAnswerField() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ,
                currentQuizItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1
            )
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.QUIZ_CHARACTERS).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.ANSWER_FIELD).assertIsDisplayed()
    }

    @Test
    fun quizPhase_submitButton_disabledWhenAnswerBlank() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ,
                currentQuizItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1, answerInput = ""
            )
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun quizPhase_typingAnswer_invokesCallback() {
        var typed = ""
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ,
                currentQuizItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1
            ),
            onEvent = { if (it is LessonScreenEvent.AnswerInputChange) typed = it.value }
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.ANSWER_FIELD).performTextInput("Water")
        assert(typed == "Water")
    }

    @Test
    fun quizPhase_submitButton_enabledWhenAnswerNonBlank() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ,
                currentQuizItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1, answerInput = "Water"
            )
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.SUBMIT_BUTTON).assertIsEnabled()
    }

    @Test
    fun quizPhase_submitButton_invokesSubmitCallback() {
        var submitted = false
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ,
                currentQuizItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1, answerInput = "Water"
            ),
            onEvent = { if (it == LessonScreenEvent.Submit) submitted = true }
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.SUBMIT_BUTTON).performClick()
        assert(submitted)
    }

    @Test
    fun quizPhase_dontKnowButton_displayedBeforeAnswering_andInvokesCallback() {
        var dontKnow = false
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ,
                currentQuizItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1
            ),
            onEvent = { if (it == LessonScreenEvent.DontKnow) dontKnow = true }
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.DONT_KNOW_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.DONT_KNOW_BUTTON).performClick()
        assert(dontKnow)
    }

    @Test
    fun quizPhase_dontKnowButton_hiddenAfterFeedbackIsShown() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ,
                currentQuizItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1,
                feedback = AnswerFeedback(isCorrect = false, correctAnswer = "Water")
            )
        )
        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.DONT_KNOW_BUTTON).assertCountEquals(0)
    }

    @Test
    fun quizPhase_feedback_showsCorrectAnswerAndContinueButton() {
        var continued = false
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ,
                currentQuizItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1,
                feedback = AnswerFeedback(isCorrect = false, correctAnswer = "Water")
            ),
            onEvent = { if (it == LessonScreenEvent.Continue) continued = true }
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.FEEDBACK_TEXT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.CONTINUE_BUTTON).performClick()
        assert(continued)
    }

    @Test
    fun quizPhase_typeMismatch_showsWarning_forMeaningQuestion() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ,
                currentQuizItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1, answerTypeMismatchCount = 1
            )
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.TYPE_MISMATCH_TEXT, useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Expecting the meaning", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun quizPhase_typeMismatch_showsWarning_forReadingQuestion() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ,
                currentQuizItem = sampleItem, currentQuestionType = QuestionType.READING,
                totalQuizCount = 1, remainingQuizCount = 1, answerTypeMismatchCount = 1
            )
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.TYPE_MISMATCH_TEXT, useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Expecting the reading", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun quizPhase_subjectTypeLabel_shownWhenSettingEnabled() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ,
                currentQuizItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1, showSubjectTypeLabel = true
            )
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.QUIZ_SUBJECT_TYPE_LABEL).assertIsDisplayed()
        composeTestRule.onNodeWithText("Kanji").assertIsDisplayed()
    }

    @Test
    fun quizPhase_subjectTypeLabel_absentWhenSettingDisabled() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ,
                currentQuizItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1, showSubjectTypeLabel = false
            )
        )
        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.QUIZ_SUBJECT_TYPE_LABEL)
            .assertCountEquals(0)
    }

    @Test
    fun quizPhase_totalTimer_shownWhenSettingEnabledAndSegmentRunning() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ,
                currentQuizItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1,
                showTotalTimer = true, sessionActiveSegmentStartMs = System.currentTimeMillis()
            )
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.TOTAL_TIMER_TEXT).assertIsDisplayed()
    }

    @Test
    fun quizPhase_totalTimer_absentWhenSettingDisabled() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ,
                currentQuizItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1,
                showTotalTimer = false, sessionActiveSegmentStartMs = System.currentTimeMillis()
            )
        )
        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.TOTAL_TIMER_TEXT).assertCountEquals(0)
    }

    @Test
    fun quizPhase_questionTimer_freezesAtAnsweredElapsedTime_onceFeedbackIsShown() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ,
                currentQuizItem = sampleItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1,
                showQuestionTimer = true,
                questionStartTimeMs = System.currentTimeMillis() - 60_000,
                questionElapsedMs = 5_000L,
                feedback = AnswerFeedback(isCorrect = true, correctAnswer = "Water")
            )
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.QUESTION_TIMER_TEXT)
            .assertTextEquals(formatElapsedClock(5_000L))
    }

    // ── Session complete ──────────────────────────────────────────────────────

    @Test
    fun sessionComplete_showsDoneButton_andInvokesCallback() {
        var done = false
        setScreen(
            LessonUiState(isLoading = false, phase = LessonPhase.QUIZ, isSessionComplete = true),
            onEvent = { if (it == LessonScreenEvent.Done) done = true }
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.SESSION_COMPLETE).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.DONE_BUTTON).performClick()
        assert(done)
    }

    @Test
    fun sessionComplete_showsOverviewCardWithItemsLearned() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, isSessionComplete = true,
                sessionItemsLearned = 5, sessionItemsCorrectFirstTry = 4
            )
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.SESSION_OVERVIEW_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithText("Items learned: 5").assertIsDisplayed()
    }

    @Test
    fun sessionComplete_showsTimingCard() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, isSessionComplete = true,
                sessionItemsLearned = 2, sessionItemsCorrectFirstTry = 2,
                sessionTotalElapsedMs = 125_000L, sessionAverageTimePerItemMs = 4_500L
            )
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.SESSION_TIMING_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithText("Total time: 2:05").assertIsDisplayed()
    }

    @Test
    fun sessionComplete_showsSlowestAnswersCard_whenPresent() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, isSessionComplete = true,
                sessionItemsLearned = 1, sessionItemsCorrectFirstTry = 1,
                sessionSlowestAnswers = listOf(
                    LessonSlowAnswer(sampleItem, QuestionType.MEANING, 12_000L, isCorrect = true)
                )
            )
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.SESSION_SLOWEST_CARD).assertIsDisplayed()
    }

    @Test
    fun sessionComplete_showsMissedItemsCard_whenPresent() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, isSessionComplete = true,
                sessionItemsLearned = 2, sessionItemsCorrectFirstTry = 1,
                sessionMissedItems = listOf(sampleItem)
            )
        )
        composeTestRule.onNodeWithTag(LessonScreenTestTags.SESSION_MISSED_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithText("水").assertIsDisplayed()
    }

    @Test
    fun sessionComplete_hidesMissedItemsCard_whenNoneMissed() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, isSessionComplete = true,
                sessionItemsLearned = 1, sessionItemsCorrectFirstTry = 1,
                sessionMissedItems = emptyList()
            )
        )
        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.SESSION_MISSED_CARD).assertCountEquals(0)
    }
}
