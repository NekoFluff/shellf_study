package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.fakes.dashboardCallbacks
import com.crazyfluff.shellfstudy.shared.data.historysync.HistorySyncStatus
import com.crazyfluff.shellfstudy.shared.feature.dashboard.DashboardFetch
import com.crazyfluff.shellfstudy.shared.feature.dashboard.DashboardScreen
import com.crazyfluff.shellfstudy.shared.feature.dashboard.DashboardScreenTestTags
import com.crazyfluff.shellfstudy.shared.feature.dashboard.DashboardUiState
import com.crazyfluff.shellfstudy.shared.feature.dashboard.DeviceSyncState
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The dashboard's sync reporting: the progress line, the overflow menu's Sync status, and its sheet. */
@RunWith(AndroidJUnit4::class)
class DashboardSyncStatusTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsAProgressLine_whileTheDeviceSyncRuns() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = idleState(deviceSync = DeviceSyncState(true, true, HistorySyncStatus(isSyncing = true))),
                callbacks = dashboardCallbacks()
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.SYNC_PROGRESS_LINE).assertIsDisplayed()
    }

    @Test
    fun hidesTheProgressLine_whenNothingIsSyncing() {
        composeTestRule.setContent {
            DashboardScreen(uiState = idleState(), callbacks = dashboardCallbacks())
        }

        composeTestRule.onAllNodesWithTag(DashboardScreenTestTags.SYNC_PROGRESS_LINE).assertCountEquals(0)
    }

    @Test
    fun theSheetSaysWhereEachKindOfSyncStands() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = idleState(lastSyncedAtMillis = System.currentTimeMillis() - 5 * 60_000),
                callbacks = dashboardCallbacks()
            )
        }

        openSyncSheet()

        composeTestRule.onNodeWithText("Updated 5 minutes ago").assertIsDisplayed()
        composeTestRule.onNodeWithText("All sent to WaniKani").assertIsDisplayed()
        // No backend, no cross-device row.
        composeTestRule.onAllNodesWithTag(DashboardScreenTestTags.SYNC_SHEET_DEVICES_ROW).assertCountEquals(0)
    }

    @Test
    fun overflowMenu_saysWhenWaniKaniLastSynced_andOpensTheSheet() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = idleState(lastSyncedAtMillis = System.currentTimeMillis() - 5 * 60_000),
                callbacks = dashboardCallbacks()
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onNodeWithText("Synced 5 minutes ago").assertIsDisplayed()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.SYNC_STATUS_MENU_ITEM).performClick()

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.SYNC_SHEET).assertIsDisplayed()
    }

    @Test
    fun overflowMenu_flagsAProblem_whenTheDeviceSyncFailed() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = idleState(deviceSync = DeviceSyncState(true, true, HistorySyncStatus(lastPassFailed = true))),
                callbacks = dashboardCallbacks()
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.OVERFLOW_MENU).performClick()

        composeTestRule.onNodeWithText("Sync issue").assertIsDisplayed()
    }

    @Test
    fun overflowIcon_carriesADot_whenSyncingHasAProblem() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = idleState(deviceSync = DeviceSyncState(true, true, HistorySyncStatus(lastPassFailed = true))),
                callbacks = dashboardCallbacks()
            )
        }

        composeTestRule.onNodeWithTag(DashboardScreenTestTags.OVERFLOW_PROBLEM_BADGE, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun overflowIcon_hasNoDot_whenSyncingIsFine() {
        composeTestRule.setContent {
            DashboardScreen(
                uiState = idleState(
                    lastSyncedAtMillis = System.currentTimeMillis(),
                    deviceSync = DeviceSyncState(
                        isAvailable = true,
                        enabled = true,
                        status = HistorySyncStatus(lastSyncedAtMs = System.currentTimeMillis())
                    )
                ),
                callbacks = dashboardCallbacks()
            )
        }

        composeTestRule.onAllNodesWithTag(DashboardScreenTestTags.OVERFLOW_PROBLEM_BADGE, useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun syncSheet_offersARetry_whenTheDeviceSyncFailed() {
        var retried = false
        composeTestRule.setContent {
            DashboardScreen(
                uiState = idleState(deviceSync = DeviceSyncState(true, true, HistorySyncStatus(lastPassFailed = true))),
                callbacks = dashboardCallbacks(onRetryDeviceSync = { retried = true })
            )
        }

        openSyncSheet()
        composeTestRule.onNodeWithText("Couldn't sync. Will try again").assertIsDisplayed()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.SYNC_SHEET_DEVICES_RETRY_BUTTON).performClick()

        assertThat(retried).isTrue()
    }

    @Test
    fun syncSheet_pointsToSettings_whenDeviceSyncIsOff() {
        var openedSettings = false
        composeTestRule.setContent {
            DashboardScreen(
                uiState = idleState(deviceSync = DeviceSyncState(isAvailable = true, enabled = false)),
                callbacks = dashboardCallbacks(onOpenSettings = { openedSettings = true })
            )
        }

        openSyncSheet()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.SYNC_SHEET_DEVICES_SETTINGS_BUTTON).performClick()

        assertThat(openedSettings).isTrue()
    }

    @Test
    fun syncSheet_refreshButton_refreshesWaniKani() {
        var refreshed = false
        composeTestRule.setContent {
            DashboardScreen(
                uiState = idleState(pendingSyncCount = 3),
                callbacks = dashboardCallbacks(onRefresh = { refreshed = true })
            )
        }

        openSyncSheet()
        composeTestRule.onNodeWithText("3 waiting to send").assertIsDisplayed()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.SYNC_SHEET_REFRESH_BUTTON).performClick()

        assertThat(refreshed).isTrue()
    }

    private fun openSyncSheet() {
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.OVERFLOW_MENU).performClick()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.SYNC_STATUS_MENU_ITEM).performClick()
        composeTestRule.onNodeWithTag(DashboardScreenTestTags.SYNC_SHEET).assertIsDisplayed()
    }

    /** A loaded dashboard with nothing in flight. */
    private fun idleState(
        lastSyncedAtMillis: Long? = null,
        pendingSyncCount: Int = 0,
        deviceSync: DeviceSyncState = DeviceSyncState()
    ) = DashboardUiState(
        fetchState = DashboardFetch.Idle,
        username = "durtle_fan",
        level = 12,
        lastSyncedAtMillis = lastSyncedAtMillis,
        pendingSyncCount = pendingSyncCount,
        deviceSync = deviceSync
    )
}
