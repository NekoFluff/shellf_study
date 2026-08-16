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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazyfluff.shellfstudy.shared.data.model.FriendStats
import com.crazyfluff.shellfstudy.shared.data.model.Leaderboard
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardMetric
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardWindow
import com.crazyfluff.shellfstudy.shared.designsystem.theme.SubjectTypeColors
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private const val DAY_MS_CHART = 86_400_000L

private val raceChartPalette = listOf(
    SubjectTypeColors.Kanji,
    SubjectTypeColors.Radical,
    SubjectTypeColors.Vocabulary,
    Color(0xFFE65100),
    Color(0xFF00695C),
    Color(0xFF1565C0)
)

private val MONTH_ABBREVS = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

private fun formatDateLabel(epochMillis: Long): String {
    val dt = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val year = (dt.year % 100).toString().padStart(2, '0')
    return "${MONTH_ABBREVS[dt.monthNumber - 1]} '$year"
}

private fun activityValue(entry: FriendStats, metric: LeaderboardMetric, window: LeaderboardWindow): Int =
    when (metric) {
        LeaderboardMetric.LEARNED -> entry.learned.forWindow(window)
        LeaderboardMetric.BURNED -> entry.burned.forWindow(window)
        else -> 0
    }

@Composable
fun RaceChartCard(
    leaderboard: Leaderboard,
    modifier: Modifier = Modifier
) {
    when (leaderboard.metric) {
        LeaderboardMetric.LEVEL -> LevelRaceChart(leaderboard, modifier)
        LeaderboardMetric.LEARNED -> ActivityWindowChart(leaderboard, "Items Learned", modifier)
        LeaderboardMetric.BURNED -> ActivityWindowChart(leaderboard, "Items Burned", modifier)
        LeaderboardMetric.REVIEWS, LeaderboardMetric.ACCURACY -> Unit
    }
}

// ---------------------------------------------------------------------------
// Level race — calendar dates on X axis, level on Y
// ---------------------------------------------------------------------------

