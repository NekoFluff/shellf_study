package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.core.data.model.ReviewsCompletedStats
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReviewsCompletedCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsDashPlaceholder_whileLoading() {
        composeTestRule.setContent { ReviewsCompletedCard(stats = null) }

        composeTestRule.onNodeWithTag(ReviewsCompletedTestTags.TODAY).assertIsDisplayed()
        composeTestRule.onNodeWithText("—").assertIsDisplayed()
    }

    @Test
    fun showsGenuineZero_distinctFromUnloaded() {
        composeTestRule.setContent {
            ReviewsCompletedCard(stats = ReviewsCompletedStats(today = 0, last7Days = 0, allTime = 0))
        }

        composeTestRule.onNodeWithTag(ReviewsCompletedTestTags.TODAY).assertIsDisplayed()
        composeTestRule.onNodeWithText("0").assertIsDisplayed()
    }

    @Test
    fun showsTodaysCount() {
        composeTestRule.setContent {
            ReviewsCompletedCard(stats = ReviewsCompletedStats(today = 12, last7Days = 84, allTime = 1502))
        }

        composeTestRule.onNodeWithText("12").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reviews completed today").assertIsDisplayed()
    }
}
