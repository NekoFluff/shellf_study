package com.crazyfluff.shellfstudy.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.audio.LevelAudioRow
import com.crazyfluff.shellfstudy.shared.data.audio.LevelAudioStatus
import com.crazyfluff.shellfstudy.shared.data.audio.OfflineAudioPreferences
import com.crazyfluff.shellfstudy.shared.data.audio.OfflineAudioState
import com.crazyfluff.shellfstudy.shared.data.audio.OfflineAudioVoices
import com.crazyfluff.shellfstudy.shared.feature.settings.OfflineAudioActions
import com.crazyfluff.shellfstudy.shared.feature.settings.OfflineAudioScreen
import com.crazyfluff.shellfstudy.shared.feature.settings.OfflineAudioTestTags
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class OfflineAudioScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class RecordingActions : OfflineAudioActions {
        val calls = mutableListOf<String>()
        override fun onAutoDownloadCurrentLevelChange(enabled: Boolean) { calls += "auto:$enabled" }
        override fun onWifiOnlyChange(enabled: Boolean) { calls += "wifi:$enabled" }
        override fun onVoicesChange(voices: OfflineAudioVoices) { calls += "voices:$voices" }
        override fun onDownloadLevel(level: Int) { calls += "download:$level" }
        override fun onDownloadCurrentLevel() { calls += "downloadCurrent" }
        override fun onDownloadUnlockedLevels() { calls += "downloadUnlocked" }
        override fun onDownloadNow() { calls += "downloadNow" }
        override fun onDeleteLevel(level: Int) { calls += "delete:$level" }
        override fun onDeleteAll() { calls += "deleteAll" }
    }

    private fun state(vararg statuses: Pair<Int, LevelAudioStatus>) = OfflineAudioState(
        preferences = OfflineAudioPreferences(currentLevel = 3, maxLevelGranted = 3),
        levels = (1..60).map { level ->
            LevelAudioRow(level, statuses.toMap()[level] ?: LevelAudioStatus.NotDownloaded(clips = 100))
        },
        totalClips = 210,
        totalBytes = 3_565_158,
        downloadedLevels = 1
    )

    private fun setContent(state: OfflineAudioState, actions: OfflineAudioActions = RecordingActions()) {
        composeTestRule.setContent { OfflineAudioScreen(state = state, actions = actions, onBack = {}) }
    }

    @Test
    fun `the summary totals what's on the device`() {
        setContent(state(1 to LevelAudioStatus.Downloaded(clips = 210, bytes = 3_565_158)))

        composeTestRule.onNodeWithText("1 level downloaded").assertIsDisplayed()
        composeTestRule.onNodeWithText("3.4 MB · 210 clips on this device").assertIsDisplayed()
    }

    @Test
    fun `each level row offers what fits its state`() {
        val actions = RecordingActions()
        setContent(
            state(
                1 to LevelAudioStatus.Downloaded(clips = 210, bytes = 3_565_158),
                2 to LevelAudioStatus.NotDownloaded(clips = 180),
                4 to LevelAudioStatus.Locked
            ),
            actions
        )

        composeTestRule.onNodeWithTag(OfflineAudioTestTags.levelDeleteTag(1)).performScrollTo().performClick()
        composeTestRule.onNodeWithTag(OfflineAudioTestTags.levelDownloadTag(2)).performScrollTo().performClick()
        composeTestRule.onNodeWithTag(OfflineAudioTestTags.levelRowTag(4)).performScrollTo()
        composeTestRule.onNodeWithText("Not included in your subscription").assertIsDisplayed()

        assertThat(actions.calls).containsExactly("delete:1", "download:2").inOrder()
    }

    @Test
    fun `waiting downloads can be started by hand`() {
        val actions = RecordingActions()
        setContent(state(3 to LevelAudioStatus.Queued(stored = 0, clips = 100)), actions)

        composeTestRule.onNodeWithTag(OfflineAudioTestTags.DOWNLOAD_NOW_ROW).performClick()

        assertThat(actions.calls).containsExactly("downloadNow")
    }

    @Test
    fun `deleting everything asks first`() {
        val actions = RecordingActions()
        setContent(state(), actions)

        composeTestRule.onNodeWithTag(OfflineAudioTestTags.DELETE_ALL_ROW).performScrollTo().performClick()
        assertThat(actions.calls).isEmpty()
        composeTestRule.onNodeWithTag(OfflineAudioTestTags.DELETE_ALL_CONFIRM_BUTTON).performClick()

        assertThat(actions.calls).containsExactly("deleteAll")
    }
}
