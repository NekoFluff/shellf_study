package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeBucket
import kotlinx.datetime.LocalDate
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollTo
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
import com.crazyfluff.shellfstudy.shared.feature.dashboard.LeaderboardCardTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.friends.FriendDetailsTestTags
import com.crazyfluff.shellfstudy.shared.data.model.SrsCounts
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardWindow
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardMetric
import com.crazyfluff.shellfstudy.shared.data.model.Leaderboard
import com.crazyfluff.shellfstudy.shared.data.model.FriendStats
import com.crazyfluff.shellfstudy.shared.data.model.ActivityStats

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
    fun muteAudioMenuItem_followsMutedState_andInvokesCallback() {
        var toggled = false
        var muted by mutableStateOf(false)
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(
                    fetchState = DashboardFetch.Idle, username = "x", level = 1, isAudioMuted = muted
                ),
                callbacks = dashboardCallbacks(onToggleAudioMuted = { toggled = true })
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.MUTE_AUDIO_MENU_ITEM)
            .assertTextEquals("Mute audio")
            .performClick()
        assert(toggled)

        muted = true
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.MUTE_AUDIO_MENU_ITEM).assertTextEquals("Unmute audio")
    }

    @Test
    fun settingsMenuItem_isNestedUnderOverflowMenu_andInvokesCallback() {
        var openedSettings = false
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(fetchState = DashboardFetch.Idle, username = "x", level = 1),
                callbacks = dashboardCallbacks(
                    onRefresh = {},
                    onStartReview = {},
                    onOpenSettings = { openedSettings = true }
                )
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
                callbacks = dashboardCallbacks(
                    onRefresh = {},
                    onStartReview = {},
                    onStartLesson = { startedLesson = true }
                )
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
                callbacks = dashboardCallbacks(
                    onRefresh = {},
                    onStartReview = {},
                    onStartLesson = { startedLesson = true }
                )
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
                callbacks = dashboardCallbacks(
                    onRefresh = {},
                    onStartReview = {},
                    onStartLesson = { startedLesson = true }
                )
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
                callbacks = dashboardCallbacks(
                    onRefresh = {},
                    onStartReview = {},
                    onAbandonReviewSession = { abandoned = true }
                )
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
                callbacks = dashboardCallbacks(
                    onRefresh = {},
                    onStartReview = {},
                    onAbandonReviewSession = { abandoned = true }
                )
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
                callbacks = dashboardCallbacks(
                    onRefresh = {},
                    onStartReview = {},
                    onAbandonLessonSession = { abandoned = true }
                )
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
    fun studyTimeCard_showsTodayAgainstTheGoal_andOpensTheScreen() {
        var opened = false
        val overview = StudyTimeOverview(
            today = StudyTimeSplit(lessonMs = 6 * 60_000L, reviewMs = 12 * 60_000L),
            goalMs = 30 * 60_000L,
            lastSevenDays = emptyList(),
            pace = StudyPace(reviewMsPerItem = 10_000L, lessonMsPerItem = 120_000L),
            hasAnyData = true
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
        composeTestRule.onNodeWithTag(StudyTimeTestTags.DASHBOARD_TODAY_TOTAL, useUnmergedTree = true)
            .assertTextEquals("18m")
        composeTestRule.onNodeWithText("of 30m today", useUnmergedTree = true).assertIsDisplayed()
        // The daily plan estimate is gone from the card.
        composeTestRule.onNodeWithText("Today's plan", substring = true, useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag(StudyTimeTestTags.DASHBOARD_CARD).performClick()
        assert(opened)
    }

    @Test
    fun studyTimeCard_showsTheFlameWithTheDaysStudied_andHidesItAtZero() {
        var streak by mutableStateOf(0)
        composeTestRule.setContent {
            DashboardScreen(
                uiState = studyTimeDashboard(studyTimeOverview(), studyStreakDays = streak),
                callbacks = dashboardCallbacks()
            )
        }

        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(StudyTimeTestTags.DASHBOARD_CARD))
        composeTestRule.onAllNodesWithTag(StudyTimeTestTags.DASHBOARD_STREAK, useUnmergedTree = true)
            .assertCountEquals(0)
        streak = 4
        composeTestRule.onNodeWithTag(StudyTimeTestTags.DASHBOARD_STREAK, useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("4", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun studyTimeCard_tapOnTheWeekChartStillOpensTheScreen() {
        var opened = false
        composeTestRule.setContent {
            DashboardScreen(
                uiState = studyTimeDashboard(studyTimeOverview()),
                callbacks = dashboardCallbacks(onOpenStudyTime = { opened = true })
            )
        }

        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(StudyTimeTestTags.DASHBOARD_CARD))
        composeTestRule.onNodeWithTag(StudyTimeTestTags.BAR_CHART, useUnmergedTree = true)
            .performTouchInput { click(center) }
        assert(opened)
    }

    @Test
    fun studyTimeCard_beforeAnySession_invitesOneAndHasNoWeekChart() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = studyTimeDashboard(
                    studyTimeOverview(today = StudyTimeSplit.ZERO, days = emptyList(), hasAnyData = false)
                ),
                callbacks = dashboardCallbacks()
            )
        }

        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(StudyTimeTestTags.DASHBOARD_CARD))
        composeTestRule.onNodeWithText("Starts with your next session", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(StudyTimeTestTags.BAR_CHART, useUnmergedTree = true).assertCountEquals(0)
    }

    private fun studyTimeOverview(
        today: StudyTimeSplit = StudyTimeSplit(lessonMs = 4 * 60_000L, reviewMs = 17 * 60_000L),
        days: List<StudyTimeBucket> = (0 until 7).map { offset ->
            StudyTimeBucket(LocalDate(2026, 9, 24 + offset), StudyTimeSplit(reviewMs = (offset + 1) * 5 * 60_000L))
        },
        hasAnyData: Boolean = true
    ) = StudyTimeOverview(
        today = today,
        goalMs = 30 * 60_000L,
        lastSevenDays = days,
        pace = StudyPace(),
        hasAnyData = hasAnyData
    )

    private fun studyTimeDashboard(overview: StudyTimeOverview, studyStreakDays: Int = 0) = DashboardUiState(
        fetchState = DashboardFetch.Idle, username = "x", level = 8, studyTime = overview,
        studyStreakDays = studyStreakDays
    )

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

    private fun leaderboardPerson(nickname: String, isSelf: Boolean, rosterIndex: Int) = FriendStats(
        friendEntryId = if (isSelf) "" else nickname.lowercase(),
        nickname = nickname,
        username = if (isSelf) "" else nickname.lowercase() + "_wk",
        level = 9,
        reviewAccuracy = 0.8f,
        avgDaysPerLevel = null,
        daysSinceStart = null,
        levelTimeline = emptyList(),
        isCurrentUser = isSelf,
        rosterIndex = rosterIndex,
        learned = ActivityStats(week = if (isSelf) 10 else 20),
        srsCounts = SrsCounts(guru = if (isSelf) 3 else 5),
        fetchedAtMillis = if (isSelf) null else System.currentTimeMillis()
    )

    private fun setLeaderboardContent() {
        val board = Leaderboard(
            entries = listOf(
                leaderboardPerson("Mei", isSelf = false, rosterIndex = 1),
                leaderboardPerson("You", isSelf = true, rosterIndex = 0)
            ),
            metric = LeaderboardMetric.LEARNED,
            window = LeaderboardWindow.WEEK
        )
        composeTestRule.setContent {
            DashboardScreen(
                uiState = DashboardUiState(
                    fetchState = DashboardFetch.Idle,
                    username = "x",
                    level = 9,
                    leaderboard = board
                ),
                callbacks = dashboardCallbacks()
            )
        }
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(LeaderboardCardTestTags.CARD))
    }

    @Test
    fun leaderboardRow_opensTheFriendsDetailsReadOnly() {
        setLeaderboardContent()

        composeTestRule.onAllNodesWithTag(LeaderboardCardTestTags.ROW)[0].performClick()

        composeTestRule.onNodeWithTag(FriendDetailsTestTags.SHEET).assertIsDisplayed()
        // No level timeline in this fixture, so no "for N days".
        composeTestRule.onNodeWithText("Level 9").assertIsDisplayed()
        composeTestRule.onNodeWithText("mei_wk", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag(FriendDetailsTestTags.SRS_ROW).performScrollTo().assertIsDisplayed()
        // Renaming and removing belong to the Friends page.
        composeTestRule.onNodeWithTag(FriendDetailsTestTags.RENAME_BUTTON).assertDoesNotExist()
        composeTestRule.onNodeWithTag(FriendDetailsTestTags.REMOVE_BUTTON).assertDoesNotExist()
    }

    @Test
    fun yourOwnLeaderboardRow_showsYourSrsBreakdown_withoutAFetchTime() {
        setLeaderboardContent()

        composeTestRule.onAllNodesWithTag(LeaderboardCardTestTags.ROW)[1].performClick()

        composeTestRule.onNodeWithTag(FriendDetailsTestTags.SHEET).assertIsDisplayed()
        composeTestRule.onNodeWithTag(FriendDetailsTestTags.SRS_ROW).performScrollTo().assertIsDisplayed()
        // Your figures are always live, so there's no "Updated …" (and no username) line.
        composeTestRule.onNodeWithTag(FriendDetailsTestTags.UPDATED).assertDoesNotExist()
    }
}
