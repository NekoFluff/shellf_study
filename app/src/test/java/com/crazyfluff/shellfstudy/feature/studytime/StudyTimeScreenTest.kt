package com.crazyfluff.shellfstudy.feature.studytime

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import com.crazyfluff.shellfstudy.shared.data.studytime.LifetimeEstimate
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyKind
import com.crazyfluff.shellfstudy.shared.data.studytime.StudySegment
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeAggregator
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeWindow
import com.crazyfluff.shellfstudy.shared.feature.studytime.StudyTimeScreen
import com.crazyfluff.shellfstudy.shared.feature.studytime.StudyTimeTestTags
import com.crazyfluff.shellfstudy.shared.feature.studytime.StudyTimeUiState
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Instant

/** Driven purely by state under Robolectric; the SDK is pinned suite-wide in robolectric.properties. */
// A phone-sized window, so the level chart lays out its columns as it would on a phone.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class StudyTimeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val today = LocalDate.parse("2026-09-24")

    private fun segment(startIso: String, minutes: Long, kind: StudyKind, level: Int, items: Int) =
        StudySegment(kind, Instant.parse(startIso).toEpochMilliseconds(), minutes * 60_000L, level, items)

    private fun loaded(
        segments: List<StudySegment>,
        window: StudyTimeWindow = StudyTimeWindow.WEEK,
        selected: Int? = null,
        lifetime: LifetimeEstimate? = null
    ) =
        StudyTimeUiState.Loaded(
            report = StudyTimeAggregator.report(
                segments = segments,
                today = today,
                zone = TimeZone.UTC,
                window = window,
                goalMs = 30 * 60_000L,
                currentLevel = 8
            ).copy(lifetime = lifetime),
            selectedBarIndex = selected
        )

    private val history = LifetimeEstimate(
        twoQuestionReviews = 9_840,
        oneQuestionReviews = 2_460,
        lessons = 1_812,
        reviewMsPerItem = 20_000L,
        lessonMsPerItem = 120_000L
    )

    private val someStudy = listOf(
        segment("2026-09-24T19:00:00Z", 20, StudyKind.REVIEW, level = 8, items = 120),
        segment("2026-09-24T19:30:00Z", 15, StudyKind.LESSON, level = 8, items = 5),
        segment("2026-09-20T08:00:00Z", 25, StudyKind.REVIEW, level = 7, items = 150)
    )

    private fun setContent(
        uiState: StudyTimeUiState,
        onWindowSelect: (StudyTimeWindow) -> Unit = {},
        onBarSelect: (Int?) -> Unit = {},
        onBack: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            StudyTimeScreen(
                uiState = uiState,
                onBack = onBack,
                onWindowSelect = onWindowSelect,
                onBarSelect = onBarSelect
            )
        }
    }

    private fun scrollTo(tag: String) {
        composeTestRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun showsTheEmptyStateBeforeAnythingIsRecorded() {
        setContent(loaded(emptyList()))

        composeTestRule.onNodeWithTag(StudyTimeTestTags.EMPTY_STATE).assertIsDisplayed()
        composeTestRule.onNodeWithText("Your daily goal is 30m. You can change it in Settings.").assertIsDisplayed()
    }

    @Test
    fun showsTodaysTotalGoalStreakAndPeriodStats() {
        setContent(loaded(someStudy))

        composeTestRule.onNodeWithTag(StudyTimeTestTags.TODAY_TOTAL).assertTextEquals("35m")
        composeTestRule.onNodeWithText("Daily goal of 30m met").assertIsDisplayed()
        composeTestRule.onNodeWithTag(StudyTimeTestTags.GOAL_STREAK).assertTextEquals("1-day goal streak")
        scrollTo(StudyTimeTestTags.PERIOD_TOTAL)
        composeTestRule.onNodeWithTag(StudyTimeTestTags.PERIOD_TOTAL).assertTextContains("1h")
        composeTestRule.onNodeWithTag(StudyTimeTestTags.PERIOD_REVIEW_ITEMS).assertTextContains("270")
        composeTestRule.onNodeWithTag(StudyTimeTestTags.PERIOD_LESSON_ITEMS).assertTextContains("5")
    }

    @Test
    fun showsPaceTimePerLevelAndWhenYouStudy() {
        setContent(loaded(someStudy))

        scrollTo(StudyTimeTestTags.PACE_REVIEW)
        // 45 minutes over 270 review items.
        composeTestRule.onNodeWithTag(StudyTimeTestTags.PACE_REVIEW).assertTextContains("10.0s")
        // 15 minutes over 5 lessons.
        composeTestRule.onNodeWithTag(StudyTimeTestTags.PACE_LESSON).assertTextContains("3m")
        scrollTo(StudyTimeTestTags.LEVELS_TOTAL)
        composeTestRule.onNodeWithTag(StudyTimeTestTags.LEVEL_CHART)
            .assertContentDescriptionEquals("Level 7: 25m, Level 8: 35m")
        // Level 8's 35m plus level 7's 25m.
        composeTestRule.onNodeWithTag(StudyTimeTestTags.LEVELS_TOTAL).assertTextEquals("Total recorded time: 1h")
        scrollTo(StudyTimeTestTags.HEATMAP_CAPTION)
        composeTestRule.onNodeWithTag(StudyTimeTestTags.HEATMAP_CAPTION)
            .assertTextEquals("You study most in the evenings, and more on weekends.")
    }

    @Test
    fun showsTheAllTimeEstimateWithReviewsSplitByQuestionCount() {
        setContent(loaded(someStudy, lifetime = history))

        scrollTo(StudyTimeTestTags.LIFETIME_TOTAL)
        // 9,840 × 20s + 2,460 × 10s + 1,812 × 2m.
        composeTestRule.onNodeWithTag(StudyTimeTestTags.LIFETIME_TOTAL).assertTextEquals("≈ 121h 54m")
        composeTestRule.onNodeWithTag(StudyTimeTestTags.LIFETIME_REVIEWS)
            .assertTextContains("9,840 kanji & vocab × 20.0s")
            .assertTextContains("2,460 radical & kana × 10.0s")
        composeTestRule.onNodeWithTag(StudyTimeTestTags.LIFETIME_LESSONS).assertTextContains("1,812 lessons × 2m")
    }

    @Test
    fun showsTheAllTimeEstimateEvenBeforeAnyTimeIsRecorded() {
        setContent(loaded(emptyList(), lifetime = history))

        composeTestRule.onNodeWithTag(StudyTimeTestTags.EMPTY_STATE).assertExists()
        composeTestRule.onNodeWithTag(StudyTimeTestTags.LIFETIME_TOTAL).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun describesTheSelectedBar() {
        setContent(loaded(someStudy, selected = 6))

        scrollTo(StudyTimeTestTags.SELECTED_BAR)
        composeTestRule.onNodeWithTag(StudyTimeTestTags.SELECTED_BAR)
            .assertTextEquals("Thu, Sep 24: 35m (reviews 20m, lessons 15m)")
    }

    @Test
    fun tappingAWindowPillAndTheBackButtonReportTheChoice() {
        var selected: StudyTimeWindow? = null
        var backed = false
        setContent(loaded(someStudy), onWindowSelect = { selected = it }, onBack = { backed = true })

        composeTestRule.onNodeWithText("30 days").performClick()
        assertThat(selected).isEqualTo(StudyTimeWindow.MONTH)
        composeTestRule.onNodeWithTag(StudyTimeTestTags.BACK_BUTTON).performClick()
        assertThat(backed).isTrue()
    }

    @Test
    fun tappingTheChartSelectsABar() {
        var tapped: Int? = -1
        setContent(loaded(someStudy), onBarSelect = { tapped = it })

        scrollTo(StudyTimeTestTags.BAR_CHART)
        composeTestRule.onNodeWithTag(StudyTimeTestTags.BAR_CHART).performClick()
        // A centre tap on a seven-bar week lands on the middle bar.
        assertThat(tapped).isEqualTo(3)
    }
}
