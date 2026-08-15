package com.crazyfluff.shellfstudy.feature.lesson

import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonPhase
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonSlowAnswer
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonUiState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.model.LessonItem
import com.crazyfluff.shellfstudy.shared.data.model.StrokeOrderStroke
import com.crazyfluff.shellfstudy.core.designsystem.quiz.formatElapsedClock
import com.crazyfluff.shellfstudy.core.designsystem.strokeorder.StrokeOrderTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.strokeorder.StrokeOrderUiState
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.crazyfluff.shellfstudy.shared.quiz.AnswerFeedback
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Runs under Robolectric (JVM) — this screen is driven purely by state, no device features needed.
 * Pinned to SDK 35: Robolectric 4.15.1 doesn't yet have shadows for this project's targetSdk (37).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
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

    private val secondRadicalItem = LessonItem(
        assignmentId = 2,
        subjectId = 2,
        subjectType = SubjectType.RADICAL,
        characters = "一",
        level = 1,
        meanings = listOf("Ground"),
        readings = emptyList(),
        meaningMnemonic = "A single horizontal line.",
        readingMnemonic = null
    )

    private fun setScreen(
        uiState: LessonUiState,
        onToggleLessonSelection: (Long) -> Unit = {},
        onSelectFirst: (Int) -> Unit = {},
        onSelectAll: () -> Unit = {},
        onSelectNone: () -> Unit = {},
        onStartSelectedLessons: () -> Unit = {},
        onStudyCardSwiped: (Int) -> Unit = {},
        onNextStudyCard: () -> Unit = {},
        onPreviousStudyCard: () -> Unit = {},
        onAnswerInputChange: (String) -> Unit = {},
        onSubmit: () -> Unit = {},
        onDontKnow: () -> Unit = {},
        onContinue: () -> Unit = {},
        onRetry: () -> Unit = {},
        onAbandon: () -> Unit = {},
        onDone: () -> Unit = {},
        onBack: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            LessonScreen(
                uiState = uiState,
                onEvent = { event ->
                    when (event) {
                        is LessonScreenEvent.ToggleLessonSelection -> onToggleLessonSelection(event.assignmentId)
                        is LessonScreenEvent.SelectFirst -> onSelectFirst(event.count)
                        LessonScreenEvent.SelectAll -> onSelectAll()
                        LessonScreenEvent.SelectNone -> onSelectNone()
                        LessonScreenEvent.StartSelectedLessons -> onStartSelectedLessons()
                        is LessonScreenEvent.StudyCardSwiped -> onStudyCardSwiped(event.index)
                        LessonScreenEvent.NextStudyCard -> onNextStudyCard()
                        LessonScreenEvent.PreviousStudyCard -> onPreviousStudyCard()
                        is LessonScreenEvent.AnswerInputChange -> onAnswerInputChange(event.value)
                        LessonScreenEvent.Submit -> onSubmit()
                        LessonScreenEvent.DontKnow -> onDontKnow()
                        is LessonScreenEvent.PlayReading -> {}
                        LessonScreenEvent.Continue -> onContinue()
                        LessonScreenEvent.Retry -> onRetry()
                        LessonScreenEvent.Abandon -> onAbandon()
                        LessonScreenEvent.Done -> onDone()
                        LessonScreenEvent.Back -> onBack()
                    }
                }
            )
        }
    }

    @Test
    fun selectPhase_showsSelectedCountAndTogglesOnCheckboxRowClick() {
        var toggledId: Long? = null
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.SELECT,
                availableLessons = listOf(radicalItem, secondRadicalItem),
                selectedAssignmentIds = setOf(1L)
            ),
            onToggleLessonSelection = { toggledId = it }
        )

        composeTestRule.onNodeWithText("1 of 2 selected").assertIsDisplayed()

        composeTestRule.onNodeWithTag(LessonScreenTestTags.CUSTOMIZE_TOGGLE).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.lessonCheckboxTag(2L)).performClick()
        assert(toggledId == 2L)
    }

    @Test
    fun selectPhase_selectAllAndSelectNoneChips_invokeCallbacks() {
        var selectedAll = false
        var selectedNone = false
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.SELECT,
                availableLessons = listOf(radicalItem, secondRadicalItem),
                selectedAssignmentIds = setOf(1L)
            ),
            onSelectAll = { selectedAll = true },
            onSelectNone = { selectedNone = true }
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.CUSTOMIZE_TOGGLE).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.SELECT_ALL_CHIP).performClick()
        assert(selectedAll)
        composeTestRule.onNodeWithTag(LessonScreenTestTags.SELECT_NONE_CHIP).performClick()
        assert(selectedNone)
    }

    @Test
    fun selectPhase_stepperButtons_invokeOnSelectFirstWithClampedCount() {
        var selectedN: Int? = null
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.SELECT,
                availableLessons = listOf(radicalItem, secondRadicalItem),
                selectedAssignmentIds = setOf(1L)
            ),
            onSelectFirst = { selectedN = it }
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.STEPPER_INCREMENT).performClick()
        assert(selectedN == 2)

        composeTestRule.onNodeWithTag(LessonScreenTestTags.STEPPER_DECREMENT).performClick()
        assert(selectedN == 0)
    }

    @Test
    fun selectPhase_stepperDecrement_disabledAtZero() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.SELECT,
                availableLessons = listOf(radicalItem),
                selectedAssignmentIds = emptySet()
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.STEPPER_DECREMENT).assertIsNotEnabled()
    }

    @Test
    fun selectPhase_stepperIncrement_disabledAtTotal() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.SELECT,
                availableLessons = listOf(radicalItem),
                selectedAssignmentIds = setOf(1L)
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.STEPPER_INCREMENT).assertIsNotEnabled()
    }

    @Test
    fun selectPhase_customizeToggle_showsAndHidesChecklist() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.SELECT,
                availableLessons = listOf(radicalItem, secondRadicalItem),
                selectedAssignmentIds = setOf(1L)
            )
        )

        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.lessonCheckboxTag(1L)).assertCountEquals(0)

        composeTestRule.onNodeWithTag(LessonScreenTestTags.CUSTOMIZE_TOGGLE).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.lessonCheckboxTag(1L)).assertIsDisplayed()

        composeTestRule.onNodeWithTag(LessonScreenTestTags.CUSTOMIZE_TOGGLE).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.lessonCheckboxTag(1L)).assertCountEquals(0)
    }

    @Test
    fun selectPhase_levelGroupToggle_showsAndHidesTilesForUnselectedLevel() {
        var toggledId: Long? = null
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.SELECT,
                availableLessons = listOf(radicalItem),
                selectedAssignmentIds = emptySet()
            ),
            onToggleLessonSelection = { toggledId = it }
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.CUSTOMIZE_TOGGLE).performClick()
        composeTestRule.waitForIdle()

        // Level 1 has no current selection, so it starts collapsed.
        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.lessonCheckboxTag(1L)).assertCountEquals(0)

        composeTestRule.onNodeWithTag(LessonScreenTestTags.levelGroupToggleTag(1)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.lessonCheckboxTag(1L)).performClick()
        assert(toggledId == 1L)

        composeTestRule.onNodeWithTag(LessonScreenTestTags.levelGroupToggleTag(1)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.lessonCheckboxTag(1L)).assertCountEquals(0)
    }

    @Test
    fun selectPhase_startButton_disabledWhenNothingSelected_enabledOtherwise() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.SELECT,
                availableLessons = listOf(radicalItem),
                selectedAssignmentIds = emptySet()
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.START_SELECTED_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun selectPhase_startButton_invokesCallback_whenSelectionNonEmpty() {
        var started = false
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.SELECT,
                availableLessons = listOf(radicalItem),
                selectedAssignmentIds = setOf(1L)
            ),
            onStartSelectedLessons = { started = true }
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.START_SELECTED_BUTTON).performClick()
        assert(started)
    }

    @Test
    fun studyPhase_swipingPager_invokesOnStudyCardSwiped() {
        var swipedToIndex: Int? = null
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.STUDY,
                studyItems = listOf(radicalItem, secondRadicalItem), studyIndex = 0
            ),
            onStudyCardSwiped = { swipedToIndex = it }
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.STUDY_PAGER).performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        assert(swipedToIndex == 1)
    }

    @Test
    fun studyPhase_showsCharacterAndMnemonic() {
        setScreen(
            LessonUiState(isLoading = false, phase = LessonPhase.STUDY, studyItems = listOf(radicalItem), studyIndex = 0)
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.STUDY_CHARACTERS).assertIsDisplayed()
    }

    @Test
    fun studyPhase_showsStrokeOrderSection_whenAvailableForCurrentItem() {
        val strokes = listOf(StrokeOrderStroke(pathData = "M10,10L90,10", labelX = 5f, labelY = 5f))
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.STUDY,
                studyItems = listOf(radicalItem), studyIndex = 0,
                strokeOrderBySubjectId = mapOf(radicalItem.subjectId to StrokeOrderUiState.Available(strokes))
            )
        )

        composeTestRule.onNodeWithTag(StrokeOrderTestTags.SECTION).assertIsDisplayed()
        composeTestRule.onNodeWithTag(StrokeOrderTestTags.DIAGRAM).assertIsDisplayed()
    }

    @Test
    fun studyPhase_hidesStrokeOrderSection_whenUnavailableForCurrentItem() {
        setScreen(
            LessonUiState(isLoading = false, phase = LessonPhase.STUDY, studyItems = listOf(radicalItem), studyIndex = 0)
        )

        composeTestRule.onAllNodesWithTag(StrokeOrderTestTags.SECTION).assertCountEquals(0)
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
                currentQuizItem = radicalItem, currentQuestionType = QuestionType.MEANING,
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
                currentQuizItem = radicalItem, currentQuestionType = QuestionType.MEANING
            ),
            onAnswerInputChange = { typed = it }
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.ANSWER_FIELD).performTextInput("Mouth")
        assert(typed == "Mouth")
    }

    @Test
    fun typeMismatchWarning_showsExpectingMeaning_forMeaningQuestion() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, totalQuizCount = 1, remainingQuizCount = 1,
                currentQuizItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                answerTypeMismatchCount = 1
            )
        )

        // OutlinedTextField sets MergeDescendants on its root node, so the supportingText's own tag
        // collapses into it in the default merged tree — this needs the unmerged tree to be
        // individually queryable, same as ReviewScreenTest's equivalent.
        composeTestRule.onNodeWithTag(LessonScreenTestTags.TYPE_MISMATCH_TEXT, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Expecting the meaning", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun typeMismatchWarning_showsExpectingReading_forReadingQuestion() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, totalQuizCount = 1, remainingQuizCount = 1,
                currentQuizItem = radicalItem, currentQuestionType = QuestionType.READING,
                answerTypeMismatchCount = 1
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.TYPE_MISMATCH_TEXT, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Expecting the reading", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun typeMismatchWarning_absentBeforeAnyMismatch() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, totalQuizCount = 1, remainingQuizCount = 1,
                currentQuizItem = radicalItem, currentQuestionType = QuestionType.MEANING
            )
        )

        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.TYPE_MISMATCH_TEXT, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun typeMismatchWarning_clearsOnceUserEditsTheAnswer() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, totalQuizCount = 1, remainingQuizCount = 1,
                currentQuizItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                answerTypeMismatchCount = 1
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.TYPE_MISMATCH_TEXT, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.ANSWER_FIELD).performTextInput("W")
        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.TYPE_MISMATCH_TEXT, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun quizPhase_totalTimer_shownWhenSettingEnabledAndSessionInProgress() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, totalQuizCount = 1, remainingQuizCount = 1,
                currentQuizItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                showTotalTimer = true, sessionActiveSegmentStartMs = System.currentTimeMillis()
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.TOTAL_TIMER_TEXT).assertIsDisplayed()
    }

    @Test
    fun quizPhase_totalTimer_absentWhenSettingDisabled() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, totalQuizCount = 1, remainingQuizCount = 1,
                currentQuizItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                showTotalTimer = false, sessionActiveSegmentStartMs = System.currentTimeMillis()
            )
        )

        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.TOTAL_TIMER_TEXT).assertCountEquals(0)
    }

    @Test
    fun quizPhase_totalTimer_freezesWhilePaused_notTickingWithoutAnActiveSegment() {
        // sessionActiveSegmentStartMs is null (as if the app were backgrounded, or navigated away
        // and back) — the timer must show the frozen base, not restart from "0:00".
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, totalQuizCount = 1, remainingQuizCount = 1,
                currentQuizItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                showTotalTimer = true, sessionActiveElapsedMs = 65_000L, sessionActiveSegmentStartMs = null
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.TOTAL_TIMER_TEXT).assertTextEquals("1:05")
    }

    @Test
    fun quizPhase_questionTimer_shownWhenSettingEnabledAndSessionInProgress() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, totalQuizCount = 1, remainingQuizCount = 1,
                currentQuizItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                showQuestionTimer = true, questionStartTimeMs = System.currentTimeMillis()
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.QUESTION_TIMER_TEXT).assertIsDisplayed()
    }

    @Test
    fun quizPhase_questionTimer_absentWhenSettingDisabled() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, totalQuizCount = 1, remainingQuizCount = 1,
                currentQuizItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                showQuestionTimer = false, questionStartTimeMs = System.currentTimeMillis()
            )
        )

        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.QUESTION_TIMER_TEXT).assertCountEquals(0)
    }

    @Test
    fun quizPhase_questionTimer_freezesAtAnsweredElapsedTime_onceFeedbackIsShown() {
        // questionStartTimeMs is a full minute in the past — if the timer were still live-ticking
        // from it, it would show "1:00". The frozen questionElapsedMs must win instead.
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, totalQuizCount = 1, remainingQuizCount = 1,
                currentQuizItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                showQuestionTimer = true,
                questionStartTimeMs = System.currentTimeMillis() - 60_000,
                questionElapsedMs = 5_000L,
                feedback = AnswerFeedback(isCorrect = true, correctAnswer = "Mouth")
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.QUESTION_TIMER_TEXT).assertTextEquals(formatElapsedClock(5_000L))
    }

    @Test
    fun quizPhase_feedback_showsContinueButton() {
        var continued = false
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, totalQuizCount = 1, remainingQuizCount = 1,
                currentQuizItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                feedback = AnswerFeedback(isCorrect = true, correctAnswer = "Mouth")
            ),
            onContinue = { continued = true }
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.FEEDBACK_TEXT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.CONTINUE_BUTTON).performClick()
        assert(continued)
    }

    @Test
    fun quizPhase_subjectTypeLabel_shownWhenSettingEnabled() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, totalQuizCount = 1, remainingQuizCount = 1,
                currentQuizItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                showSubjectTypeLabel = true
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.QUIZ_SUBJECT_TYPE_LABEL).assertIsDisplayed()
        composeTestRule.onNodeWithText("Radical").assertIsDisplayed()
    }

    @Test
    fun quizPhase_subjectTypeLabel_absentWhenSettingDisabled() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, totalQuizCount = 1, remainingQuizCount = 1,
                currentQuizItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                showSubjectTypeLabel = false
            )
        )

        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.QUIZ_SUBJECT_TYPE_LABEL).assertCountEquals(0)
    }

    @Test
    fun quizPhase_continueButton_disabledBrieflyAfterIncorrectAnswer_thenEnables() {
        composeTestRule.mainClock.autoAdvance = false
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, totalQuizCount = 1, remainingQuizCount = 1,
                currentQuizItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                feedback = AnswerFeedback(isCorrect = false, correctAnswer = "Mouth")
            )
        )

        composeTestRule.mainClock.advanceTimeBy(50)
        composeTestRule.onNodeWithTag(LessonScreenTestTags.CONTINUE_BUTTON).assertIsNotEnabled()

        composeTestRule.mainClock.advanceTimeBy(1300)
        composeTestRule.onNodeWithTag(LessonScreenTestTags.CONTINUE_BUTTON).assertIsEnabled()
    }

    @Test
    fun quizPhase_continueButton_enabledImmediately_afterCorrectAnswer() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ, totalQuizCount = 1, remainingQuizCount = 1,
                currentQuizItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                feedback = AnswerFeedback(isCorrect = true, correctAnswer = "Mouth")
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.CONTINUE_BUTTON).assertIsEnabled()
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
    fun sessionComplete_showsOverviewCardWithCounts() {
        setScreen(
            LessonUiState(
                isLoading = false, isSessionComplete = true,
                sessionItemsLearned = 5, sessionItemsCorrectFirstTry = 3
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.SESSION_OVERVIEW_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithText("Items learned: 5").assertIsDisplayed()
        composeTestRule.onNodeWithText("Correct on first try: 3 of 5 (60%)").assertIsDisplayed()
    }

    @Test
    fun sessionComplete_hidesCardsWhenNothingWasLearned() {
        setScreen(LessonUiState(isLoading = false, isSessionComplete = true, sessionItemsLearned = 0))

        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.SESSION_OVERVIEW_CARD).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.SESSION_TIMING_CARD).assertCountEquals(0)
    }

    @Test
    fun sessionComplete_showsTimingCard() {
        setScreen(
            LessonUiState(
                isLoading = false, isSessionComplete = true,
                sessionItemsLearned = 3, sessionItemsCorrectFirstTry = 3,
                sessionTotalElapsedMs = 125_000L, sessionAverageTimePerItemMs = 4_500L
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.SESSION_TIMING_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithText("Total time: 2:05").assertIsDisplayed()
        composeTestRule.onNodeWithText("Avg. time per item learned: 4s").assertIsDisplayed()
    }

    @Test
    fun sessionComplete_showsSlowestAnswersCard_whenPresent() {
        setScreen(
            LessonUiState(
                isLoading = false, isSessionComplete = true,
                sessionItemsLearned = 1, sessionItemsCorrectFirstTry = 1,
                sessionSlowestAnswers = listOf(
                    LessonSlowAnswer(radicalItem, QuestionType.MEANING, 12_000L, isCorrect = true)
                )
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.SESSION_SLOWEST_CARD).assertIsDisplayed()
    }

    @Test
    fun sessionComplete_hidesSlowestAnswersCard_whenEmpty() {
        setScreen(
            LessonUiState(
                isLoading = false, isSessionComplete = true,
                sessionItemsLearned = 1, sessionItemsCorrectFirstTry = 1,
                sessionSlowestAnswers = emptyList()
            )
        )

        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.SESSION_SLOWEST_CARD).assertCountEquals(0)
    }

    @Test
    fun sessionComplete_showsMissedItemsCard_whenPresent() {
        setScreen(
            LessonUiState(
                isLoading = false, isSessionComplete = true,
                sessionItemsLearned = 2, sessionItemsCorrectFirstTry = 1,
                sessionMissedItems = listOf(radicalItem)
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.SESSION_MISSED_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithText("口").assertIsDisplayed()
    }

    @Test
    fun sessionComplete_hidesMissedItemsCard_whenEmpty() {
        setScreen(
            LessonUiState(
                isLoading = false, isSessionComplete = true,
                sessionItemsLearned = 1, sessionItemsCorrectFirstTry = 1,
                sessionMissedItems = emptyList()
            )
        )

        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.SESSION_MISSED_CARD).assertCountEquals(0)
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

    @Test
    fun overflowMenu_isAbsent_duringSelectPhase() {
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.SELECT,
                availableLessons = listOf(radicalItem), selectedAssignmentIds = setOf(1L)
            )
        )

        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.OVERFLOW_MENU).assertCountEquals(0)
    }

    @Test
    fun overflowMenu_abandonConfirmed_invokesCallback_duringStudyPhase() {
        var abandoned = false
        setScreen(
            LessonUiState(isLoading = false, phase = LessonPhase.STUDY, studyItems = listOf(radicalItem)),
            onAbandon = { abandoned = true }
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.ABANDON_MENU_ITEM).performClick()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.ABANDON_CONFIRM_BUTTON).performClick()
        assert(abandoned)
    }

    @Test
    fun overflowMenu_abandonConfirmed_invokesCallback_duringQuizPhase() {
        var abandoned = false
        setScreen(
            LessonUiState(
                isLoading = false, phase = LessonPhase.QUIZ,
                currentQuizItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1
            ),
            onAbandon = { abandoned = true }
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.ABANDON_MENU_ITEM).performClick()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.ABANDON_CONFIRM_BUTTON).performClick()
        assert(abandoned)
    }

    @Test
    fun overflowMenu_abandonCancelled_doesNotInvokeCallback() {
        var abandoned = false
        setScreen(
            LessonUiState(isLoading = false, phase = LessonPhase.STUDY, studyItems = listOf(radicalItem)),
            onAbandon = { abandoned = true }
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.ABANDON_MENU_ITEM).performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.ABANDON_CONFIRM_BUTTON).assertCountEquals(0)
        assert(!abandoned)
    }
}
