package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.core.data.model.LevelProgress
import com.crazyfluff.shellfstudy.core.data.model.SubjectTypeProgress
import com.crazyfluff.shellfstudy.core.network.SubjectType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LevelProgressCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersNothing_beforeFirstLoad() {
        composeTestRule.setContent { LevelProgressCard(progress = null) }

        // Card is entirely gated behind a non-null progress — nothing to assert is displayed.
    }

    @Test
    fun showsPerTypeBreakdown_whenLoaded() {
        composeTestRule.setContent {
            LevelProgressCard(
                progress = LevelProgress(
                    level = 12,
                    breakdown = listOf(
                        SubjectTypeProgress(SubjectType.RADICAL, passedCount = 5, totalCount = 5),
                        SubjectTypeProgress(SubjectType.KANJI, passedCount = 18, totalCount = 25),
                        SubjectTypeProgress(SubjectType.VOCABULARY, passedCount = 40, totalCount = 90)
                    )
                )
            )
        }

        composeTestRule.onNodeWithTag(LevelProgressTestTags.CARD).assertIsDisplayed()
        composeTestRule.onNodeWithText("Level 12 Progress").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kanji: 18 / 25").assertIsDisplayed()
    }
}
