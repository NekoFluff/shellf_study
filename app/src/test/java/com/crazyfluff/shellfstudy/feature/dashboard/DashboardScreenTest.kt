package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.feature.search.SearchOverlayTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Runs under Robolectric (JVM) — this screen is driven purely by state, no device features needed.
 * Pinned to SDK 35: Robolectric 4.15.1 doesn't yet have shadows for this project's targetSdk (37).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsLoadingIndicator_whileLoading_andNothingIsCachedYet() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(isRefreshing = true, username = null),
                onRefresh = {}, onStartReview = {}, onLogOut = {}
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.LOADING_INDICATOR).assertIsDisplayed()
    }

    @Test
    fun showsRefreshingBanner_andKeepsContentVisible_whenRefreshingWithCachedContent() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(
                    isRefreshing = true, username = "durtle_fan", level = 12, lessonCount = 5, reviewCount = 23
                ),
                onRefresh = {}, onStartReview = {}, onLogOut = {}
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
                    isRefreshing = false, isOffline = true,
                    username = "durtle_fan", level = 12, lessonCount = 5, reviewCount = 23
                ),
                onRefresh = { retried = true }, onStartReview = {}, onLogOut = {}
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
                    isRefreshing = false, pendingSyncCount = 3,
                    username = "durtle_fan", level = 12, lessonCount = 5, reviewCount = 23
                ),
                onRefresh = {}, onStartReview = {}, onLogOut = {}
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
                    isRefreshing = false, isOffline = true, pendingSyncCount = 2, syncBlockedOnAuth = true,
                    username = "durtle_fan", level = 12, lessonCount = 5, reviewCount = 23
                ),
                onRefresh = {}, onStartReview = {}, onLogOut = {}
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
                    isRefreshing = false, username = "durtle_fan", level = 12, lessonCount = 5, reviewCount = 23
                ),
                onRefresh = {}, onStartReview = {}, onLogOut = {}
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
                uiState = DashboardUiState(isRefreshing = false, errorMessage = "Network error"),
                onRefresh = { retried = true }, onStartReview = {}, onLogOut = {}
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.ERROR_TEXT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.RETRY_BUTTON).performClick()
        assert(retried)
    }

    @Test
    fun logOutMenuItem_isNestedUnderOverflowMenu_andInvokesCallback() {
        var loggedOut = false
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(isRefreshing = false, username = "x", level = 1),
                onRefresh = {}, onStartReview = {}, onLogOut = { loggedOut = true }
            )
        }

        // Not visible until the overflow menu is opened.
        composeTestRule.onAllNodesWithText("Log out").assertCountEquals(0)

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.LOG_OUT_BUTTON).performClick()
        assert(loggedOut)
    }

    @Test
    fun settingsMenuItem_isNestedUnderOverflowMenu_andInvokesCallback() {
        var openedSettings = false
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(isRefreshing = false, username = "x", level = 1),
                onRefresh = {}, onStartReview = {}, onLogOut = {}, onOpenSettings = { openedSettings = true }
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
                uiState = DashboardUiState(isRefreshing = false, username = "x", level = 1, lessonCount = 5),
                onRefresh = {}, onStartReview = {}, onLogOut = {}, onStartLesson = { startedLesson = true }
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
                uiState = DashboardUiState(isRefreshing = false, username = "x", level = 1, reviewCount = 5),
                onRefresh = {}, onStartReview = { startedReview = true }, onLogOut = {}
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
                uiState = DashboardUiState(isRefreshing = false, username = "x", level = 1, lessonCount = 0),
                onRefresh = {}, onStartReview = {}, onLogOut = {}, onStartLesson = { startedLesson = true }
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
                uiState = DashboardUiState(isRefreshing = false, username = "x", level = 1, reviewCount = 0),
                onRefresh = {}, onStartReview = { startedReview = true }, onLogOut = {}
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
                    isRefreshing = false, username = "x", level = 1,
                    lessonCount = 0, hasActiveLessonSession = true
                ),
                onRefresh = {}, onStartReview = {}, onLogOut = {}, onStartLesson = { startedLesson = true }
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
                    isRefreshing = false, username = "x", level = 1,
                    lessonsCompletedToday = 3, dailyLessonGoal = 15
                ),
                onRefresh = {}, onStartReview = {}, onLogOut = {}
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
                uiState = DashboardUiState(isRefreshing = false, username = "x", level = 12, daysOnCurrentLevel = 6),
                onRefresh = {}, onStartReview = {}, onLogOut = {}
            )
        }

        composeTestRule.onNodeWithText("Level 12 · Day 6").assertIsDisplayed()
    }

    @Test
    fun reviewsCard_showsRenamedLabel_whenSessionActive() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(isRefreshing = false, username = "x", level = 1, hasActiveReviewSession = true),
                onRefresh = {}, onStartReview = {}, onLogOut = {}
            )
        }

        composeTestRule.onNodeWithText("Resume").assertIsDisplayed()
    }

    @Test
    fun lessonsCard_showsRenamedLabel_whenSessionActive() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(isRefreshing = false, username = "x", level = 1, hasActiveLessonSession = true),
                onRefresh = {}, onStartReview = {}, onLogOut = {}
            )
        }

        composeTestRule.onNodeWithText("Resume").assertIsDisplayed()
    }

    @Test
    fun searchButton_opensInlineSearchOverlay() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(isRefreshing = false, username = "x", level = 1),
                onRefresh = {}, onStartReview = {}, onLogOut = {}
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
                uiState = DashboardUiState(isRefreshing = false, username = "x", level = 1),
                onRefresh = {}, onStartReview = {}, onLogOut = {}
            )
        }

        composeTestRule.onAllNodesWithText("Shellf Study").assertCountEquals(0)
    }

    @Test
    fun abandonReviewMenuItem_isAbsent_whenNoReviewSessionIsActive() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(isRefreshing = false, username = "x", level = 1),
                onRefresh = {}, onStartReview = {}, onLogOut = {}
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
                uiState = DashboardUiState(isRefreshing = false, username = "x", level = 1, hasActiveReviewSession = true),
                onRefresh = {}, onStartReview = {}, onLogOut = {}, onAbandonReviewSession = { abandoned = true }
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
                uiState = DashboardUiState(isRefreshing = false, username = "x", level = 1, hasActiveReviewSession = true),
                onRefresh = {}, onStartReview = {}, onLogOut = {}, onAbandonReviewSession = { abandoned = true }
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
                uiState = DashboardUiState(isRefreshing = false, username = "x", level = 1),
                onRefresh = {}, onStartReview = {}, onLogOut = {}
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
                uiState = DashboardUiState(isRefreshing = false, username = "x", level = 1, hasActiveLessonSession = true),
                onRefresh = {}, onStartReview = {}, onLogOut = {}, onAbandonLessonSession = { abandoned = true }
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.ABANDON_LESSON_MENU_ITEM).performClick()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.ABANDON_LESSON_CONFIRM_BUTTON).performClick()
        assert(abandoned)
    }

    @Test
    fun bothAbandonMenuItems_appearTogether_whenBothSessionsAreActive() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(
                    isRefreshing = false, username = "x", level = 1,
                    hasActiveReviewSession = true, hasActiveLessonSession = true
                ),
                onRefresh = {}, onStartReview = {}, onLogOut = {}
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.ABANDON_REVIEW_MENU_ITEM).assertIsDisplayed()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.ABANDON_LESSON_MENU_ITEM).assertIsDisplayed()
    }
}
