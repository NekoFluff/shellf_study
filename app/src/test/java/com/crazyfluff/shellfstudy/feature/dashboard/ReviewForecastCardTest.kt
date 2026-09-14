package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.crazyfluff.shellfstudy.shared.data.model.ItemSpreadBucket
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecast
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastBucket
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastColorMode
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastWindow
import com.crazyfluff.shellfstudy.shared.network.SubjectType
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
        // Bug regression: the status line above the chart resolves to the same "All caught up" in this
        // state, so the card said it twice, ~10dp apart, in two slightly different wordings. The status
        // line is suppressed here so the empty state is the one message.
        composeTestRule.onNodeWithTag(ReviewForecastTestTags.SUMMARY).assertDoesNotExist()
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

    /** Distinct type counts on "now" vs. every bucket, so the default (nothing tapped) breakdown's
     *  window-wide sum can be told apart from either "now" alone or a single bucket alone. */
    private fun typeBreakdownForecast(): ReviewForecast = ReviewForecast(
        reviewsAvailableNow = 5,
        availableNowCountsByType = mapOf(SubjectType.RADICAL to 3, SubjectType.KANJI to 2),
        buckets = (1..ReviewForecastWindow.DAY.bucketCount).map { index ->
            ReviewForecastBucket(
                hoursFromNow = index,
                availableAt = Clock.System.now(),
                newlyAvailableCount = 2,
                countsByType = mapOf(SubjectType.KANJI to 1, SubjectType.VOCABULARY to 1)
            )
        }
    )

    @Test
    fun breakdownList_defaultShowsSumAcrossWholeWindow() {
        // 24 buckets × (1 Kanji + 1 Vocabulary) plus "now"'s 3 Radical/2 Kanji.
        composeTestRule.setContent {
            ReviewForecastCard(forecast = typeBreakdownForecast(), selectedColorMode = ReviewForecastColorMode.SUBJECT_TYPE)
        }

        composeTestRule.onNodeWithText("Radical: 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kanji: 26").assertIsDisplayed()
        composeTestRule.onNodeWithText("Vocabulary: 24").assertIsDisplayed()
    }

    @Test
    fun breakdownList_tappingNow_showsOnlyAvailableNowCounts_andHidesZeroSegments() {
        composeTestRule.setContent {
            ReviewForecastCard(forecast = typeBreakdownForecast(), selectedColorMode = ReviewForecastColorMode.SUBJECT_TYPE)
        }

        // offset.x = 0 always resolves to bar index 0 ("now"), regardless of bar width math.
        composeTestRule.onNodeWithTag(ReviewForecastTestTags.CHART).performTouchInput {
            down(Offset(0f, height / 2f))
            up()
        }

        composeTestRule.onNodeWithText("Radical: 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kanji: 2").assertIsDisplayed()
        // Vocabulary is 0 in availableNowCountsByType — must not appear once "now" is selected.
        composeTestRule.onNodeWithText("Vocabulary: 0").assertDoesNotExist()
    }

    @Test
    fun breakdownList_tappingABar_showsThatBucketsOwnCounts() {
        composeTestRule.setContent {
            ReviewForecastCard(forecast = typeBreakdownForecast(), selectedColorMode = ReviewForecastColorMode.SUBJECT_TYPE)
        }

        // offset.x at the far right edge always resolves to the last bar index: barWidth is
        // computed from a narrower "bars only" width (total width minus the reserved y-axis label
        // column), so dividing the full node width by that smaller step always overshoots past
        // barCount - 1 and gets coerced back to it.
        composeTestRule.onNodeWithTag(ReviewForecastTestTags.CHART).performTouchInput {
            down(Offset(width - 1f, height / 2f))
            up()
        }

        composeTestRule.onNodeWithText("Kanji: 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Vocabulary: 1").assertIsDisplayed()
        // Radical is 0 in every bucket (only "now" has any) — must not appear once a bucket is selected.
        composeTestRule.onNodeWithText("Radical: 0").assertDoesNotExist()
    }

    @Test
    fun breakdownList_srsStageMode_defaultShowsSumAcrossWholeWindow() {
        val forecast = ReviewForecast(
            reviewsAvailableNow = 2,
            availableNowCountsByNextStage = mapOf(ItemSpreadBucket.BURNED to 2),
            buckets = (1..ReviewForecastWindow.DAY.bucketCount).map { index ->
                ReviewForecastBucket(
                    hoursFromNow = index,
                    availableAt = Clock.System.now(),
                    newlyAvailableCount = 3,
                    countsByNextStage = mapOf(ItemSpreadBucket.GURU to 2, ItemSpreadBucket.MASTER to 1)
                )
            }
        )
        composeTestRule.setContent {
            ReviewForecastCard(forecast = forecast, selectedColorMode = ReviewForecastColorMode.SRS_STAGE)
        }

        composeTestRule.onNodeWithText("Guru: 48").assertIsDisplayed()
        composeTestRule.onNodeWithText("Master: 24").assertIsDisplayed()
        composeTestRule.onNodeWithText("Burned: 2").assertIsDisplayed()
    }
}
