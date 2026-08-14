package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecast
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastBucket
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Clock

/** Runs under Robolectric (JVM) — state-driven, no device features needed. Pinned per project convention. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReviewForecastCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsSkeletonChart_whileLoading() {
        composeTestRule.setContent { ReviewForecastCard(forecast = null) }

        composeTestRule.onNodeWithTag(ReviewForecastTestTags.CHART).assertIsDisplayed()
    }

    @Test
    fun showsEmptyState_whenNothingIsDueOrUpcoming() {
        composeTestRule.setContent {
            ReviewForecastCard(
                forecast = ReviewForecast(
                    reviewsAvailableNow = 0,
                    buckets = (1..24).map { ReviewForecastBucket(it, Clock.System.now(), newlyAvailableCount = 0) }
                )
            )
        }

        composeTestRule.onNodeWithTag(ReviewForecastTestTags.EMPTY_STATE).assertIsDisplayed()
    }

    @Test
    fun showsChart_whenReviewsAreDueOrUpcoming() {
        composeTestRule.setContent {
            ReviewForecastCard(
                forecast = ReviewForecast(
                    reviewsAvailableNow = 5,
                    buckets = (1..24).map { ReviewForecastBucket(it, Clock.System.now(), newlyAvailableCount = 1) }
                )
            )
        }

        composeTestRule.onNodeWithTag(ReviewForecastTestTags.CHART).assertIsDisplayed()
    }
}
