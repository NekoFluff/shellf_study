package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.crazyfluff.shellfstudy.shared.data.model.ItemSpreadBucket
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecast
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastBucket
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastColorMode
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastWindow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Clock
import com.crazyfluff.shellfstudy.shared.feature.dashboard.ReviewForecastCard
import com.crazyfluff.shellfstudy.shared.feature.dashboard.ReviewForecastTestTags

/** Runs under Robolectric (JVM) — state-driven, no device features needed. Pinned per project convention. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReviewForecastCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** Builds a well-formed forecast for [window]: exactly [ReviewForecastWindow.bucketCount]
     *  buckets, each [ReviewForecastWindow.bucketHours] apart, matching what the repository
     *  actually produces for that window. */
    private fun forecastFor(window: ReviewForecastWindow, newlyAvailableCount: Int = 1): ReviewForecast =
        ReviewForecast(
            reviewsAvailableNow = 5,
            buckets = (1..window.bucketCount).map { index ->
                ReviewForecastBucket(index * window.bucketHours, Clock.System.now(), newlyAvailableCount)
            }
        )

    @Test
    fun showsSkeletonChart_whileLoading() {
        composeTestRule.setContent { ReviewForecastCard(forecast = null) }

        composeTestRule.onNodeWithTag(ReviewForecastTestTags.CHART).assertIsDisplayed()
    }

    @Test
    fun showsEmptyState_whenNothingIsDueOrUpcoming() {
        composeTestRule.setContent {
            ReviewForecastCard(forecast = forecastFor(ReviewForecastWindow.DAY, newlyAvailableCount = 0).copy(reviewsAvailableNow = 0))
        }

        composeTestRule.onNodeWithTag(ReviewForecastTestTags.EMPTY_STATE).assertIsDisplayed()
    }

    @Test
    fun showsChart_whenReviewsAreDueOrUpcoming() {
        composeTestRule.setContent {
            ReviewForecastCard(forecast = forecastFor(ReviewForecastWindow.DAY))
        }

        composeTestRule.onNodeWithTag(ReviewForecastTestTags.CHART).assertIsDisplayed()
    }

    @Test
    fun windowDropdown_showsAllOptions_andReportsSelection() {
        var selected: ReviewForecastWindow? = null
        composeTestRule.setContent {
            ReviewForecastCard(
                forecast = forecastFor(ReviewForecastWindow.DAY),
                selectedWindow = ReviewForecastWindow.DAY,
                onWindowChange = { selected = it }
            )
        }

        composeTestRule.onNodeWithText(ReviewForecastWindow.DAY.label).performClick()
        composeTestRule.onNodeWithText(ReviewForecastWindow.FOUR_MONTHS.label).performClick()

        assertThat(selected).isEqualTo(ReviewForecastWindow.FOUR_MONTHS)
    }

    @Test
    fun weekWindow_rendersItsSevenDailyBuckets() {
        // A week's worth of daily buckets, not the day window's hourly ones — the chart must adapt
        // to whatever forecast.buckets.size actually is rather than assuming a fixed bar count.
        composeTestRule.setContent {
            ReviewForecastCard(forecast = forecastFor(ReviewForecastWindow.WEEK), selectedWindow = ReviewForecastWindow.WEEK)
        }

        composeTestRule.onNodeWithTag(ReviewForecastTestTags.CHART).assertIsDisplayed()
    }

    @Test
    fun fourMonthWindow_theLongestOption_stillRendersWithoutCrashing() {
        composeTestRule.setContent {
            ReviewForecastCard(forecast = forecastFor(ReviewForecastWindow.FOUR_MONTHS), selectedWindow = ReviewForecastWindow.FOUR_MONTHS)
        }

        composeTestRule.onNodeWithTag(ReviewForecastTestTags.CHART).assertIsDisplayed()
    }

    @Test
    fun forecastStillOnOldWindowsBucketCount_afterSelectedWindowAdvances_doesNotCrash() {
        // Reproduces the real-world race: DashboardViewModel flips selectedForecastWindow
        // synchronously on tap, but the re-fetched ReviewForecast for the new window arrives an
        // instant later via a separate flow — so there's a frame where this card is asked to render
        // selectedWindow = MONTH (30 buckets) against a forecast that still only has the DAY
        // window's 24 hourly buckets. The chart/axis must key off the forecast's own bucket count,
        // not the newly selected window's, or this indexes past the end of the stale list.
        composeTestRule.setContent {
            ReviewForecastCard(
                forecast = forecastFor(ReviewForecastWindow.DAY),
                selectedWindow = ReviewForecastWindow.MONTH
            )
        }

        composeTestRule.onNodeWithTag(ReviewForecastTestTags.CHART).assertIsDisplayed()
    }

    @Test
    fun colorModeChips_showBothOptions_andReportSelection() {
        var selected: ReviewForecastColorMode? = null
        composeTestRule.setContent {
            ReviewForecastCard(
                forecast = forecastFor(ReviewForecastWindow.DAY),
                selectedColorMode = ReviewForecastColorMode.SUBJECT_TYPE,
                onColorModeChange = { selected = it }
            )
        }

        composeTestRule.onNodeWithText(ReviewForecastColorMode.SRS_STAGE.label).performClick()

        assertThat(selected).isEqualTo(ReviewForecastColorMode.SRS_STAGE)
    }

    @Test
    fun srsStageColorMode_rendersTheChartFromNextStageCounts() {
        // Distinct from the subject-type breakdown: same bucket, but colored by the SRS stage each
        // assignment would advance to on a pass rather than its subject type.
        val forecast = ReviewForecast(
            reviewsAvailableNow = 2,
            buckets = (1..ReviewForecastWindow.DAY.bucketCount).map { index ->
                ReviewForecastBucket(
                    hoursFromNow = index,
                    availableAt = Clock.System.now(),
                    newlyAvailableCount = 3,
                    countsByNextStage = mapOf(ItemSpreadBucket.GURU to 2, ItemSpreadBucket.MASTER to 1)
                )
            },
            availableNowCountsByNextStage = mapOf(ItemSpreadBucket.BURNED to 2)
        )
        composeTestRule.setContent {
            ReviewForecastCard(forecast = forecast, selectedColorMode = ReviewForecastColorMode.SRS_STAGE)
        }

        composeTestRule.onNodeWithTag(ReviewForecastTestTags.CHART).assertIsDisplayed()
    }
}
