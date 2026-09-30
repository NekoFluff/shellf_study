package com.crazyfluff.shellfstudy.feature.leaderboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.model.ActivityStats
import com.crazyfluff.shellfstudy.shared.data.model.FriendEntry
import com.crazyfluff.shellfstudy.shared.data.model.FriendStats
import com.crazyfluff.shellfstudy.shared.data.model.LevelTimelinePoint
import com.crazyfluff.shellfstudy.shared.data.model.SrsCounts
import com.crazyfluff.shellfstudy.shared.designsystem.friends.FriendDetailsTestTags
import com.crazyfluff.shellfstudy.shared.feature.leaderboard.LeaderboardActions
import com.crazyfluff.shellfstudy.shared.feature.leaderboard.LeaderboardScreen
import com.crazyfluff.shellfstudy.shared.feature.leaderboard.LeaderboardScreenTestTags
import com.crazyfluff.shellfstudy.shared.feature.leaderboard.LeaderboardUiState
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Runs under Robolectric (JVM) — this screen is driven purely by state, no device features needed.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class LeaderboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mei = FriendEntry(id = "mei", nickname = "Mei", encryptedToken = "enc:1")
    private val koichi = FriendEntry(id = "koichi", nickname = "Koichi", encryptedToken = "enc:2")

    private val meiStats = FriendStats(
        friendEntryId = "mei",
        nickname = "Mei",
        username = "mei_wk",
        level = 11,
        reviewAccuracy = 0.9f,
        avgDaysPerLevel = 9.5f,
        daysSinceStart = 120,
        levelTimeline = listOf(LevelTimelinePoint(0, 1), LevelTimelinePoint(108, 11)),
        isCurrentUser = false,
        rosterIndex = 1,
        learned = ActivityStats(today = 4, week = 20),
        srsCounts = SrsCounts(apprentice = 61, guru = 140, master = 90, enlightened = 300, burned = 210),
        fetchedAtMillis = System.currentTimeMillis() - 5 * 60_000L
    )

    /** Mei has stats; Koichi is on the roster but hasn't been fetched yet. */
    private val state = LeaderboardUiState(
        friends = listOf(mei, koichi),
        isRosterLoaded = true,
        statsByFriendId = mapOf("mei" to meiStats)
    )

    private fun setContent(
        uiState: LeaderboardUiState,
        actions: RecordingLeaderboardActions = RecordingLeaderboardActions()
    ) {
        composeTestRule.setContent {
            LeaderboardScreen(uiState = uiState, actions = actions, onBack = {})
        }
    }

    /** The list is lazy, so rows below the fold aren't composed until it scrolls to them. */
    private fun scrollListTo(tag: String) {
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun `with no friends, the list is just the add row, with how to get a token under it`() {
        setContent(LeaderboardUiState(isRosterLoaded = true))

        composeTestRule.onNodeWithText("personal_access_tokens", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LeaderboardScreenTestTags.ADD_FRIEND_ROW).performClick()
        composeTestRule.onNodeWithTag(LeaderboardScreenTestTags.TOKEN_FIELD).assertIsDisplayed()
    }

    @Test
    fun `before the roster is read, nothing claims there are no friends`() {
        setContent(LeaderboardUiState(isRosterLoaded = false))

        composeTestRule.onNodeWithTag(LeaderboardScreenTestTags.ADD_FRIEND_ROW).assertIsDisplayed()
        composeTestRule.onNodeWithText("personal_access_tokens", substring = true).assertDoesNotExist()
    }

    @Test
    fun `lists every friend on the roster, and not you`() {
        setContent(state)

        composeTestRule.onNodeWithTag(LeaderboardScreenTestTags.friendRow("mei")).assertIsDisplayed()
        composeTestRule.onNodeWithText("mei_wk · Level 11").assertIsDisplayed() // The list row: no time on level.
        composeTestRule.onNodeWithTag(LeaderboardScreenTestTags.friendRow("koichi")).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LeaderboardScreenTestTags.friendRow("")).assertDoesNotExist()
        composeTestRule.onNodeWithText("You").assertDoesNotExist()
    }

    @Test
    fun `a friend without stats says so`() {
        setContent(state)

        composeTestRule.onNodeWithText("Stats not loaded yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a friend's row opens their details, with time on level, the SRS breakdown and freshness`() {
        setContent(state)

        composeTestRule.onNodeWithTag(LeaderboardScreenTestTags.friendRow("mei")).performClick()

        composeTestRule.onNodeWithTag(FriendDetailsTestTags.SHEET).assertIsDisplayed()
        composeTestRule.onNodeWithText("Level 11 for 12 days").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lessons this month").assertIsDisplayed()
        composeTestRule.onNodeWithTag(FriendDetailsTestTags.SRS_ROW).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("210").assertIsDisplayed()
        composeTestRule.onNodeWithText("mei_wk · Updated 5 minutes ago").assertIsDisplayed()
        composeTestRule.onNodeWithText("Studying for").assertDoesNotExist()
        composeTestRule.onNodeWithText("Per level").assertDoesNotExist()
    }

    @Test
    fun `remove, from the details sheet, asks before removing`() {
        val actions = RecordingLeaderboardActions()
        setContent(state, actions)

        composeTestRule.onNodeWithTag(LeaderboardScreenTestTags.friendRow("mei")).performClick()
        composeTestRule.onNodeWithTag(FriendDetailsTestTags.REMOVE_BUTTON).performScrollTo().performClick()
        assertThat(actions.calls).doesNotContain("onRemoveFriend:mei")
        composeTestRule.onNodeWithTag(LeaderboardScreenTestTags.REMOVE_CONFIRM).performClick()
        assertThat(actions.calls).contains("onRemoveFriend:mei")
    }

    @Test
    fun `a friend without stats can still be renamed or removed`() {
        val actions = RecordingLeaderboardActions()
        setContent(state, actions)

        composeTestRule.onNodeWithTag(LeaderboardScreenTestTags.friendRow("koichi")).performClick()
        composeTestRule.onNodeWithTag(FriendDetailsTestTags.RENAME_BUTTON).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Save").performClick()

        assertThat(actions.calls).contains("onEditNickname:koichi:Koichi")
    }

    @Test
    fun `the top bar and the list both open the add dialog`() {
        val actions = RecordingLeaderboardActions()
        setContent(state, actions)

        composeTestRule.onNodeWithTag(LeaderboardScreenTestTags.ADD_FRIEND_BUTTON).performClick()
        composeTestRule.onNodeWithTag(LeaderboardScreenTestTags.ADD_FRIEND_CONFIRM).performClick()
        assertThat(actions.calls).contains("onAddFriendConfirm")
        composeTestRule.onNodeWithText("Cancel").performClick()

        scrollListTo(LeaderboardScreenTestTags.ADD_FRIEND_ROW)
        composeTestRule.onNodeWithTag(LeaderboardScreenTestTags.ADD_FRIEND_ROW).performClick()
        composeTestRule.onNodeWithTag(LeaderboardScreenTestTags.TOKEN_FIELD).assertIsDisplayed()
    }

    @Test
    fun `refresh error message renders as a banner when set`() {
        setContent(state.copy(refreshErrorMessage = "Couldn't refresh 1 friend."))

        composeTestRule.onNodeWithTag(LeaderboardScreenTestTags.ERROR_BANNER).assertIsDisplayed()
        composeTestRule.onNodeWithText("Couldn't refresh 1 friend.").assertIsDisplayed()
    }

    @Test
    fun `no refresh error message shown when null`() {
        setContent(state.copy(refreshErrorMessage = null))

        composeTestRule.onNodeWithTag(LeaderboardScreenTestTags.ERROR_BANNER).assertDoesNotExist()
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
    override fun onRemoveFriend(id: String) = record("onRemoveFriend:$id")
    override fun onEditNickname(id: String, nickname: String) = record("onEditNickname:$id:$nickname")
}
