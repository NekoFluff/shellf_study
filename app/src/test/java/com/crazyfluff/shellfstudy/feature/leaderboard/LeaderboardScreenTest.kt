package com.crazyfluff.shellfstudy.feature.leaderboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.model.FriendEntry
import com.crazyfluff.shellfstudy.shared.feature.leaderboard.LeaderboardActions
import com.crazyfluff.shellfstudy.shared.feature.leaderboard.LeaderboardScreen
import com.crazyfluff.shellfstudy.shared.feature.leaderboard.LeaderboardUiState
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
class LeaderboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val oneFriend = listOf(FriendEntry(id = "1", nickname = "Alex", encryptedToken = "enc:token"))

    private fun setContent(uiState: LeaderboardUiState) {
        composeTestRule.setContent {
            LeaderboardScreen(
                uiState = uiState,
                actions = RecordingLeaderboardActions(),
                onBack = {}
            )
        }
    }

    @Test
    fun `refresh error message renders when set`() {
        setContent(
            LeaderboardUiState(
                friends = oneFriend,
                refreshErrorMessage = "Couldn't refresh 1 friend."
            )
        )

        composeTestRule.onNodeWithText("Couldn't refresh 1 friend.").assertIsDisplayed()
    }

    @Test
    fun `no refresh error message shown when null`() {
        setContent(LeaderboardUiState(friends = oneFriend, refreshErrorMessage = null))

        composeTestRule.onAllNodesWithText("Couldn't refresh 1 friend.").assertCountEquals(0)
    }
}

/** A [LeaderboardActions] that records what it was asked to do — see the Lesson and Review tests. */
private class RecordingLeaderboardActions : LeaderboardActions {
    val calls = mutableListOf<String>()

    private fun record(name: String) { calls += name }

    override fun onRefresh() = record("onRefresh")
    override fun onAddFriendNicknameChange(value: String) = record("onAddFriendNicknameChange")
    override fun onAddFriendTokenChange(value: String) = record("onAddFriendTokenChange")
    override fun onAddFriendConfirm() = record("onAddFriendConfirm")
    override fun onRemoveFriend(id: String) = record("onRemoveFriend")
    override fun onEditNickname(id: String, nickname: String) = record("onEditNickname")
}
