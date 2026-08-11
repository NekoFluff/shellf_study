package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.core.data.model.LevelItem
import com.crazyfluff.shellfstudy.core.data.model.LevelProgress
import com.crazyfluff.shellfstudy.core.data.model.SubjectTypeProgress
import com.crazyfluff.shellfstudy.core.network.SubjectType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class LevelProgressCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleProgress = LevelProgress(
        level = 12,
        breakdown = listOf(
            SubjectTypeProgress(SubjectType.RADICAL, items = levelItems(SubjectType.RADICAL, total = 5, passed = 5)),
            SubjectTypeProgress(SubjectType.KANJI, items = levelItems(SubjectType.KANJI, total = 25, passed = 18)),
            SubjectTypeProgress(SubjectType.VOCABULARY, items = levelItems(SubjectType.VOCABULARY, total = 90, passed = 40))
        )
    )

    private fun levelItems(subjectType: SubjectType, total: Int, passed: Int): List<LevelItem> =
        (1..total).map { index ->
            LevelItem(
                subjectId = subjectType.ordinal * 1000L + index,
                subjectType = subjectType,
                display = "${subjectType.name}$index",
                passed = index <= passed
            )
        }

    @Test
    fun rendersNothing_beforeFirstLoad() {
        composeTestRule.setContent { LevelProgressCard(progress = null) }

        // Card is entirely gated behind a non-null progress — nothing to assert is displayed.
    }

    @Test
    fun showsPerTypeBreakdown_whenLoaded() {
        composeTestRule.setContent { LevelProgressCard(progress = sampleProgress) }

        composeTestRule.onNodeWithTag(LevelProgressTestTags.CARD).assertIsDisplayed()
        composeTestRule.onNodeWithText("Level 12 Progress").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kanji: 18 / 25").assertIsDisplayed()
    }

    @Test
    fun tappingExpandToggle_revealsItemChipsInsteadOfRemainingCount() {
        composeTestRule.setContent { LevelProgressCard(progress = sampleProgress) }

        composeTestRule.onAllNodesWithTag(LevelProgressTestTags.DETAIL_PREFIX + "KANJI").assertCountEquals(0)

        composeTestRule.onNodeWithTag(LevelProgressTestTags.EXPAND_TOGGLE_BUTTON).performClick()

        composeTestRule.onNodeWithTag(LevelProgressTestTags.DETAIL_PREFIX + "KANJI").assertIsDisplayed()
        // A passed item and a not-yet-passed item both render as their own chip.
        composeTestRule.onNodeWithText("KANJI1").assertIsDisplayed()
        composeTestRule.onNodeWithText("KANJI25").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("remaining").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("%", substring = true).assertCountEquals(0)
    }

    @Test
    fun tappingItemChip_invokesOnSubjectClickWithItsSubjectId() {
        var clickedSubjectId: Long? = null
        composeTestRule.setContent {
            LevelProgressCard(progress = sampleProgress, onSubjectClick = { clickedSubjectId = it })
        }

        composeTestRule.onNodeWithTag(LevelProgressTestTags.EXPAND_TOGGLE_BUTTON).performClick()
        composeTestRule.onNodeWithText("KANJI1").performClick()

        assert(clickedSubjectId == SubjectType.KANJI.ordinal * 1000L + 1)
    }

    @Test
    fun nextLevelButton_invokesOnLevelChangeWithLevelPlusOne() {
        var requestedLevel: Int? = null
        composeTestRule.setContent {
            LevelProgressCard(progress = sampleProgress, maxLevel = 20, onLevelChange = { requestedLevel = it })
        }

        composeTestRule.onNodeWithTag(LevelProgressTestTags.NEXT_LEVEL_BUTTON).performClick()

        assert(requestedLevel == 13)
    }

    @Test
    fun previousLevelButton_invokesOnLevelChangeWithLevelMinusOne() {
        var requestedLevel: Int? = null
        composeTestRule.setContent {
            LevelProgressCard(progress = sampleProgress, onLevelChange = { requestedLevel = it })
        }

        composeTestRule.onNodeWithTag(LevelProgressTestTags.PREV_LEVEL_BUTTON).performClick()

        assert(requestedLevel == 11)
    }

    @Test
    fun nextLevelButton_disabledAtMaxLevel() {
        composeTestRule.setContent {
            LevelProgressCard(progress = sampleProgress, maxLevel = 12, onLevelChange = {})
        }

        composeTestRule.onNodeWithTag(LevelProgressTestTags.NEXT_LEVEL_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun previousLevelButton_disabledAtLevelOne() {
        composeTestRule.setContent {
            LevelProgressCard(progress = sampleProgress.copy(level = 1), onLevelChange = {})
        }

        composeTestRule.onNodeWithTag(LevelProgressTestTags.PREV_LEVEL_BUTTON).assertIsNotEnabled()
    }
}