@Composable
private fun LevelRaceChart(leaderboard: Leaderboard, modifier: Modifier) {
    val usersWithData = leaderboard.entries.filter {
        it.levelTimeline.isNotEmpty() && it.daysSinceStart != null
    }
    if (usersWithData.isEmpty()) return

    val nowMillis = Clock.System.now().toEpochMilliseconds()
    val textMeasurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall

    // Convert daysSinceStart offsets to absolute epoch millis
    val userTimelines = usersWithData.map { user ->
        val startMs = nowMillis - user.daysSinceStart!! * DAY_MS_CHART
        val points = user.levelTimeline.map { pt -> startMs + pt.daysSinceStart * DAY_MS_CHART to pt.level }
        user to points
    }

    val globalMinMs = userTimelines.minOf { (_, pts) -> pts.first().first }
    val timeRange = (nowMillis - globalMinMs).coerceAtLeast(1L)

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Level Race", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Level over time",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                val w = size.width
                val h = size.height
                val xLabelH = 20.dp.toPx()
                val yLabelW = 28.dp.toPx()
                val plotH = h - xLabelH
                val maxLvl = 60f

                fun xOf(ms: Long) = yLabelW + (ms - globalMinMs).toFloat() / timeRange * (w - yLabelW)
                fun yOf(lvl: Int) = plotH * (1f - lvl / maxLvl)

                // Horizontal grid lines
                for (lvl in listOf(10, 20, 30, 40, 50)) {
                    val y = yOf(lvl)
                    drawLine(gridColor, Offset(yLabelW, y), Offset(w, y), 1.dp.toPx())
                    val lr = textMeasurer.measure("$lvl", labelStyle)
                    drawText(lr, labelColor, Offset(0f, y - lr.size.height / 2f))
                }

                // X-axis date labels (4 evenly spaced)
                for (i in 0..3) {
                    val ms = globalMinMs + (i.toFloat() / 3f * timeRange).toLong()
                    val x = xOf(ms)
                    val lr = textMeasurer.measure(formatDateLabel(ms), labelStyle)
                    val lx = (x - lr.size.width / 2f).coerceIn(yLabelW, w - lr.size.width)
                    drawText(lr, labelColor, Offset(lx, plotH + 4.dp.toPx()))
                }

                // User lines
                userTimelines.forEachIndexed { idx, (user, points) ->
                    val color = raceChartPalette.getOrElse(idx) { raceChartPalette.last() }
                    val strokeW = if (user.isCurrentUser) 3.dp.toPx() else 1.5.dp.toPx()

                    if (points.size == 1) {
                        val (ms, lvl) = points.first()
                        drawCircle(color, 4.dp.toPx(), Offset(xOf(ms), yOf(lvl)))
                    } else {
                        val path = Path()
                        points.forEachIndexed { pIdx, (ms, lvl) ->
                            val x = xOf(ms); val y = yOf(lvl)
                            if (pIdx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color, style = Stroke(strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        val (lastMs, lastLvl) = points.last()
                        drawCircle(color, 4.dp.toPx(), Offset(xOf(lastMs), yOf(lastLvl)))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            usersWithData.forEachIndexed { idx, user ->
                ChartLegendRow(
                    color = raceChartPalette.getOrElse(idx) { raceChartPalette.last() },
                    label = user.nickname,
                    isCurrentUser = user.isCurrentUser
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Activity window chart — 4 window groups × N user bars
// ---------------------------------------------------------------------------

@Composable
private fun ActivityWindowChart(
    leaderboard: Leaderboard,
    title: String,
    modifier: Modifier
) {
    if (leaderboard.entries.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val labelStyle = MaterialTheme.typography.labelSmall
    val boldLabelStyle = labelStyle.copy(fontWeight = FontWeight.Bold)

    val windows = LeaderboardWindow.entries
    val entries = leaderboard.entries
    val metric = leaderboard.metric
    val selectedWindow = leaderboard.window
    val N = entries.size

    val allValues = windows.flatMap { wnd -> entries.map { activityValue(it, metric, wnd) } }
    val maxVal = allValues.maxOrNull()?.coerceAtLeast(1) ?: 1

    val windowLabel = when (selectedWindow) {
        LeaderboardWindow.WEEK -> "this week"
        LeaderboardWindow.MONTH -> "this month"
        LeaderboardWindow.YEAR -> "this year"
        LeaderboardWindow.ALL_TIME -> "all time"
    }

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "All time periods — $windowLabel highlighted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                val w = size.width
                val h = size.height
                val xLabelH = 20.dp.toPx()
                val yLabelW = 32.dp.toPx()
                val plotW = w - yLabelW
                val plotH = h - xLabelH

                // Y-axis grid + labels
                for (frac in listOf(0.25f, 0.5f, 0.75f, 1f)) {
                    val yVal = (maxVal * frac).toInt()
                    val y = plotH * (1f - frac)
                    drawLine(gridColor, Offset(yLabelW, y), Offset(w, y), 1.dp.toPx())
                    val label = if (yVal >= 1000) "${yVal / 1000}k" else "$yVal"
                    val lr = textMeasurer.measure(label, labelStyle)
                    val lx = (yLabelW - lr.size.width - 4.dp.toPx()).coerceAtLeast(0f)
                    drawText(lr, labelColor, Offset(lx, y - lr.size.height / 2f))
                }

                val groupW = plotW / windows.size
                val barPadding = groupW * 0.1f
                val availW = groupW - barPadding * 2f
                val barGap = 2.dp.toPx()
                val barW = if (N > 1) (availW - barGap * (N - 1)) / N else availW

                windows.forEachIndexed { wi, window ->
                    val isSelected = window == selectedWindow
                    val groupLeft = yLabelW + wi * groupW
                    val groupCenterX = groupLeft + groupW / 2f

                    // X-axis label
                    val lStr = when (window) {
                        LeaderboardWindow.WEEK -> "Week"
                        LeaderboardWindow.MONTH -> "Month"
                        LeaderboardWindow.YEAR -> "Year"
                        LeaderboardWindow.ALL_TIME -> "All"
                    }
                    val lrStyle = if (isSelected) boldLabelStyle else labelStyle
                    val lrColor = if (isSelected) primaryColor else labelColor
                    val lr = textMeasurer.measure(lStr, lrStyle)
                    drawText(lr, lrColor, Offset(groupCenterX - lr.size.width / 2f, plotH + 4.dp.toPx()))

                    // One bar per user
                    entries.forEachIndexed { ui, entry ->
                        val value = activityValue(entry, metric, window)
                        val barLeft = groupLeft + barPadding + ui * (barW + barGap)
                        val barH = (value.toFloat() / maxVal) * plotH
                        val alpha = if (isSelected) 1f else 0.3f
                        val color = raceChartPalette.getOrElse(ui) { raceChartPalette.last() }.copy(alpha = alpha)

                        if (barH > 0.5f) {
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(barLeft, plotH - barH),
                                size = Size(barW.coerceAtLeast(1.dp.toPx()), barH),
                                cornerRadius = CornerRadius(2.dp.toPx())
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            entries.forEachIndexed { idx, user ->
                ChartLegendRow(
                    color = raceChartPalette.getOrElse(idx) { raceChartPalette.last() },
                    label = user.nickname,
                    isCurrentUser = user.isCurrentUser
                )
            }
        }
    }
}

@Composable
private fun ChartLegendRow(
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
