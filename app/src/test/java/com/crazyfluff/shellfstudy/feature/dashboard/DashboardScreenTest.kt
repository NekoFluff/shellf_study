package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertTextEquals
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyPace
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeOverview
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeSplit
import com.crazyfluff.shellfstudy.shared.feature.studytime.StudyTimeTestTags
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.fakes.dashboardCallbacks
import com.crazyfluff.shellfstudy.shared.feature.dashboard.DashboardFetch
import com.crazyfluff.shellfstudy.shared.feature.dashboard.DashboardScreen
import com.crazyfluff.shellfstudy.shared.feature.dashboard.DashboardScreenTestTags
import com.crazyfluff.shellfstudy.shared.feature.dashboard.DashboardUiState
import com.crazyfluff.shellfstudy.shared.feature.search.SearchOverlayTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs under Robolectric (JVM) — this screen is driven purely by state, no device features needed.
 * Pinned to SDK 35: Robolectric 4.15.1 doesn't yet have shadows for this project's targetSdk (37).
 */
@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsLoadingIndicator_whileLoading_andNothingIsCachedYet() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.InFlight, username = null),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = {})
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.LOADING_INDICATOR).assertIsDisplayed()
    }

    @Test
    fun showsRefreshingBanner_andKeepsContentVisible_whenRefreshingWithCachedContent() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(
                    fetchState = DashboardFetch.InFlight, username = "durtle_fan", level = 12, lessonCount = 5, reviewCount = 23
                ),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = {})
            )
        }

        composeTestRule.onAllNodesWithTag(DashboardScreenTestTags.LOADING_INDICATOR).assertCountEquals(0)
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.REFRESHING_BANNER).assertIsDisplayed()
        composeTestRule.onNodeWithText("Welcome back, durtle_fan!").assertIsDisplayed()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.LESSON_COUNT).assertIsDisplayed()
    }

    @Test
    fun showsOfflineBanner_andKeepsContentVisible_whenOfflineWithCachedContent() {
        var retried = false
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(
                    fetchState = DashboardFetch.Stale,
                    username = "durtle_fan", level = 12, lessonCount = 5, reviewCount = 23
                ),
                callbacks = dashboardCallbacks(onRefresh = { retried = true }, onStartReview = {})
            )
        }

        composeTestRule.onAllNodesWithTag(DashboardScreenTestTags.LOADING_INDICATOR).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(DashboardScreenTestTags.ERROR_TEXT).assertCountEquals(0)
        composeTestRule.onNodeWithText("Welcome back, durtle_fan!").assertIsDisplayed()

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.OFFLINE_BANNER).performClick()
        assert(retried)
    }

    @Test
    fun showsPendingSyncBanner_whenReviewsAreQueuedButOnline() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(
                    fetchState = DashboardFetch.Idle, pendingSyncCount = 3,
                    username = "durtle_fan", level = 12, lessonCount = 5, reviewCount = 23
                ),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = {})
            )
        }

        composeTestRule.onAllNodesWithTag(DashboardScreenTestTags.LOADING_INDICATOR).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(DashboardScreenTestTags.OFFLINE_BANNER).assertCountEquals(0)
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.PENDING_SYNC_BANNER).assertIsDisplayed()
        composeTestRule.onNodeWithText("3 items waiting to sync.").assertIsDisplayed()
    }

    @Test
    fun showsSyncBlockedBanner_insteadOfOfflineOrPendingSync_whenBothApply() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(
                    fetchState = DashboardFetch.Stale, pendingSyncCount = 2, syncBlockedOnAuth = true,
                    username = "durtle_fan", level = 12, lessonCount = 5, reviewCount = 23
                ),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = {})
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.SYNC_BLOCKED_BANNER).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(DashboardScreenTestTags.OFFLINE_BANNER).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(DashboardScreenTestTags.PENDING_SYNC_BANNER).assertCountEquals(0)
    }

    @Test
    fun showsUserInfoAndCounts_whenLoaded() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(
                    fetchState = DashboardFetch.Idle, username = "durtle_fan", level = 12, lessonCount = 5, reviewCount = 23
                ),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = {})
            )
        }

        composeTestRule.onNodeWithText("Welcome back, durtle_fan!").assertIsDisplayed()
        composeTestRule.onNodeWithText("Level 12").assertIsDisplayed()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.LESSON_COUNT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.REVIEW_COUNT).assertIsDisplayed()
    }

    @Test
    fun showsErrorAndRetry_whenErrorPresent() {
        var retried = false
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.Failed("Network error")),
                callbacks = dashboardCallbacks(onRefresh = { retried = true }, onStartReview = {})
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.ERROR_TEXT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.RETRY_BUTTON).performClick()
        assert(retried)
    }

    @Test
    fun overflowMenu_noLongerOffersLogOut() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.Idle, username = "x", level = 1),
                callbacks = dashboardCallbacks()
            )
        }

        // Log out moved to Settings, with the rest of the account.
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.SETTINGS_BUTTON).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Log out").assertCountEquals(0)
    }

    @Test
    fun settingsMenuItem_isNestedUnderOverflowMenu_andInvokesCallback() {
        var openedSettings = false
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.Idle, username = "x", level = 1),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = {}, onOpenSettings = { openedSettings = true })
            )
        }

        composeTestRule.onAllNodesWithText("Settings").assertCountEquals(0)

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.SETTINGS_BUTTON).performClick()
        assert(openedSettings)
    }

    @Test
    fun lessonCard_invokesOnStartLesson_whenTapped() {
        var startedLesson = false
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.Idle, username = "x", level = 1, lessonCount = 5),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = {}, onStartLesson = { startedLesson = true })
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.LESSON_COUNT).performClick()
        assert(startedLesson)
    }

    @Test
    fun reviewCard_invokesOnStartReview_whenTapped() {
        var startedReview = false
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.Idle, username = "x", level = 1, reviewCount = 5),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = { startedReview = true })
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.REVIEW_COUNT).performClick()
        assert(startedReview)
    }

    @Test
    fun lessonCard_doesNotInvokeOnStartLesson_whenNoLessonsAndNoActiveSession() {
        var startedLesson = false
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.Idle, username = "x", level = 1, lessonCount = 0),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = {}, onStartLesson = { startedLesson = true })
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.LESSON_COUNT).performClick()
        assert(!startedLesson)
    }

    @Test
    fun reviewCard_doesNotInvokeOnStartReview_whenNoReviewsAndNoActiveSession() {
        var startedReview = false
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.Idle, username = "x", level = 1, reviewCount = 0),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = { startedReview = true })
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.REVIEW_COUNT).performClick()
        assert(!startedReview)
    }

    @Test
    fun lessonCard_invokesOnStartLesson_whenNoLessonsButSessionActive() {
        var startedLesson = false
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(
                    fetchState = DashboardFetch.Idle, username = "x", level = 1,
                    lessonCount = 0, hasActiveLessonSession = true
                ),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = {}, onStartLesson = { startedLesson = true })
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.LESSON_COUNT).performClick()
        assert(startedLesson)
    }

    @Test
    fun showsLessonsCompletedTodayProgress() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(
                    fetchState = DashboardFetch.Idle, username = "x", level = 1,
                    lessonsCompletedToday = 3, dailyLessonGoal = 15
                ),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = {})
            )
        }

        // The badge sits inside the clickable Lessons card, whose semantics merge descendants
        // together — useUnmergedTree finds the badge's own node instead of the merged card node.
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.LESSONS_TODAY_PROGRESS, useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun showsDaysOnLevel_inlineWithLevelText() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.Idle, username = "x", level = 12, daysOnCurrentLevel = 6),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = {})
            )
        }

        composeTestRule.onNodeWithText("Level 12 · Day 6").assertIsDisplayed()
    }

    @Test
    fun reviewsCard_showsRenamedLabel_whenSessionActive() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.Idle, username = "x", level = 1, hasActiveReviewSession = true),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = {})
            )
        }

        composeTestRule.onNodeWithText("Resume").assertIsDisplayed()
    }

    @Test
    fun lessonsCard_showsRenamedLabel_whenSessionActive() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.Idle, username = "x", level = 1, hasActiveLessonSession = true),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = {})
            )
        }

        composeTestRule.onNodeWithText("Resume").assertIsDisplayed()
    }

    @Test
    fun searchButton_opensInlineSearchOverlay() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.Idle, username = "x", level = 1),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = {})
            )
        }

        // The search field isn't part of the tree until search is activated (AnimatedVisibility).
        composeTestRule.onAllNodesWithText("Search kanji, vocabulary, radicals").assertCountEquals(0)

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.SEARCH_BUTTON).performClick()
        composeTestRule.onNodeWithTag(SearchOverlayTestTags.QUERY_FIELD).assertIsDisplayed()
    }

    @Test
    fun header_hasNoWordmarkTitle() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.Idle, username = "x", level = 1),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = {})
            )
        }

        composeTestRule.onAllNodesWithText("Shellf Study").assertCountEquals(0)
    }

    @Test
    fun abandonReviewMenuItem_isAbsent_whenNoReviewSessionIsActive() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.Idle, username = "x", level = 1),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = {})
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onAllNodesWithText("Abandon review session").assertCountEquals(0)
    }

    @Test
    fun abandonReviewMenuItem_confirming_invokesCallback() {
        var abandoned = false
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.Idle, username = "x", level = 1, hasActiveReviewSession = true),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = {}, onAbandonReviewSession = { abandoned = true })
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.ABANDON_REVIEW_MENU_ITEM).performClick()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.ABANDON_REVIEW_CONFIRM_BUTTON).performClick()
        assert(abandoned)
    }

    @Test
    fun abandonReviewConfirmDialog_cancel_doesNotInvokeCallback() {
        var abandoned = false
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.Idle, username = "x", level = 1, hasActiveReviewSession = true),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = {}, onAbandonReviewSession = { abandoned = true })
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.ABANDON_REVIEW_MENU_ITEM).performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.onAllNodesWithTag(DashboardScreenTestTags.ABANDON_REVIEW_CONFIRM_BUTTON).assertCountEquals(0)
        assert(!abandoned)
    }

    @Test
    fun abandonLessonMenuItem_isAbsent_whenNoLessonSessionIsActive() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.Idle, username = "x", level = 1),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = {})
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onAllNodesWithText("Abandon lesson session").assertCountEquals(0)
    }

    @Test
    fun abandonLessonMenuItem_confirming_invokesCallback() {
        var abandoned = false
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.Idle, username = "x", level = 1, hasActiveLessonSession = true),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = {}, onAbandonLessonSession = { abandoned = true })
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.ABANDON_LESSON_MENU_ITEM).performClick()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.ABANDON_LESSON_CONFIRM_BUTTON).performClick()
        assert(abandoned)
    }

    @Test
    fun lastSessionSummaryMenuItem_isAbsent_whenNoLastSessionSummaryExists() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.Idle, username = "x", level = 1, hasLastSessionSummary = false),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = {})
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onAllNodesWithText("Last session summary").assertCountEquals(0)
    }

    @Test
    fun lastSessionSummaryMenuItem_invokesCallback_whenSummaryExists() {
        var opened = false
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.Idle, username = "x", level = 1, hasLastSessionSummary = true),
                callbacks = dashboardCallbacks(
                    onRefresh = {}, onStartReview = {},
                    onOpenLastSessionSummary = { opened = true }
                )
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.LAST_SESSION_SUMMARY_MENU_ITEM).performClick()
        assert(opened)
    }

    @Test
    fun studyTimeMenuItem_invokesCallback() {
        var opened = false
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.Idle, username = "x", level = 1),
                callbacks = dashboardCallbacks(onOpenStudyTime = { opened = true })
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.STUDY_TIME_MENU_ITEM).performClick()
        assert(opened)
    }

    @Test
    fun studyTimeCard_showsTimeOnTheLevelTodayAgainstTheGoalAndTodaysPlan_andOpensTheScreen() {
        var opened = false
        val overview = StudyTimeOverview(
            today = StudyTimeSplit(lessonMs = 6 * 60_000L, reviewMs = 12 * 60_000L),
            goalMs = 30 * 60_000L,
            goalStreakDays = 0,
            lastSevenDays = emptyList(),
            pace = StudyPace(reviewMsPerItem = 10_000L, lessonMsPerItem = 120_000L),
            hasAnyData = true,
            levelTotalsMs = mapOf(8 to (4 * 60 + 24) * 60_000L)
        )
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(
                    fetchState = DashboardFetch.Idle, username = "x", level = 8, daysOnCurrentLevel = 9,
                    reviewCount = 60, lessonCount = 20, dailyLessonGoal = 15, lessonsCompletedToday = 10,
                    studyTime = overview
                ),
                callbacks = dashboardCallbacks(onOpenStudyTime = { opened = true })
            )
        }

        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(StudyTimeTestTags.DASHBOARD_CARD))
        composeTestRule.onNodeWithTag(StudyTimeTestTags.DASHBOARD_LEVEL_TIME, useUnmergedTree = true)
            .assertTextEquals("4h 24m this level")
        composeTestRule.onNodeWithText("of 30m", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Reviews 12m", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Lessons 6m", useUnmergedTree = true).assertIsDisplayed()
        // 60 reviews at 10s plus the 5 lessons left for the goal at 2m each.
        // The card is clickable, so it merges its children; the line is read from the unmerged tree.
        composeTestRule.onNodeWithTag(StudyTimeTestTags.DASHBOARD_ESTIMATE, useUnmergedTree = true)
            .assertTextEquals("Today's plan: 60 reviews + 5 lessons ≈ 20m at your pace")
        composeTestRule.onNodeWithTag(StudyTimeTestTags.DASHBOARD_CARD).performClick()
        assert(opened)
    }

    @Test
    fun studyTimeCard_isAbsentUntilTheStudyTimeLogHasBeenRead() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.Idle, username = "x", level = 1),
                callbacks = dashboardCallbacks()
            )
        }

        composeTestRule.onAllNodesWithTag(StudyTimeTestTags.DASHBOARD_CARD).assertCountEquals(0)
    }

    @Test
    fun bothAbandonMenuItems_appearTogether_whenBothSessionsAreActive() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(
                    fetchState = DashboardFetch.Idle, username = "x", level = 1,
                    hasActiveReviewSession = true, hasActiveLessonSession = true
                ),
                callbacks = dashboardCallbacks(onRefresh = {}, onStartReview = {})
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.ABANDON_REVIEW_MENU_ITEM).assertIsDisplayed()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.ABANDON_LESSON_MENU_ITEM).assertIsDisplayed()
    }
}
