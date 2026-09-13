package com.crazyfluff.shellfstudy.feature.lesson

import com.crazyfluff.shellfstudy.shared.designsystem.settings.DisplaySettings
import com.crazyfluff.shellfstudy.shared.designsystem.settings.LocalDisplaySettings
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonActions
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonScreen
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonScreenTestTags
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonSort
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonUiState
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.fakes.FakePronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.data.DEFAULT_LESSON_BATCH_SIZE
import com.crazyfluff.shellfstudy.shared.data.model.ContextSentence
import com.crazyfluff.shellfstudy.shared.data.model.LessonItem
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import com.crazyfluff.shellfstudy.shared.data.model.StrokeOrderStroke
import com.crazyfluff.shellfstudy.shared.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.AnswerReadingHint
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.formatElapsedClock
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.LocalPronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentUiState
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.SubjectDetailTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.strokeorder.StrokeOrderTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.strokeorder.StrokeOrderUiState
import com.crazyfluff.shellfstudy.shared.designsystem.text.ContextSentenceRowTestTags
import com.crazyfluff.shellfstudy.shared.feature.search.SearchOverlayTestTags
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.crazyfluff.shellfstudy.shared.quiz.AnswerFeedback
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType
import com.crazyfluff.shellfstudy.shared.quiz.QuizTimingUiState
import com.crazyfluff.shellfstudy.shared.quiz.SlowAnswer
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Runs under Robolectric (JVM) — this screen is driven purely by state, no device features needed.
 * Pinned to SDK 35: Robolectric 4.15.1 doesn't yet have shadows for this project's targetSdk (37).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class LessonScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

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

    private val glyphlessRadicalItem = LessonItem(
        assignmentId = 3,
        subjectId = 3,
        subjectType = SubjectType.RADICAL,
        characters = null,
        characterImageUrl = "https://example.com/hill.png",
        level = 1,
        meanings = listOf("Hill"),
        readings = emptyList(),
        meaningMnemonic = "A small mound of earth.",
        readingMnemonic = null
    )

    private val kanjiItem = LessonItem(
        assignmentId = 4,
        subjectId = 4,
        subjectType = SubjectType.KANJI,
        characters = "口",
        level = 1,
        meanings = listOf("Mouth"),
        readings = listOf("コウ"),
        meaningMnemonic = "A mouth.",
        readingMnemonic = "A row of mouths."
    )

    private val vocabularyItem = LessonItem(
        assignmentId = 5,
        subjectId = 5,
        subjectType = SubjectType.VOCABULARY,
        characters = "口",
        level = 2,
        meanings = listOf("Mouth"),
        readings = listOf("くち"),
        meaningMnemonic = "A mouth.",
        readingMnemonic = "A mouth."
    )

    // --- LessonUiState fixture helpers -------------------------------------------------------
    // LessonUiState.Phase is a sealed hierarchy now (Select/Study/Quiz/Complete/etc.) instead of a
    // flat data class with a `phase: LessonPhase` enum plus a pile of independent nullable fields.
    // These collapse the repetitive `LessonUiState(phase = LessonUiState.Phase.Xxx(...))` shape down
    // to just the fields each test actually varies.

    private fun selectState(
        availableLessons: List<LessonItem> = emptyList(),
        selectedAssignmentIds: Set<Long> = emptySet(),
        batchSize: Int = DEFAULT_LESSON_BATCH_SIZE,
        sort: LessonSort = LessonSort.DEFAULT
    ) = LessonUiState(
        phase = LessonUiState.Phase.Select(
            availableLessons = availableLessons,
            selectedAssignmentIds = selectedAssignmentIds,
            batchSize = batchSize,
            sort = sort
        )
    )

    private fun studyState(
        studyItems: List<LessonItem>,
        studyIndex: Int = 0,
        strokeOrderBySubjectId: Map<Long, StrokeOrderUiState> = emptyMap(),
        relatedSubjectsById: Map<Long, SubjectSummary> = emptyMap(),
        batchIndex: Int = 0,
        batchCount: Int = 1
    ) = LessonUiState(
        phase = LessonUiState.Phase.Study(
            studyItems = studyItems,
            studyIndex = studyIndex,
            strokeOrderBySubjectId = strokeOrderBySubjectId,
            batchIndex = batchIndex,
            batchCount = batchCount
        ),
        relatedSubjectsById = relatedSubjectsById
    )

    private fun batchCompleteState(
        batchIndex: Int = 0,
        batchCount: Int = 1,
        itemsLearned: Int = 2,
        itemsCorrectFirstTry: Int = 2,
        missedItems: List<LessonItem> = emptyList(),
        remainingSessionItems: Int = 0
    ) = LessonUiState(
        phase = LessonUiState.Phase.BatchComplete(
            batchIndex = batchIndex,
            batchCount = batchCount,
            itemsLearned = itemsLearned,
            itemsCorrectFirstTry = itemsCorrectFirstTry,
            missedItems = missedItems,
            remainingSessionItems = remainingSessionItems
        )
    )

    private fun quizState(
        currentItem: LessonItem,
        currentQuestionType: QuestionType,
        answerInput: String = "",
        feedback: AnswerFeedback? = null,
        answerTypeMismatchCount: Int = 0,
        totalQuizCount: Int = 0,
        remainingQuizCount: Int = 0,
        questionSequence: Int = 0,
        timing: QuizTimingUiState = QuizTimingUiState(),
        answerReading: String? = null,
        answerPitchAccents: PitchAccentUiState = PitchAccentUiState.Unavailable,
        answerReadingAudio: PronunciationAudio? = null
    ) = LessonUiState(
        phase = LessonUiState.Phase.Quiz(
            currentItem = currentItem,
            currentQuestionType = currentQuestionType,
            answerInput = answerInput,
            feedback = feedback,
            answerTypeMismatchCount = answerTypeMismatchCount,
            totalQuizCount = totalQuizCount,
            remainingQuizCount = remainingQuizCount,
            questionSequence = questionSequence,
            timing = timing,
            answerHint = answerReading?.let {
                AnswerReadingHint(reading = it, audio = answerReadingAudio)
            }
        ),
        // The quiz hint reads the live map for the current item — spelled out here so a fixture can
        // still express "this word's pitch accent is Available/Unavailable" in one line.
        pitchAccentsBySubjectId = mapOf(currentItem.subjectId to answerPitchAccents)
    )

    private fun completeState(
        sessionItemsLearned: Int = 0,
        sessionItemsCorrectFirstTry: Int = 0,
        sessionMissedItems: List<LessonItem> = emptyList(),
        sessionTotalElapsedMs: Long = 0L,
        sessionAverageTimePerItemMs: Long = 0L,
        sessionSlowestAnswers: List<SlowAnswer<LessonItem>> = emptyList()
    ) = LessonUiState(
        phase = LessonUiState.Phase.Complete(
            sessionItemsLearned = sessionItemsLearned,
            sessionItemsCorrectFirstTry = sessionItemsCorrectFirstTry,
            sessionMissedItems = sessionMissedItems,
            sessionTotalElapsedMs = sessionTotalElapsedMs,
            sessionAverageTimePerItemMs = sessionAverageTimePerItemMs,
            sessionSlowestAnswers = sessionSlowestAnswers
        )
    )

    /**
     * Renders the screen against a recording stand-in for the ViewModel, so a control's wiring can be
     * asserted without standing up the repository graph. Rendering assertions pass only [uiState] and
     * ignore [actions].
     */
    private fun setScreen(
        uiState: LessonUiState,
        actions: LessonActions = RecordingLessonActions(),
        audioPlayer: FakePronunciationAudioPlayer = FakePronunciationAudioPlayer(),
        onSessionComplete: () -> Unit = {},
        onBack: () -> Unit = {},
        displaySettings: DisplaySettings = DisplaySettings()
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalPronunciationAudioPlayer provides audioPlayer,
                LocalDisplaySettings provides displaySettings
            ) {
                LessonScreen(
                    uiState = uiState,
                    actions = actions,
                    onSessionComplete = onSessionComplete,
                    onBack = onBack
                )
            }
        }
    }

    @Test
    fun searchButton_opensInlineSearchOverlay() {
        setScreen(
            selectState(
                availableLessons = listOf(radicalItem, secondRadicalItem),
                selectedAssignmentIds = setOf(1L)
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.SEARCH_BUTTON).performClick()
        composeTestRule.onNodeWithTag(SearchOverlayTestTags.QUERY_FIELD).assertIsDisplayed()
    }

    @Test
    fun selectPhase_showsSelectedCountAndTogglesOnCheckboxRowClick() {
        val actions = RecordingLessonActions()
        setScreen(
            selectState(
                availableLessons = listOf(radicalItem, secondRadicalItem),
                selectedAssignmentIds = setOf(1L)
            ),
            actions = actions
        )

        composeTestRule.onNodeWithText("1 of 2 selected").assertIsDisplayed()

        composeTestRule.onNodeWithTag(LessonScreenTestTags.CUSTOMIZE_TOGGLE).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.lessonCheckboxTag(2L)).performClick()
        assertThat(actions.lastArgumentOf("toggleLessonSelection")).isEqualTo(2L)
    }

    @Test
    fun customizeSelection_rendersImageForGlyphlessRadical_notMeaningText() {
        // Glyph-less radicals (e.g. "Hill") carry a characterImageUrl and no characters — the tile
        // should render the image (via SubjectGlyph), not fall back to printing the meaning text.
        setScreen(
            selectState(
                availableLessons = listOf(glyphlessRadicalItem),
                selectedAssignmentIds = setOf(3L)
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.CUSTOMIZE_TOGGLE).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(LessonScreenTestTags.lessonCheckboxTag(3L)).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Hill").assertCountEquals(0)
    }

    @Test
    fun selectPhase_selectAllAndSelectNoneChips_invokeCallbacks() {
        val actions = RecordingLessonActions()
        setScreen(
            selectState(
                availableLessons = listOf(radicalItem, secondRadicalItem),
                selectedAssignmentIds = setOf(1L)
            ),
            actions = actions
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.CUSTOMIZE_TOGGLE).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.SELECT_ALL_CHIP).performClick()
        assertThat(actions.calls).contains("selectAll")
        composeTestRule.onNodeWithTag(LessonScreenTestTags.SELECT_NONE_CHIP).performClick()
        assertThat(actions.calls).contains("selectNone")
    }

    @Test
    fun selectPhase_stepperButtons_invokeOnSelectFirstWithClampedCount() {
        val actions = RecordingLessonActions()
        setScreen(
            selectState(
                availableLessons = listOf(radicalItem, secondRadicalItem),
                selectedAssignmentIds = setOf(1L)
            ),
            actions = actions
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.STEPPER_INCREMENT).performClick()
        assertThat(actions.lastArgumentOf("selectFirst")).isEqualTo(2)

        composeTestRule.onNodeWithTag(LessonScreenTestTags.STEPPER_DECREMENT).performClick()
        assertThat(actions.lastArgumentOf("selectFirst")).isEqualTo(0)
    }

    @Test
    fun selectPhase_stepperDecrement_disabledAtZero() {
        setScreen(
            selectState(
                availableLessons = listOf(radicalItem),
                selectedAssignmentIds = emptySet()
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.STEPPER_DECREMENT).assertIsNotEnabled()
    }

    @Test
    fun selectPhase_stepperIncrement_disabledAtTotal() {
        setScreen(
            selectState(
                availableLessons = listOf(radicalItem),
                selectedAssignmentIds = setOf(1L)
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.STEPPER_INCREMENT).assertIsNotEnabled()
    }

    @Test
    fun selectPhase_customizeToggle_showsAndHidesChecklist() {
        setScreen(
            selectState(
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
    fun selectPhase_sortDropdown_showsTheCurrentSortAndInvokesOnSetLessonSort() {
        val actions = RecordingLessonActions()
        setScreen(
            selectState(
                availableLessons = listOf(radicalItem, kanjiItem, vocabularyItem),
                selectedAssignmentIds = setOf(1L)
            ),
            actions = actions
        )

        // Only part of the checklist, not of the quick pick.
        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.SORT_DROPDOWN).assertCountEquals(0)

        composeTestRule.onNodeWithTag(LessonScreenTestTags.CUSTOMIZE_TOGGLE).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(LessonScreenTestTags.SORT_DROPDOWN).assertIsDisplayed()
        composeTestRule.onNodeWithText("Sort: Default").assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.SORT_DROPDOWN).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.sortOptionTag(LessonSort.KANJI_FIRST)).performClick()

        assertThat(actions.lastArgumentOf("setLessonSort")).isEqualTo(LessonSort.KANJI_FIRST)
    }

    @Test
    fun selectPhase_sortDropdown_reflectsANonDefaultSort() {
        setScreen(
            selectState(
                availableLessons = listOf(radicalItem, kanjiItem, vocabularyItem),
                sort = LessonSort.KANJI_FIRST
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.CUSTOMIZE_TOGGLE).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Sort: Kanji first").assertIsDisplayed()
    }

    @Test
    fun selectPhase_sortDropdown_absentWhenTheQueueHoldsASingleType() {
        setScreen(selectState(availableLessons = listOf(radicalItem, secondRadicalItem)))

        composeTestRule.onNodeWithTag(LessonScreenTestTags.CUSTOMIZE_TOGGLE).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.SORT_DROPDOWN).assertCountEquals(0)
    }

    @Test
    fun selectPhase_levelGroupToggle_showsAndHidesTilesForUnselectedLevel() {
        val actions = RecordingLessonActions()
        setScreen(
            selectState(
                availableLessons = listOf(radicalItem),
                selectedAssignmentIds = emptySet()
            ),
            actions = actions
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.CUSTOMIZE_TOGGLE).performClick()
        composeTestRule.waitForIdle()

        // Level 1 has no current selection, so it starts collapsed.
        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.lessonCheckboxTag(1L)).assertCountEquals(0)

        composeTestRule.onNodeWithTag(LessonScreenTestTags.levelGroupToggleTag(1)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.lessonCheckboxTag(1L)).performClick()
        assertThat(actions.lastArgumentOf("toggleLessonSelection")).isEqualTo(1L)

        composeTestRule.onNodeWithTag(LessonScreenTestTags.levelGroupToggleTag(1)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.lessonCheckboxTag(1L)).assertCountEquals(0)
    }

    @Test
    fun selectPhase_startButton_disabledWhenNothingSelected_enabledOtherwise() {
        setScreen(
            selectState(
                availableLessons = listOf(radicalItem),
                selectedAssignmentIds = emptySet()
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.START_SELECTED_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun selectPhase_startButton_invokesCallback_whenSelectionNonEmpty() {
        val actions = RecordingLessonActions()
        setScreen(
            selectState(
                availableLessons = listOf(radicalItem),
                selectedAssignmentIds = setOf(1L)
            ),
            actions = actions
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.START_SELECTED_BUTTON).performClick()
        assertThat(actions.calls).contains("startSelectedLessons")
    }

    @Test
    fun studyPhase_swipingPager_invokesOnStudyCardSwiped() {
        val actions = RecordingLessonActions()
        setScreen(
            studyState(studyItems = listOf(radicalItem, secondRadicalItem), studyIndex = 0),
            actions = actions
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.STUDY_PAGER).performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        assertThat(actions.lastArgumentOf("onStudyCardSwiped")).isEqualTo(1)
    }

    @Test
    fun studyPhase_showsCharacterAndMnemonic() {
        setScreen(studyState(studyItems = listOf(radicalItem), studyIndex = 0))

        composeTestRule.onNodeWithTag(LessonScreenTestTags.STUDY_CHARACTERS).assertIsDisplayed()
    }

    @Test
    fun studyPhase_headlineGlyphBoxIsTrimmedToTheInk() {
        setScreen(studyState(studyItems = listOf(radicalItem), studyIndex = 0))

        // The character only fills ~55% of a square box, and that empty band is what pushed the meaning
        // away from the glyph. The box is deliberately shorter than the 96dp ink size — a square box
        // here means the trim was lost.
        val glyphBounds = composeTestRule.onNodeWithTag(LessonScreenTestTags.STUDY_CHARACTERS)
            .getUnclippedBoundsInRoot()
        assertThat(glyphBounds.bottom - glyphBounds.top < 96.dp).isTrue()
    }

    @Test
    fun studyPhase_auxiliaryMeaningsUnderCap_showAllWithoutTruncation() {
        val item = radicalItem.copy(auxiliaryMeanings = listOf("Aqua", "H2O"))
        setScreen(studyState(studyItems = listOf(item), studyIndex = 0))

        composeTestRule.onNodeWithText("Aqua, H2O").assertIsDisplayed()
    }

    @Test
    fun studyPhase_auxiliaryMeaningsOverCap_tapExpandsThenCollapses() {
        val item = radicalItem.copy(auxiliaryMeanings = listOf("A", "B", "C", "D", "E"))
        setScreen(studyState(studyItems = listOf(item), studyIndex = 0))

        composeTestRule.onNodeWithText("A, B, C +2 more").assertIsDisplayed()

        composeTestRule.onNodeWithTag(SubjectDetailTestTags.AUXILIARY_MEANINGS_TEXT).performClick()
        composeTestRule.onNodeWithText("A, B, C, D, E").assertIsDisplayed()

        composeTestRule.onNodeWithTag(SubjectDetailTestTags.AUXILIARY_MEANINGS_TEXT).performClick()
        composeTestRule.onNodeWithText("A, B, C +2 more").assertIsDisplayed()
    }

    @Test
    fun studyPhase_showsStrokeOrderSection_whenAvailableForCurrentItem() {
        val strokes = listOf(StrokeOrderStroke(pathData = "M10,10L90,10", labelX = 5f, labelY = 5f))
        setScreen(
            studyState(
                studyItems = listOf(radicalItem),
                studyIndex = 0,
                strokeOrderBySubjectId = mapOf(radicalItem.subjectId to StrokeOrderUiState.Available(strokes))
            )
        )

        composeTestRule.onNodeWithTag(StrokeOrderTestTags.SECTION).assertIsDisplayed()
        composeTestRule.onNodeWithTag(StrokeOrderTestTags.DIAGRAM).assertIsDisplayed()
    }

    @Test
    fun studyPhase_hidesStrokeOrderSection_whenUnavailableForCurrentItem() {
        setScreen(studyState(studyItems = listOf(radicalItem), studyIndex = 0))

        composeTestRule.onAllNodesWithTag(StrokeOrderTestTags.SECTION).assertCountEquals(0)
    }

    @Test
    fun studyPhase_relatedSubjectIdsNotYetCached_showsNotLoadedCaption() {
        // amalgamationSubjectIds = [99] but relatedSubjectsById has no entry for it — distinct from
        // "this item is used in nothing" (the default empty list on radicalItem/etc.).
        setScreen(
            studyState(
                studyItems = listOf(radicalItem.copy(amalgamationSubjectIds = listOf(99))),
                studyIndex = 0
            )
        )

        composeTestRule.onNodeWithText("Not loaded yet").assertIsDisplayed()
    }

    @Test
    fun studyPhase_noRelatedSubjectIds_showsNoRelatedSubjectsSection() {
        setScreen(studyState(studyItems = listOf(radicalItem), studyIndex = 0))

        composeTestRule.onAllNodesWithText("Not loaded yet").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Used in").assertCountEquals(0)
    }

    @Test
    fun studyPhase_previousDisabledOnFirstCard_nextLabelledStartQuizOnLastCard() {
        setScreen(studyState(studyItems = listOf(radicalItem), studyIndex = 0))

        composeTestRule.onNodeWithTag(LessonScreenTestTags.STUDY_PREVIOUS_BUTTON).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.START_QUIZ_BUTTON).assertIsDisplayed()
    }

    @Test
    fun studyPhase_multiBatch_sitsTheBatchContextBesideTheBatchProgressCount() {
        setScreen(
            studyState(
                studyItems = listOf(radicalItem, secondRadicalItem),
                studyIndex = 1,
                batchIndex = 0,
                batchCount = 3
            )
        )

        // The count is the batch's own position, with the batch context annotating it rather than
        // competing with it, and no session total repeated from the picker.
        composeTestRule.onNodeWithTag(LessonScreenTestTags.STUDY_PROGRESS_COUNT).assertTextEquals("2 / 2")
        composeTestRule.onNodeWithTag(LessonScreenTestTags.STUDY_BATCH_LABEL).assertTextEquals("Batch 1 of 3")
        // The last card of a non-final batch hands off to that batch's quiz; the button says so the
        // same way it does for a single-batch session.
        composeTestRule.onNodeWithTag(LessonScreenTestTags.START_QUIZ_BUTTON).assertTextEquals("Start Quiz")
    }

    @Test
    fun studyPhase_singleBatch_hidesBatchContextAndKeepsStartQuizLabel() {
        setScreen(studyState(studyItems = listOf(radicalItem), studyIndex = 0))

        composeTestRule.onNodeWithTag(LessonScreenTestTags.STUDY_BATCH_LABEL).assertDoesNotExist()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.START_QUIZ_BUTTON).assertTextEquals("Start Quiz")
    }

    @Test
    fun selectPhase_typeSelectorChips_showOnePerAvailableTypeWithCounts() {
        setScreen(
            selectState(
                availableLessons = listOf(radicalItem, kanjiItem, vocabularyItem),
                selectedAssignmentIds = setOf(1L)
            )
        )

        // Whole-type shortcuts live in the picker's header, so they're reachable without opening the
        // checklist — that's what makes one-tap kanji-only possible from the quick pick.
        composeTestRule.onNodeWithTag(LessonScreenTestTags.typeSelectorChipTag(SubjectType.RADICAL))
            .assertTextEquals("Radical · 1")
        composeTestRule.onNodeWithTag(LessonScreenTestTags.typeSelectorChipTag(SubjectType.KANJI))
            .assertTextEquals("Kanji · 1")
        composeTestRule.onNodeWithTag(LessonScreenTestTags.typeSelectorChipTag(SubjectType.VOCABULARY))
            .assertTextEquals("Vocabulary · 1")
    }

    @Test
    fun selectPhase_typeSelectorChips_fillOnlyWhenTheWholeTypeIsSelected() {
        setScreen(
            selectState(
                // Two radicals with only one of them selected: the chip must read as unfilled, since
                // tapping it would add the missing lesson rather than clear the type.
                availableLessons = listOf(radicalItem, secondRadicalItem, kanjiItem),
                selectedAssignmentIds = setOf(1L, 4L)
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.typeSelectorChipTag(SubjectType.RADICAL)).assertIsNotSelected()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.typeSelectorChipTag(SubjectType.KANJI)).assertIsSelected()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.typeSelectorChipTag(SubjectType.VOCABULARY)).assertDoesNotExist()
    }

    @Test
    fun selectPhase_typeSelectorChips_absentWhenTheQueueHoldsASingleType() {
        setScreen(selectState(availableLessons = listOf(radicalItem, secondRadicalItem)))

        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.typeSelectorChipTag(SubjectType.RADICAL)).assertCountEquals(0)
    }

    @Test
    fun selectPhase_typeSelectorChipClick_invokesOnToggleTypeSelection() {
        val actions = RecordingLessonActions()
        setScreen(
            selectState(availableLessons = listOf(radicalItem, kanjiItem)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.typeSelectorChipTag(SubjectType.KANJI)).performClick()

        assertThat(actions.lastArgumentOf("toggleLessonTypeSelection")).isEqualTo(SubjectType.KANJI)
    }

    @Test
    fun batchCompletePhase_offersTheNextBatchAndAChanceToStop() {
        val actions = RecordingLessonActions()
        setScreen(
            batchCompleteState(
                batchIndex = 0,
                batchCount = 3,
                itemsLearned = 5,
                itemsCorrectFirstTry = 4,
                remainingSessionItems = 10
            ),
            actions = actions
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.BATCH_COMPLETE_HEADLINE).assertTextEquals("Batch 1 of 3 done!")
        composeTestRule.onNodeWithTag(LessonScreenTestTags.BATCH_COMPLETE_SUMMARY_TEXT)
            .assertTextContains("5 learned · 4 right first try")
        composeTestRule.onNodeWithTag(LessonScreenTestTags.CONTINUE_SESSION_BUTTON).assertTextEquals("Continue")
        // Stopping early is a first-class outcome, not an abandon.
        composeTestRule.onNodeWithTag(LessonScreenTestTags.FINISH_FOR_NOW_BUTTON).assertIsDisplayed()

        composeTestRule.onNodeWithTag(LessonScreenTestTags.CONTINUE_SESSION_BUTTON).performClick()
        assertThat(actions.calls).contains("continueSession")
        assertThat(actions.calls).doesNotContain("finishForNow")
    }

    @Test
    fun batchCompletePhase_finishForNow_invokesItsCallback() {
        val actions = RecordingLessonActions()
        setScreen(
            batchCompleteState(
                remainingSessionItems = 2
            ),
            actions = actions
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.FINISH_FOR_NOW_BUTTON).performClick()

        assertThat(actions.calls).contains("finishForNow")
    }

    @Test
    fun batchCompletePhase_listsTheBatchesMisses() {
        setScreen(
            batchCompleteState(
                batchIndex = 0,
                batchCount = 2,
                itemsLearned = 5,
                itemsCorrectFirstTry = 3,
                missedItems = listOf(radicalItem, secondRadicalItem),
                remainingSessionItems = 3
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.BATCH_COMPLETE_MISSED_TEXT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.BATCH_COMPLETE_SUMMARY_TEXT)
            .assertTextContains("5 learned · 3 right first try")
        composeTestRule.onNodeWithTag(LessonScreenTestTags.CONTINUE_SESSION_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.FINISH_FOR_NOW_BUTTON).assertIsDisplayed()
    }

    @Test
    fun studyPhase_nextButton_invokesCallback() {
        val actions = RecordingLessonActions()
        setScreen(
            studyState(studyItems = listOf(radicalItem), studyIndex = 0),
            actions = actions
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.START_QUIZ_BUTTON).performClick()
        assertThat(actions.calls).contains("nextStudyCard")
    }

    @Test
    fun quizPhase_submittingAnswer_invokesOnSubmit() {
        val actions = RecordingLessonActions()
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1, answerInput = "Mouth"
            ),
            actions = actions
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.SUBMIT_BUTTON).performClick()
        assertThat(actions.calls).contains("submitAnswer")
    }

    @Test
    fun studyPhase_contextSentenceLookupButton_launchesAkebiDirectly_whenInstalled() {
        // Context sentences are shown on the flashcard study step (before the quiz), see
        // LessonContextSentencesSection's call site. This fires the exact ACTION_PROCESS_TEXT
        // intent Akebi already registers for, directly, with no chooser and no selection gesture.
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.craxic.akebifree"
                name = "com.craxic.akebifree.ProcessTextActivity"
            }
        }
        shadowOf(composeTestRule.activity.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").setPackage("com.craxic.akebifree"),
            resolveInfo
        )
        val vocabItem = radicalItem.copy(
            subjectType = SubjectType.VOCABULARY,
            contextSentences = listOf(ContextSentence(japanese = "水を飲みます。", english = "I drink water."))
        )
        setScreen(studyState(studyItems = listOf(vocabItem), studyIndex = 0))

        composeTestRule.onNodeWithTag(ContextSentenceRowTestTags.SHARE_BUTTON).performScrollTo().performClick()

        val started = shadowOf(composeTestRule.activity).nextStartedActivity
        assertThat(started.action).isEqualTo(Intent.ACTION_PROCESS_TEXT)
        assertThat(started.`package`).isEqualTo("com.craxic.akebifree")
        assertThat(started.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)).isEqualTo("水を飲みます。")
    }

    @Test
    fun studyPhase_contextSentenceLookupButton_opensPlayStoreListing_whenAkebiNotInstalled() {
        val playStoreResolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.android.browser"
                name = "com.android.browser.BrowserActivity"
            }
        }
        shadowOf(composeTestRule.activity.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.craxic.akebifree")),
            playStoreResolveInfo
        )
        val vocabItem = radicalItem.copy(
            subjectType = SubjectType.VOCABULARY,
            contextSentences = listOf(ContextSentence(japanese = "水を飲みます。", english = "I drink water."))
        )
        setScreen(studyState(studyItems = listOf(vocabItem), studyIndex = 0))

        composeTestRule.onNodeWithTag(ContextSentenceRowTestTags.SHARE_BUTTON).performScrollTo().performClick()

        val started = shadowOf(composeTestRule.activity).nextStartedActivity
        assertThat(started.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(started.data.toString()).contains("com.craxic.akebifree")
    }

    @Test
    fun quizPhase_typingAnswer_invokesCallback() {
        val actions = RecordingLessonActions()
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1
            ),
            actions = actions
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.ANSWER_FIELD).performTextInput("Mouth")
        // TextFieldState pushes edits up via a LaunchedEffect/snapshotFlow, one dispatch removed
        // from performTextInput itself — wait for that to land before reading the callback value.
        composeTestRule.waitForIdle()
        assertThat(actions.lastArgumentOf("onAnswerInputChange")).isEqualTo("Mouth")
    }

    /** Regression test for a requeued question (same item/questionType reappearing after an
     *  incorrect answer, e.g. the last item left in the quiz queue) failing to clear the visible
     *  answer field even though `uiState.answerInput` resets to "" — the field's real backing state
     *  is a locally-`remember`ed `TextFieldState` keyed on `focusResetKey`, which only re-seeds when
     *  that key's identity changes. Bumping `questionSequence` on every advance (even a same-item
     *  one) is what forces that re-seed; asserting only against `uiState.answerInput` wouldn't catch
     *  this. */
    @Test
    fun requeuedSameQuestion_clearsVisibleAnswerField() {
        var state by mutableStateOf(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1, answerInput = "", questionSequence = 0
            )
        )
        composeTestRule.setContent {
            LessonScreen(
                uiState = state,
                actions = RecordingLessonActions(),
                onSessionComplete = {},
                onBack = {}
            )
        }

        composeTestRule.onNodeWithTag(LessonScreenTestTags.ANSWER_FIELD).performTextInput("wrong answer")
        composeTestRule.waitForIdle()

        // Same item/questionType come back as current again (a requeue), with answerInput reset
        // and questionSequence bumped — mirrors what LessonViewModel.advanceQuiz() does.
        state = quizState(
            currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
            totalQuizCount = 1, remainingQuizCount = 1, answerInput = "", questionSequence = 1
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(LessonScreenTestTags.ANSWER_FIELD).assertTextEquals("答え", "")
    }

    @Test
    fun typeMismatchWarning_showsExpectingMeaning_forMeaningQuestion() {
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1, answerTypeMismatchCount = 1
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
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.READING,
                totalQuizCount = 1, remainingQuizCount = 1, answerTypeMismatchCount = 1
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.TYPE_MISMATCH_TEXT, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Expecting the reading", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun typeMismatchWarning_absentBeforeAnyMismatch() {
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1
            )
        )

        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.TYPE_MISMATCH_TEXT, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun typeMismatchWarning_clearsOnceUserEditsTheAnswer() {
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1, answerTypeMismatchCount = 1
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.TYPE_MISMATCH_TEXT, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.ANSWER_FIELD).performTextInput("W")
        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.TYPE_MISMATCH_TEXT, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun quizPhase_totalTimer_shownWhenSettingEnabledAndSessionInProgress() {
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1,
                timing = QuizTimingUiState(sessionActiveSegmentStartMs = System.currentTimeMillis()),
            ),
            displaySettings = DisplaySettings(showTotalTimer = true),
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.TOTAL_TIMER_TEXT).assertIsDisplayed()
    }

    @Test
    fun quizPhase_totalTimer_absentWhenSettingDisabled() {
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1,
                timing = QuizTimingUiState(sessionActiveSegmentStartMs = System.currentTimeMillis()),
            ),
            displaySettings = DisplaySettings(showTotalTimer = false),
        )

        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.TOTAL_TIMER_TEXT).assertCountEquals(0)
    }

    @Test
    fun quizPhase_totalTimer_freezesWhilePaused_notTickingWithoutAnActiveSegment() {
        // sessionActiveSegmentStartMs is null (as if the app were backgrounded, or navigated away
        // and back) — the timer must show the frozen base, not restart from "0:00".
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1,
                timing = QuizTimingUiState(sessionActiveElapsedMs = 65_000L, sessionActiveSegmentStartMs = null),
            ),
            displaySettings = DisplaySettings(showTotalTimer = true),
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.TOTAL_TIMER_TEXT).assertTextEquals("1:05")
    }

    @Test
    fun quizPhase_questionTimer_shownWhenSettingEnabledAndSessionInProgress() {
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1,
                timing = QuizTimingUiState(questionActiveSegmentStartMs = System.currentTimeMillis()),
            ),
            displaySettings = DisplaySettings(showQuestionTimer = true),
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.QUESTION_TIMER_TEXT).assertIsDisplayed()
    }

    @Test
    fun quizPhase_questionTimer_absentWhenSettingDisabled() {
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1,
                timing = QuizTimingUiState(questionActiveSegmentStartMs = System.currentTimeMillis()),
            ),
            displaySettings = DisplaySettings(showQuestionTimer = false),
        )

        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.QUESTION_TIMER_TEXT).assertCountEquals(0)
    }

    @Test
    fun quizPhase_questionTimer_freezesAtAnsweredElapsedTime_onceFeedbackIsShown() {
        // questionActiveSegmentStartMs is a full minute in the past — if the timer were still
        // live-ticking from it, it would show "1:00". The frozen questionElapsedMs must win instead.
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1,
                timing = QuizTimingUiState(
                    questionActiveSegmentStartMs = System.currentTimeMillis() - 60_000,
                    questionElapsedMs = 5_000L
                ),
                feedback = AnswerFeedback(isCorrect = true, correctAnswer = "Mouth")
            ),
            displaySettings = DisplaySettings(showQuestionTimer = true),
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.QUESTION_TIMER_TEXT).assertTextEquals(formatElapsedClock(5_000L))
    }

    @Test
    fun quizPhase_questionTimer_freezesWhilePaused_notTickingWithoutAnActiveSegment() {
        // questionActiveSegmentStartMs is null (as if the app were backgrounded mid-question) — the
        // timer must show the frozen base, not restart from "0:00" or keep ticking through the gap.
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1,
                timing = QuizTimingUiState(questionActiveElapsedMs = 5_000L, questionActiveSegmentStartMs = null),
            ),
            displaySettings = DisplaySettings(showQuestionTimer = true),
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.QUESTION_TIMER_TEXT).assertTextEquals("0:05")
    }

    @Test
    fun quizPhase_feedback_showsContinueButton() {
        val actions = RecordingLessonActions()
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1,
                feedback = AnswerFeedback(isCorrect = true, correctAnswer = "Mouth")
            ),
            actions = actions
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.FEEDBACK_TEXT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.CONTINUE_BUTTON).performClick()
        assertThat(actions.calls).contains("onContinue")
    }

    @Test
    fun quizPhase_answerReadingPitchAccentHint_shownForReadingQuestionWhenSettingEnabledAndAnswered() {
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.READING,
                totalQuizCount = 1, remainingQuizCount = 1,
                feedback = AnswerFeedback(isCorrect = true, correctAnswer = "みず"),
                answerReading = "みず",
                answerPitchAccents = PitchAccentUiState.Available(listOf(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0)))
            ),
            displaySettings = DisplaySettings(showAnswerReadingPitchAccent = true),
        )

        composeTestRule.onNodeWithTag(PitchAccentTestTags.ROOT).assertIsDisplayed()
    }

    @Test
    fun quizPhase_answerReadingPitchAccentHint_absentWhenSettingDisabled() {
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.READING,
                totalQuizCount = 1, remainingQuizCount = 1,
                feedback = AnswerFeedback(isCorrect = true, correctAnswer = "みず"),
                answerReading = "みず"
            ),
            displaySettings = DisplaySettings(showAnswerReadingPitchAccent = false),
        )

        composeTestRule.onAllNodesWithTag(PitchAccentTestTags.ROOT).assertCountEquals(0)
    }

    @Test
    fun quizPhase_answerReadingPitchAccentHint_absentForMeaningQuestionEvenWithSettingEnabled() {
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1,
                feedback = AnswerFeedback(isCorrect = true, correctAnswer = "Mouth"),
                // answerReading stays null — the ViewModel never populates it for a meaning question.
            ),
            displaySettings = DisplaySettings(showAnswerReadingPitchAccent = true),
        )

        composeTestRule.onAllNodesWithTag(PitchAccentTestTags.ROOT).assertCountEquals(0)
    }

    @Test
    fun quizPhase_answerReadingPitchAccentHint_playButton_playsTheAnswerReadingClip() {
        val audio = PronunciationAudio(
            url = "https://api.wanikani.com/audio/mizu.mp3",
            contentType = "audio/mpeg",
            pronunciation = "みず",
            gender = null,
            voiceActorId = null,
            voiceActorName = null,
            voiceDescription = null
        )
        val itemWithAudio = radicalItem.copy(pronunciationAudios = listOf(audio))
        val player = FakePronunciationAudioPlayer()
        setScreen(
            quizState(
                currentItem = itemWithAudio, currentQuestionType = QuestionType.READING,
                totalQuizCount = 1, remainingQuizCount = 1,
                feedback = AnswerFeedback(isCorrect = true, correctAnswer = "みず"),
                answerReading = "みず",
                answerPitchAccents = PitchAccentUiState.Available(listOf(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0))),
                answerReadingAudio = audio
            ),
            audioPlayer = player,
            displaySettings = DisplaySettings(showAnswerReadingPitchAccent = true),
        )

        composeTestRule.onNodeWithContentDescription("Play pronunciation for みず").performClick()
        assertThat(player.playedAudios).containsExactly(audio)
    }

    @Test
    fun quizPhase_answerReadingPitchAccentHint_playButton_absentWhenItemHasNoAudio() {
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.READING,
                totalQuizCount = 1, remainingQuizCount = 1,
                feedback = AnswerFeedback(isCorrect = true, correctAnswer = "みず"),
                answerReading = "みず",
                answerPitchAccents = PitchAccentUiState.Available(listOf(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0))),
                answerReadingAudio = null
            ),
            displaySettings = DisplaySettings(showAnswerReadingPitchAccent = true),
        )

        composeTestRule.onAllNodesWithContentDescription("Play pronunciation for みず").assertCountEquals(0)
    }

    @Test
    fun quizPhase_subjectTypeLabel_shownWhenSettingEnabled() {
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1,
            ),
            displaySettings = DisplaySettings(showSubjectTypeLabel = true),
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.QUIZ_SUBJECT_TYPE_LABEL).assertIsDisplayed()
        composeTestRule.onNodeWithText("Radical").assertIsDisplayed()
    }

    @Test
    fun quizPhase_subjectTypeLabel_absentWhenSettingDisabled() {
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1,
            ),
            displaySettings = DisplaySettings(showSubjectTypeLabel = false),
        )

        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.QUIZ_SUBJECT_TYPE_LABEL).assertCountEquals(0)
    }

    @Test
    fun quizPhase_continueButton_disabledBrieflyAfterIncorrectAnswer_thenEnables() {
        composeTestRule.mainClock.autoAdvance = false
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1,
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
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1,
                feedback = AnswerFeedback(isCorrect = true, correctAnswer = "Mouth")
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.CONTINUE_BUTTON).assertIsEnabled()
    }

    @Test
    fun noLessonsAvailable_showsMessageAndDoneButton() {
        var done = false
        setScreen(
            LessonUiState(phase = LessonUiState.Phase.NoLessonsAvailable),
            onSessionComplete = { done = true }
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.NO_LESSONS_TEXT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.NO_LESSONS_DONE_BUTTON).performClick()
        assert(done)
    }

    @Test
    fun sessionComplete_showsDoneButtonAndInvokesCallback() {
        var done = false
        setScreen(
            completeState(),
            onSessionComplete = { done = true }
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.SESSION_COMPLETE).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.DONE_BUTTON).performClick()
        assert(done)
    }

    @Test
    fun sessionComplete_showsOverviewCardWithCounts() {
        setScreen(completeState(sessionItemsLearned = 5, sessionItemsCorrectFirstTry = 3))

        composeTestRule.onNodeWithTag(LessonScreenTestTags.SESSION_OVERVIEW_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithText("Items learned: 5").assertIsDisplayed()
        composeTestRule.onNodeWithText("Correct on first try: 3 of 5 (60%)").assertIsDisplayed()
    }

    @Test
    fun sessionComplete_hidesCardsWhenNothingWasLearned() {
        setScreen(completeState(sessionItemsLearned = 0))

        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.SESSION_OVERVIEW_CARD).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.SESSION_TIMING_CARD).assertCountEquals(0)
    }

    @Test
    fun sessionComplete_showsTimingCard() {
        setScreen(
            completeState(
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
            completeState(
                sessionItemsLearned = 1, sessionItemsCorrectFirstTry = 1,
                sessionSlowestAnswers = listOf(
                    SlowAnswer(radicalItem, QuestionType.MEANING, 12_000L, isCorrect = true)
                )
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.SESSION_SLOWEST_CARD).assertIsDisplayed()
    }

    @Test
    fun sessionComplete_hidesSlowestAnswersCard_whenEmpty() {
        setScreen(
            completeState(
                sessionItemsLearned = 1, sessionItemsCorrectFirstTry = 1,
                sessionSlowestAnswers = emptyList()
            )
        )

        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.SESSION_SLOWEST_CARD).assertCountEquals(0)
    }

    @Test
    fun sessionComplete_showsMissedItemsCard_whenPresent() {
        setScreen(
            completeState(
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
            completeState(
                sessionItemsLearned = 1, sessionItemsCorrectFirstTry = 1,
                sessionMissedItems = emptyList()
            )
        )

        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.SESSION_MISSED_CARD).assertCountEquals(0)
    }

    @Test
    fun errorState_showsErrorTextAndRetry() {
        val actions = RecordingLessonActions()
        setScreen(
            LessonUiState(phase = LessonUiState.Phase.Error(message = "Network error")),
            actions = actions
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.ERROR_TEXT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.RETRY_BUTTON).performClick()
        assertThat(actions.calls).contains("load")
    }

    @Test
    fun errorState_studyOfflineButton_invokesCallback() {
        val actions = RecordingLessonActions()
        setScreen(
            LessonUiState(phase = LessonUiState.Phase.Error(message = "Network error")),
            actions = actions
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.STUDY_OFFLINE_BUTTON).performClick()

        assertThat(actions.calls).contains("studyOffline")
    }

    @Test
    fun backButton_invokesCallback() {
        var wentBack = false
        setScreen(
            studyState(studyItems = listOf(radicalItem)),
            onBack = { wentBack = true }
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.BACK_BUTTON).performClick()
        assert(wentBack)
    }

    @Test
    fun overflowMenu_isAbsent_duringSelectPhase() {
        setScreen(
            selectState(availableLessons = listOf(radicalItem), selectedAssignmentIds = setOf(1L))
        )

        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.OVERFLOW_MENU).assertCountEquals(0)
    }

    @Test
    fun overflowMenu_abandonConfirmed_invokesCallback_duringStudyPhase() {
        val actions = RecordingLessonActions()
        setScreen(
            studyState(studyItems = listOf(radicalItem)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.ABANDON_MENU_ITEM).performClick()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.ABANDON_CONFIRM_BUTTON).performClick()
        assertThat(actions.calls).contains("abandonSession")
    }

    @Test
    fun overflowMenu_abandonConfirmed_invokesCallback_duringQuizPhase() {
        val actions = RecordingLessonActions()
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1
            ),
            actions = actions
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.ABANDON_MENU_ITEM).performClick()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.ABANDON_CONFIRM_BUTTON).performClick()
        assertThat(actions.calls).contains("abandonSession")
    }

    @Test
    fun overflowMenu_abandonCancelled_doesNotInvokeCallback() {
        val actions = RecordingLessonActions()
        setScreen(
            studyState(studyItems = listOf(radicalItem)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.ABANDON_MENU_ITEM).performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.ABANDON_CONFIRM_BUTTON).assertCountEquals(0)
        assertThat(actions.calls).doesNotContain("abandonSession")
    }

    // The following eight tests were ported from the instrumented LessonScreenTest when that file
    // was consolidated into this Robolectric suite — they are the cases it covered that this file
    // did not. All are pure state-drives-UI assertions with no device dependency.

    @Test
    fun loadingState_showsLoadingIndicator() {
        setScreen(LessonUiState(phase = LessonUiState.Phase.Loading))

        composeTestRule.onNodeWithTag(LessonScreenTestTags.LOADING_INDICATOR).assertIsDisplayed()
    }

    @Test
    fun studyPhase_nextButton_invokesNextStudyCard() {
        val actions = RecordingLessonActions()
        setScreen(
            studyState(studyItems = listOf(radicalItem, secondRadicalItem), studyIndex = 0),
            actions = actions
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.STUDY_NEXT_BUTTON).performClick()
        assertThat(actions.calls).contains("nextStudyCard")
    }

    @Test
    fun studyPhase_previousButton_invokesCallback() {
        val actions = RecordingLessonActions()
        setScreen(
            studyState(studyItems = listOf(radicalItem, secondRadicalItem), studyIndex = 1),
            actions = actions
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.STUDY_PREVIOUS_BUTTON).performClick()
        assertThat(actions.calls).contains("previousStudyCard")
    }

    @Test
    fun quizPhase_showsCharactersAndAnswerField() {
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.QUIZ_CHARACTERS).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.ANSWER_FIELD).assertIsDisplayed()
    }

    @Test
    fun quizPhase_submitButton_disabledWhenAnswerBlank() {
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1, answerInput = ""
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun quizPhase_submitButton_enabledWhenAnswerNonBlank() {
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1, answerInput = "Mouth"
            )
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.SUBMIT_BUTTON).assertIsEnabled()
    }

    @Test
    fun quizPhase_dontKnowButton_displayedBeforeAnswering_andInvokesCallback() {
        val actions = RecordingLessonActions()
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1
            ),
            actions = actions
        )

        composeTestRule.onNodeWithTag(LessonScreenTestTags.DONT_KNOW_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LessonScreenTestTags.DONT_KNOW_BUTTON).performClick()
        assertThat(actions.calls).contains("dontKnowAnswer")
    }

    @Test
    fun quizPhase_dontKnowButton_hiddenAfterFeedbackIsShown() {
        setScreen(
            quizState(
                currentItem = radicalItem, currentQuestionType = QuestionType.MEANING,
                totalQuizCount = 1, remainingQuizCount = 1,
                feedback = AnswerFeedback(isCorrect = false, correctAnswer = "Mouth")
            )
        )

        composeTestRule.onAllNodesWithTag(LessonScreenTestTags.DONT_KNOW_BUTTON).assertCountEquals(0)
    }
}

