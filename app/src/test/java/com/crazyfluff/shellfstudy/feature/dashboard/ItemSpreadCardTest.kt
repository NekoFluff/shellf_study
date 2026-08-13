package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.core.data.model.ItemSpread
import com.crazyfluff.shellfstudy.core.data.model.ItemSpreadBucket
import com.crazyfluff.shellfstudy.core.network.SubjectType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ItemSpreadCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsEmptyState_forBrandNewAccount() {
        composeTestRule.setContent {
            ItemSpreadCard(spread = ItemSpread(0, 0, 0, 0, 0, 0))
        }

        composeTestRule.onNodeWithTag(ItemSpreadTestTags.EMPTY_STATE).assertIsDisplayed()
    }

    @Test
    fun showsBarAndCounts_whenItemsExist() {
        composeTestRule.setContent {
            ItemSpreadCard(
                spread = ItemSpread(
                    lockedCount = 500, apprenticeCount = 80, guruCount = 120,
                    masterCount = 40, enlightenedCount = 30, burnedCount = 200
                )
            )
        }

        composeTestRule.onNodeWithTag(ItemSpreadTestTags.BAR).assertIsDisplayed()
        composeTestRule.onNodeWithText("Guru: 120 (12%)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Burned: 200 (20%)").assertIsDisplayed()
    }

    @Test
    fun showsTypeBreakdown_whenCountsByTypePresent() {
        composeTestRule.setContent {
            ItemSpreadCard(
                spread = ItemSpread(
                    lockedCount = 500, apprenticeCount = 80, guruCount = 120,
                    masterCount = 40, enlightenedCount = 30, burnedCount = 200,
                    countsByType = mapOf(
                        ItemSpreadBucket.GURU to mapOf(
                            SubjectType.RADICAL to 20,
                            SubjectType.KANJI to 40,
                            SubjectType.VOCABULARY to 60
                        )
                    )
                )
            )
        }

        composeTestRule.onNodeWithTag(ItemSpreadTestTags.typeBar(ItemSpreadBucket.GURU)).assertIsDisplayed()
    }
}
