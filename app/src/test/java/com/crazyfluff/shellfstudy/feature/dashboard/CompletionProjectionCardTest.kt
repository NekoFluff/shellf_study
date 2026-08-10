package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.core.data.model.CompletionProjection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CompletionProjectionCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersNothing_beforeFirstLoad() {
        composeTestRule.setContent { CompletionProjectionCard(projection = null) }
    }

    @Test
    fun showsDaysAndDate_whenItemsRemain() {
        composeTestRule.setContent {
            CompletionProjectionCard(
                projection = CompletionProjection(
                    totalItems = 9000, itemsSeen = 1200, dailyPace = 15,
                    daysRemaining = 520, projectedCompletionDate = LocalDate.now().plusDays(520)
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
                    daysRemaining = 0, projectedCompletionDate = LocalDate.now()
                )
            )
        }

        composeTestRule.onNodeWithTag(CompletionProjectionTestTags.SUMMARY_TEXT).assertIsDisplayed()
    }
}