/**
 * A [LessonActions] that records what it was asked to do. Assertions read [calls] (in call order) for
 * the parameterless actions and [argumentOf] for the ones carrying a value — so a rendering test can
 * assert "this control is wired to that action" without building a ViewModel and its repository graph.
 */
private class RecordingLessonActions : LessonActions {
    val calls = mutableListOf<String>()
    private val arguments = mutableListOf<Any?>()

    /** The argument passed to the most recent [name] call — mirrors the `var x: T? = null` the
     *  per-callback tests used to capture, where each call overwrote the previous value. */
    fun lastArgumentOf(name: String): Any? = arguments[calls.lastIndexOf(name)]

    private fun record(name: String, argument: Any? = null) {
        calls += name
        arguments += argument
    }

    override fun load() = record("load")
    override fun studyOffline() = record("studyOffline")
    override fun toggleLessonSelection(assignmentId: Long) = record("toggleLessonSelection", assignmentId)
    override fun toggleLessonTypeSelection(type: SubjectType) = record("toggleLessonTypeSelection", type)
    override fun setLessonSort(sort: LessonSort) = record("setLessonSort", sort)
    override fun selectFirst(count: Int) = record("selectFirst", count)
    override fun selectAll() = record("selectAll")
    override fun selectNone() = record("selectNone")
    override fun startSelectedLessons() = record("startSelectedLessons")
    override fun onStudyCardSwiped(index: Int) = record("onStudyCardSwiped", index)
    override fun nextStudyCard() = record("nextStudyCard")
    override fun previousStudyCard() = record("previousStudyCard")
    override fun onAnswerInputChange(value: String) = record("onAnswerInputChange", value)
    override fun submitAnswer() = record("submitAnswer")
    override fun dontKnowAnswer() = record("dontKnowAnswer")
    override fun undoLastAnswer() = record("undoLastAnswer")
    override fun onContinue() = record("onContinue")
    override fun toggleDetails() = record("toggleDetails")
    override fun closeDetails() = record("closeDetails")
    override fun continueSession() = record("continueSession")
    override fun finishForNow() = record("finishForNow")
    override fun abandonSession() = record("abandonSession")
}
