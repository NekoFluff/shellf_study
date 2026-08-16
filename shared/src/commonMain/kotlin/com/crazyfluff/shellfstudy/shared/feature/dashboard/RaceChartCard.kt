package com.crazyfluff.shellfstudy.shared.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazyfluff.shellfstudy.shared.data.model.FriendStats
import com.crazyfluff.shellfstudy.shared.data.model.Leaderboard
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardMetric
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardWindow
import com.crazyfluff.shellfstudy.shared.designsystem.theme.SubjectTypeColors

private val raceChartPalette = listOf(
    SubjectTypeColors.Kanji,
    SubjectTypeColors.Radical,
    SubjectTypeColors.Vocabulary,
    Color(0xFFE65100),
    Color(0xFF00695C),
    Color(0xFF1565C0)
)

@Composable
fun RaceChartCard(
    leaderboard: Leaderboard,
    modifier: Modifier = Modifier
) {
    when (leaderboard.metric) {
        LeaderboardMetric.LEVEL -> LevelRaceChart(leaderboard, modifier)
        else -> ActivityComparisonChart(leaderboard, modifier)
    }
}

@Composable
private fun LevelRaceChart(leaderboard: Leaderboard, modifier: Modifier) {
    val usersWithTimeline = leaderboard.entries.filter { it.levelTimeline.isNotEmpty() }
    if (usersWithTimeline.isEmpty()) return

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Level Race",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Days on WaniKani vs. level reached",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            val maxDays = usersWithTimeline.maxOf { user ->
                user.levelTimeline.maxOf { it.daysSinceStart }
            }.coerceAtLeast(1)
            val maxLevel = 60
            val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                val w = size.width
                val h = size.height

                for (lvl in listOf(10, 20, 30, 40, 50)) {
                    val y = h - (lvl.toFloat() / maxLevel) * h
                    drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
                }

                usersWithTimeline.forEachIndexed { idx, user ->
                    val color = raceChartPalette.getOrElse(idx) { raceChartPalette.last() }
                    val strokeWidth = if (user.isCurrentUser) 3.dp.toPx() else 1.5.dp.toPx()

                    if (user.levelTimeline.size == 1) {
                        val pt = user.levelTimeline.first()
                        val x = (pt.daysSinceStart.toFloat() / maxDays) * w
                        val y = h - (pt.level.toFloat() / maxLevel) * h
                        drawCircle(color, radius = 4.dp.toPx(), center = Offset(x, y))
                    } else {
                        val path = Path()
                        user.levelTimeline.forEachIndexed { pIdx, pt ->
                            val x = (pt.daysSinceStart.toFloat() / maxDays) * w
                            val y = h - (pt.level.toFloat() / maxLevel) * h
                            if (pIdx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path, color,
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                        val last = user.levelTimeline.last()
                        val ex = (last.daysSinceStart.toFloat() / maxDays) * w
                        val ey = h - (last.level.toFloat() / maxLevel) * h
                        drawCircle(color, radius = 4.dp.toPx(), center = Offset(ex, ey))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            usersWithTimeline.forEachIndexed { idx, user ->
                val color = raceChartPalette.getOrElse(idx) { raceChartPalette.last() }
                RaceChartLegendRow(color = color, label = user.nickname, isCurrentUser = user.isCurrentUser)
            }
        }
    }
}

@Composable
private fun ActivityComparisonChart(leaderboard: Leaderboard, modifier: Modifier) {
    if (leaderboard.entries.isEmpty()) return

    val (title, subtitle) = chartTitleAndSubtitle(leaderboard.metric, leaderboard.window)

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            val values = leaderboard.entries.map { entryValue(it, leaderboard.metric, leaderboard.window).toFloat() }
            val maxVal = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f

            leaderboard.entries.forEachIndexed { idx, entry ->
                val color = raceChartPalette.getOrElse(idx) { raceChartPalette.last() }
                val fraction = values[idx] / maxVal
                ActivityBarRow(
                    label = entry.nickname,
                    value = values[idx].toInt(),
                    fraction = fraction,
                    color = color,
                    isCurrentUser = entry.isCurrentUser
                )
                if (idx < leaderboard.entries.lastIndex) Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ActivityBarRow(
    label: String,
    value: Int,
    fraction: Float,
    color: Color,
    isCurrentUser: Boolean
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(72.dp),
            maxLines = 1
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
        ) {
            val barH = size.height * 0.6f
            val top = (size.height - barH) / 2f
            val cornerR = CornerRadius(barH / 2f)

            // Track
            drawRoundRect(trackColor, topLeft = Offset(0f, top), size = Size(size.width, barH), cornerRadius = cornerR)
            // Fill
            val fillW = (size.width * fraction).coerceAtLeast(if (fraction > 0f) barH else 0f)
            if (fillW > 0f) {
                drawRoundRect(color, topLeft = Offset(0f, top), size = Size(fillW, barH), cornerRadius = cornerR)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.width(36.dp)
        )
    }
}

private fun chartTitleAndSubtitle(metric: LeaderboardMetric, window: LeaderboardWindow): Pair<String, String> {
    val windowLabel = when (window) {
        LeaderboardWindow.WEEK -> "this week"
        LeaderboardWindow.MONTH -> "this month"
        LeaderboardWindow.YEAR -> "this year"
        LeaderboardWindow.ALL_TIME -> "all time"
    }
    return when (metric) {
        LeaderboardMetric.LEARNED -> "Items Learned" to "New items started $windowLabel"
        LeaderboardMetric.REVIEWS -> "Reviews Done" to "Total review attempts all time"
        LeaderboardMetric.BURNED -> "Items Burned" to "Items burned $windowLabel"
        LeaderboardMetric.ACCURACY -> "Review Accuracy" to "Correct answers over all-time reviews"
        LeaderboardMetric.LEVEL -> "Level Race" to "Days on WaniKani vs. level reached"
    }
}

private fun entryValue(entry: FriendStats, metric: LeaderboardMetric, window: LeaderboardWindow): Int =
    when (metric) {
        LeaderboardMetric.LEARNED -> entry.learned.forWindow(window)
        LeaderboardMetric.REVIEWS -> entry.totalReviews
        LeaderboardMetric.BURNED -> entry.burned.forWindow(window)
        LeaderboardMetric.ACCURACY -> if (entry.reviewAccuracy < 0f) 0 else (entry.reviewAccuracy * 100).toInt()
        LeaderboardMetric.LEVEL -> entry.level
    }

@Composable
private fun RaceChartLegendRow(
    color: Color,
    label: String,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(10.dp)) { drawCircle(color) }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp
        )
    }
}
