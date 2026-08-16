package com.crazyfluff.shellfstudy.shared.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazyfluff.shellfstudy.shared.data.model.FriendStats
import com.crazyfluff.shellfstudy.shared.data.model.Leaderboard
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardMetric
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardWindow
import com.crazyfluff.shellfstudy.shared.designsystem.theme.SubjectTypeColors

private val leaderboardPalette = listOf(
    SubjectTypeColors.Kanji,
    SubjectTypeColors.Radical,
    SubjectTypeColors.Vocabulary,
    Color(0xFFE65100),
    Color(0xFF00695C),
    Color(0xFF1565C0)
)

private val metrics = LeaderboardMetric.entries

@Composable
fun LeaderboardCard(
    leaderboard: Leaderboard,
    isLoading: Boolean,
    onMetricChange: (LeaderboardMetric) -> Unit,
    onWindowChange: (LeaderboardWindow) -> Unit,
    onFriendTap: (FriendStats) -> Unit,
    onSeeAll: () -> Unit,
    selectedMetric: LeaderboardMetric = leaderboard.metric,
    selectedWindow: LeaderboardWindow = leaderboard.window,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = leaderboard.selfRank?.let { "You're #$it!" } ?: "Leaderboard",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            ScrollableTabRow(
                selectedTabIndex = metrics.indexOf(selectedMetric),
                edgePadding = 16.dp
            ) {
                metrics.forEachIndexed { index, metric ->
                    Tab(
                        selected = index == metrics.indexOf(selectedMetric),
                        onClick = { onMetricChange(metric) },
                        text = {
                            Text(
                                text = when (metric) {
                                    LeaderboardMetric.LEARNED -> "Learned"
                                    LeaderboardMetric.REVIEWS -> "Reviews"
                                    LeaderboardMetric.LEVEL -> "Level"
                                    LeaderboardMetric.BURNED -> "Burned"
                                    LeaderboardMetric.ACCURACY -> "Accuracy"
                                }
                            )
                        }
                    )
                }
            }

            // Window filter chips — shown only for windowed metrics
            if (selectedMetric == LeaderboardMetric.LEARNED || selectedMetric == LeaderboardMetric.BURNED) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LeaderboardWindow.entries.forEach { window ->
                        FilterChip(
                            selected = window == selectedWindow,
                            onClick = { onWindowChange(window) },
                            label = {
                                Text(
                                    text = when (window) {
                                        LeaderboardWindow.WEEK -> "Week"
                                        LeaderboardWindow.MONTH -> "Month"
                                        LeaderboardWindow.YEAR -> "Year"
                                        LeaderboardWindow.ALL_TIME -> "All time"
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            val displayEntries = leaderboard.entries.take(3)
            displayEntries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                LeaderboardRow(
                    entry = entry,
                    rank = index + 1,
                    color = leaderboardPalette.getOrElse(index) { leaderboardPalette.last() },
                    metric = selectedMetric,
                    window = selectedWindow,
                    onTap = { if (!entry.isCurrentUser) onFriendTap(entry) }
                )
            }

            if (leaderboard.entries.size > 3) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onSeeAll) { Text("See all") }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LeaderboardRow(
    entry: FriendStats,
    rank: Int,
    color: Color,
    metric: LeaderboardMetric,
    window: LeaderboardWindow,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = if (entry.isCurrentUser) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .clickable(enabled = !entry.isCurrentUser, onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$rank",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp)
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (entry.nickname.firstOrNull() ?: entry.username.firstOrNull() ?: '?')
                    .uppercaseChar().toString(),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = entry.nickname,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (entry.isCurrentUser) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = metricValue(entry, metric, window),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            val todayDelta = todayDelta(entry, metric)
            if (todayDelta != null) {
                Text(
                    text = todayDelta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun metricValue(entry: FriendStats, metric: LeaderboardMetric, window: LeaderboardWindow): String =
    when (metric) {
        LeaderboardMetric.LEARNED -> "${entry.learned.forWindow(window)} learned"
        LeaderboardMetric.REVIEWS -> "${entry.totalReviews} reviews"
        LeaderboardMetric.LEVEL -> "Lv. ${entry.level}"
        LeaderboardMetric.BURNED -> "${entry.burned.forWindow(window)} 🔥"
        LeaderboardMetric.ACCURACY ->
            if (entry.reviewAccuracy < 0f) "—" else "${(entry.reviewAccuracy * 100).toInt()}%"
    }

private fun todayDelta(entry: FriendStats, metric: LeaderboardMetric): String? = when (metric) {
    LeaderboardMetric.LEARNED -> if (entry.learned.today > 0) "+${entry.learned.today} today" else null
    LeaderboardMetric.BURNED -> if (entry.burned.today > 0) "+${entry.burned.today} today" else null
    else -> null
}
