package com.crazyfluff.shellfstudy.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.AppSettings
import com.crazyfluff.shellfstudy.shared.data.DAILY_STUDY_MINUTES_GOAL_RANGE
import com.crazyfluff.shellfstudy.shared.data.NotificationSettings
import com.crazyfluff.shellfstudy.shared.data.ThemeMode
import com.crazyfluff.shellfstudy.shared.data.historysync.HistorySyncStatus
import com.crazyfluff.shellfstudy.shared.data.model.ReviewPriority
import com.crazyfluff.shellfstudy.shared.designsystem.AppVersion
import com.crazyfluff.shellfstudy.shared.feature.settings.AccountSummary
import com.crazyfluff.shellfstudy.shared.feature.settings.FullRefreshStatus
import com.crazyfluff.shellfstudy.shared.feature.settings.HistorySyncUiState
import com.crazyfluff.shellfstudy.shared.feature.settings.SettingsActions
import com.crazyfluff.shellfstudy.shared.feature.settings.SettingsScreen
import com.crazyfluff.shellfstudy.shared.feature.settings.SettingsScreenTestTags
import com.crazyfluff.shellfstudy.shared.feature.settings.SettingsUiState
import com.crazyfluff.shellfstudy.shared.feature.settings.SyncedHistoryDeletion
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Job
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Settings screen's Sync group: the switch, its status line, and deleting synced data. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class SettingsSyncGroupTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        uiState: SettingsUiState,
        actions: SettingsActions = RecordingSettingsActions(),
        onNotificationsEnabledChange: (Boolean) -> Unit = {},
        onOpenLeaderboard: () -> Unit = {},
        onBack: () -> Unit = {},
        appVersion: AppVersion? = null
    ) {
        composeTestRule.setContent {
            SettingsScreen(
                uiState = uiState,
                actions = actions,
                onNotificationsEnabledChange = onNotificationsEnabledChange,
                onOpenLeaderboard = onOpenLeaderboard,
                onBack = onBack,
                appVersion = appVersion
            )
        }
    }


    @Test
    fun syncGroup_isHiddenUntilTheBackendIsAvailable() {
        setContent(uiState = SettingsUiState(historySync = HistorySyncUiState(isAvailable = false)))

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.HISTORY_SYNC_TOGGLE).assertDoesNotExist()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.DELETE_SYNCED_HISTORY_ROW).assertDoesNotExist()
    }

    @Test
    fun historySyncToggle_asksBeforeTurningOn() {
        val actions = RecordingSettingsActions()
        setContent(uiState = SettingsUiState(historySync = HistorySyncUiState(isAvailable = true)), actions = actions)

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.HISTORY_SYNC_TOGGLE).performScrollTo().performClick()
        assertThat(actions.calls).doesNotContain("onHistorySyncEnabledChange")

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.HISTORY_SYNC_CONFIRM_BUTTON).performClick()
        assertThat(actions.lastArgumentOf("onHistorySyncEnabledChange")).isEqualTo(true)
    }

    @Test
    fun historySyncToggle_turnsOffWithoutAsking() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(historySync = HistorySyncUiState(isAvailable = true, enabled = true)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.HISTORY_SYNC_TOGGLE)
            .performScrollTo()
            .assertIsOn()
            .performClick()

        assertThat(actions.lastArgumentOf("onHistorySyncEnabledChange")).isEqualTo(false)
    }

    @Test
    fun historySyncStatus_saysWhenItLastSynced() {
        setContent(
            uiState = SettingsUiState(
                historySync = HistorySyncUiState(
                    isAvailable = true,
                    enabled = true,
                    status = HistorySyncStatus(lastSyncedAtMs = System.currentTimeMillis())
                )
            )
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.HISTORY_SYNC_STATUS)
            .performScrollTo()
            .assertTextEquals("Last synced just now")
    }

    @Test
    fun historySyncStatus_saysWhenTheLastSyncFailed() {
        setContent(
            uiState = SettingsUiState(
                historySync = HistorySyncUiState(
                    isAvailable = true,
                    enabled = true,
                    status = HistorySyncStatus(lastPassFailed = true)
                )
            )
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.HISTORY_SYNC_STATUS)
            .performScrollTo()
            .assertTextEquals("Couldn't sync. Will try again")
    }

    @Test
    fun historySyncStatus_isHiddenWhileSyncIsOff() {
        setContent(uiState = SettingsUiState(historySync = HistorySyncUiState(isAvailable = true, enabled = false)))

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.HISTORY_SYNC_STATUS).assertDoesNotExist()
    }

    @Test
    fun deleteSyncedHistory_asksForConfirmationFirst() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(historySync = HistorySyncUiState(isAvailable = true, enabled = true)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.DELETE_SYNCED_HISTORY_ROW).performScrollTo().performClick()
        assertThat(actions.calls).doesNotContain("onDeleteSyncedHistoryRequested")

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.DELETE_SYNCED_HISTORY_CONFIRM_BUTTON).performClick()
        assertThat(actions.calls).contains("onDeleteSyncedHistoryRequested")
    }

    @Test
    fun deleteSyncedHistory_showsProgressInsteadOfTheRowWhileDeleting() {
        setContent(
            uiState = SettingsUiState(
                historySync = HistorySyncUiState(
                    isAvailable = true,
                    enabled = true,
                    deletion = SyncedHistoryDeletion.InFlight
                )
            )
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.DELETE_SYNCED_HISTORY_PROGRESS)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.DELETE_SYNCED_HISTORY_ROW).assertDoesNotExist()
    }

    @Test
    fun deleteSyncedHistory_showsTheErrorOnFailure() {
        setContent(
            uiState = SettingsUiState(
                historySync = HistorySyncUiState(
                    isAvailable = true,
                    enabled = true,
                    deletion = SyncedHistoryDeletion.Failed("Couldn't delete synced data. Try again later.")
                )
            )
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.DELETE_SYNCED_HISTORY_ERROR_TEXT)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun accountRow_showsUsernameAndLevel() {
        setContent(uiState = SettingsUiState(account = AccountSummary(username = "koichi", level = 12)))

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.ACCOUNT_ROW).performScrollTo()
        composeTestRule.onNodeWithText("koichi").assertIsDisplayed()
        composeTestRule.onNodeWithText("Level 12", substring = true).assertIsDisplayed()
    }

    @Test
    fun accountRow_isHiddenBeforeTheFirstSync_butLogOutIsStillOffered() {
        setContent(uiState = SettingsUiState(account = null))

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.ACCOUNT_ROW).assertDoesNotExist()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LOG_OUT_ROW).performScrollTo().assertIsDisplayed()
    }
}
