package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.core.data.model.ItemSpread
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
        composeTestRule.onNodeWithText("Guru: 120").assertIsDisplayed()
        composeTestRule.onNodeWithText("Burned: 200").assertIsDisplayed()
    }
}
