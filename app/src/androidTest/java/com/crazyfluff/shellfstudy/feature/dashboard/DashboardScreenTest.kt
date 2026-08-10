package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.crazyfluff.shellfstudy.feature.search.SearchOverlayTestTags
import org.junit.Rule
import org.junit.Test

class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsLoadingIndicator_whileLoading() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(isLoading = true),
                onRefresh = {}, onStartReview = {}, onLogOut = {}
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.LOADING_INDICATOR).assertIsDisplayed()
    }

    @Test
    fun showsUserInfoAndCounts_whenLoaded() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(
                    isLoading = false, username = "durtle_fan", level = 12, lessonCount = 5, reviewCount = 23
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
                uiState = DashboardUiState(isLoading = false, errorMessage = "Network error"),
                onRefresh = { retried = true }, onStartReview = {}, onLogOut = {}
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.ERROR_TEXT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.RETRY_BUTTON).performClick()
        assert(retried)
    }

    @Test
    fun startReviewButton_disabledWhenNoReviewsDue() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(isLoading = false, username = "x", level = 1, reviewCount = 0),
                onRefresh = {}, onStartReview = {}, onLogOut = {}
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.START_REVIEW_BUTTON).assertIsDisplayed()
    }

    @Test
    fun startReviewButton_invokesCallback_whenReviewsDue() {
        var started = false
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(isLoading = false, username = "x", level = 1, reviewCount = 3),
                onRefresh = {}, onStartReview = { started = true }, onLogOut = {}
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.START_REVIEW_BUTTON).performClick()
        assert(started)
    }

    @Test
    fun logOutMenuItem_isNestedUnderOverflowMenu_andInvokesCallback() {
        var loggedOut = false
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(isLoading = false, username = "x", level = 1),
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
                uiState = DashboardUiState(isLoading = false, username = "x", level = 1),
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
                uiState = DashboardUiState(isLoading = false, username = "x", level = 1, lessonCount = 5),
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
                uiState = DashboardUiState(isLoading = false, username = "x", level = 1, reviewCount = 5),
                onRefresh = {}, onStartReview = { startedReview = true }, onLogOut = {}
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.REVIEW_COUNT).performClick()
        assert(startedReview)
    }

    @Test
    fun showsLessonsCompletedTodayProgress() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(
                    isLoading = false, username = "x", level = 1,
                    lessonsCompletedToday = 3, dailyLessonGoal = 15
                ),
                onRefresh = {}, onStartReview = {}, onLogOut = {}
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.LESSONS_TODAY_PROGRESS).assertIsDisplayed()
        composeTestRule.onNodeWithText("3 / 15 lessons today").assertIsDisplayed()
    }

    @Test
    fun resumeSessionButton_showsRenamedText_whenSessionActive() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(isLoading = false, username = "x", level = 1, hasActiveReviewSession = true),
                onRefresh = {}, onStartReview = {}, onLogOut = {}
            )
        }

        composeTestRule.onNodeWithText("Resume Session").assertIsDisplayed()
    }

    @Test
    fun searchButton_opensInlineSearchOverlay() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(isLoading = false, username = "x", level = 1),
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
                uiState = DashboardUiState(isLoading = false, username = "x", level = 1),
                onRefresh = {}, onStartReview = {}, onLogOut = {}
            )
        }

        composeTestRule.onAllNodesWithText("Shellf Study").assertCountEquals(0)
    }
}
