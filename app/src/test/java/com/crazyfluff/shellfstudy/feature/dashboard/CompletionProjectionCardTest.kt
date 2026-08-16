package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.model.CompletionProjection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import com.crazyfluff.shellfstudy.shared.feature.dashboard.CompletionProjectionCard
import com.crazyfluff.shellfstudy.shared.feature.dashboard.CompletionProjectionTestTags
import kotlin.time.Clock

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CompletionProjectionCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsDaysAndDate_whenItemsRemain() {
        composeTestRule.setContent {
            CompletionProjectionCard(
                projection = CompletionProjection(
                    totalItems = 9000, itemsSeen = 1200, dailyPace = 15,
                    daysRemaining = 520,
                    projectedCompletionDate = Clock.System.todayIn(TimeZone.currentSystemDefault()).plus(520, DateTimeUnit.DAY)
                )
            )
        }

        composeTestRule.onNodeWithTag(CompletionProjectionTestTags.SUMMARY_TEXT).assertIsDisplayed()
    }

    @Test
    fun showsCongratulations_whenNothingRemains() {
        composeTestRule.setContent {
            CompletionProjectionCard(
                projection = CompletionProjection(
                    totalItems = 100, itemsSeen = 100, dailyPace = 15,
                    daysRemaining = 0, projectedCompletionDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
                )
            )
        }

        composeTestRule.onNodeWithTag(CompletionProjectionTestTags.SUMMARY_TEXT).assertIsDisplayed()
    }
}
