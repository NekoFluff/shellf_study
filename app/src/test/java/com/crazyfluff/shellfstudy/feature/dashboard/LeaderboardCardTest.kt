package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.model.ActivityStats
import com.crazyfluff.shellfstudy.shared.data.model.FriendStats
import com.crazyfluff.shellfstudy.shared.data.model.Leaderboard
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardMetric
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardWindow
import com.crazyfluff.shellfstudy.shared.feature.dashboard.LeaderboardCard
import com.crazyfluff.shellfstudy.shared.feature.dashboard.LeaderboardCardTestTags
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Runs under Robolectric (JVM). Pinned to SDK 35: Robolectric 4.15.1 doesn't yet have shadows for
 * this project's targetSdk (37). The qualifiers give the card enough vertical room for five rows.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class LeaderboardCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun leaderboardOf(count: Int) = Leaderboard(
        entries = List(count) { index -> entry("User ${index + 1}", index) },
        metric = LeaderboardMetric.LEARNED,
        window = LeaderboardWindow.WEEK,
        selfRank = null
    )

    private fun setContent(leaderboard: Leaderboard, onSeeAll: () -> Unit = {}) {
        composeTestRule.setContent {
            LeaderboardCard(
                leaderboard = leaderboard,
                isLoading = false,
                onMetricChange = {},
                onWindowChange = {},
                onSeeAll = onSeeAll
            )
        }
    }

    @Test
    fun `shows five rows and no See all when there are exactly five people`() {
        setContent(leaderboardOf(5))

        composeTestRule.onAllNodesWithTag(LeaderboardCardTestTags.ROW).assertCountEquals(5)
        composeTestRule.onAllNodesWithTag(LeaderboardCardTestTags.SEE_ALL).assertCountEquals(0)
    }

    @Test
    fun `shows five rows and See all when there are more than five people`() {
        setContent(leaderboardOf(7))

        composeTestRule.onAllNodesWithTag(LeaderboardCardTestTags.ROW).assertCountEquals(5)
        composeTestRule.onNodeWithTag(LeaderboardCardTestTags.SEE_ALL).assertIsDisplayed()
    }

    @Test
    fun `shows every row and no See all when there are fewer than five people`() {
        setContent(leaderboardOf(2))

        composeTestRule.onAllNodesWithTag(LeaderboardCardTestTags.ROW).assertCountEquals(2)
        composeTestRule.onAllNodesWithTag(LeaderboardCardTestTags.SEE_ALL).assertCountEquals(0)
    }

    @Test
    fun `See all opens the full leaderboard`() {
        var opened = false
        setContent(leaderboardOf(6), onSeeAll = { opened = true })

        composeTestRule.onNodeWithTag(LeaderboardCardTestTags.SEE_ALL).performClick()

        assertThat(opened).isTrue()
    }

    /** Slots deliberately do not follow the list order here — the card must color by roster index. */
    private fun entry(nickname: String, rosterIndex: Int) = FriendStats(
        friendEntryId = nickname,
        nickname = nickname,
        username = nickname.lowercase(),
        level = 1,
        reviewAccuracy = 1f,
        avgDaysPerLevel = null,
        daysSinceStart = null,
        levelTimeline = emptyList(),
        isCurrentUser = false,
        rosterIndex = rosterIndex,
        learned = ActivityStats()
    )
}
