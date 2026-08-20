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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.crazyfluff.shellfstudy.shared.designsystem.theme.kanjiColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.leaderboardUserPalette
import com.crazyfluff.shellfstudy.shared.designsystem.theme.radicalColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.vocabularyColor


private val metrics = listOf(LeaderboardMetric.LEARNED, LeaderboardMetric.BURNED, LeaderboardMetric.LEVEL)


@Composable
fun LeaderboardCard(
    leaderboard: Leaderboard,
    isLoading: Boolean,
    onMetricChange: (LeaderboardMetric) -> Unit,
    onWindowChange: (LeaderboardWindow) -> Unit,
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

            // Header: title + window dropdown
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Leaderboard",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                WindowDropdownButton(
                    selectedWindow = selectedWindow,
                    onWindowChange = onWindowChange
                )
            }

            // Compact metric pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                metrics.forEach { metric ->
                    val selected = metric == selectedMetric
                    FilterChip(
                        selected = selected,
                        onClick = { onMetricChange(metric) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        border = if (selected) null else FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = false
                        ),
                        label = {
                            Text(text = metric.displayName, style = MaterialTheme.typography.labelSmall)
                        }
                    )
                }
            }

            val palette = leaderboardUserPalette()
            val displayEntries = leaderboard.entries.take(3)
            displayEntries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                LeaderboardRow(
                    entry = entry,
                    rank = index + 1,
                    color = palette.getOrElse(index) { palette.last() },
                    metric = selectedMetric,
                    window = selectedWindow
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
        LeaderboardMetric.LEARNED -> "${entry.learned.forWindow(window)} lessons"
        LeaderboardMetric.LEVEL -> "Lv. ${entry.level}"
        LeaderboardMetric.BURNED -> "${entry.burned.forWindow(window)} burned"
        LeaderboardMetric.ACCURACY -> "—"
    }

private fun todayDelta(entry: FriendStats, metric: LeaderboardMetric): String? = when (metric) {
    LeaderboardMetric.LEARNED -> if (entry.learned.today > 0) "+${entry.learned.today} today" else null
    LeaderboardMetric.BURNED -> if (entry.burned.today > 0) "+${entry.burned.today} today" else null
    else -> null
}

@Composable
fun WindowDropdownButton(
    selectedWindow: LeaderboardWindow,
    onWindowChange: (LeaderboardWindow) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }) {
            Text(
                text = selectedWindow.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Change time window",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LeaderboardWindow.entries.forEach { window ->
                DropdownMenuItem(
                    text = { Text(window.label) },
                    onClick = { onWindowChange(window); expanded = false },
                    trailingIcon = if (window == selectedWindow) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
    }
}
