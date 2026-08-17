package com.crazyfluff.shellfstudy.shared.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazyfluff.shellfstudy.shared.data.model.FriendStats
import com.crazyfluff.shellfstudy.shared.data.model.Leaderboard
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardMetric
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardWindow
import com.crazyfluff.shellfstudy.shared.designsystem.theme.kanjiColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.radicalColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.vocabularyColor
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private val DAY_MS_CHART = 24.hours.inWholeMilliseconds

private fun formatMonthYear(epochMillis: Long): String {
    val dt = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val year = (dt.year % 100).toString().padStart(2, '0')
    return "${MonthNames.ENGLISH_ABBREVIATED.names[dt.monthNumber - 1]} '$year"
}


@Composable
fun RaceChartCard(
    leaderboard: Leaderboard,
    modifier: Modifier = Modifier
) {
    when (leaderboard.metric) {
        LeaderboardMetric.LEVEL -> LevelRaceChart(leaderboard, modifier)
        LeaderboardMetric.LEARNED -> ActivityWindowChart(leaderboard, "Lessons", modifier)
        LeaderboardMetric.BURNED -> ActivityWindowChart(leaderboard, "Items Burned", modifier)
        LeaderboardMetric.ACCURACY -> Unit
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
    val window = leaderboard.window
    val windowStartMs = when (window) {
        LeaderboardWindow.WEEK -> nowMillis - 7 * DAY_MS_CHART
        LeaderboardWindow.MONTH -> nowMillis - 30 * DAY_MS_CHART
        LeaderboardWindow.YEAR -> nowMillis - 365 * DAY_MS_CHART
        LeaderboardWindow.ALL_TIME -> Long.MIN_VALUE
    }

    val subtitle = levelChartSubtitle(window)

    val palette = leaderboardUserPalette()
    val textMeasurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val tooltipBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val tooltipFg = MaterialTheme.colorScheme.onSurface

    val userTimelines = usersWithData.map { user ->
        val startMs = nowMillis - user.daysSinceStart!! * DAY_MS_CHART
        val allPoints = user.levelTimeline.map { pt -> startMs + pt.daysSinceStart * DAY_MS_CHART to pt.level }

        val points = if (window == LeaderboardWindow.ALL_TIME) {
            allPoints + listOf(nowMillis to user.level)
        } else {
            val preWindow = allPoints.lastOrNull { (ms, _) -> ms <= windowStartMs }
            val inWindow = allPoints.filter { (ms, _) -> ms > windowStartMs }
            val startLevel = preWindow?.second ?: allPoints.firstOrNull()?.second ?: user.level
            buildList {
                add(windowStartMs to startLevel)
                addAll(inWindow)
                if (last().first < nowMillis) add(nowMillis to user.level)
            }
        }
        user to points
    }

    val globalMinMs = if (window == LeaderboardWindow.ALL_TIME) {
        userTimelines.minOf { (_, pts) -> pts.first().first }
    } else {
        windowStartMs
    }
    val timeRange = (nowMillis - globalMinMs).coerceAtLeast(1L)

    var selectedX by remember(leaderboard.window) { mutableStateOf<Float?>(null) }

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Level", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .pointerInput(leaderboard.window) {
                        detectTapGestures { offset ->
                            val prev = selectedX
                            selectedX = if (prev != null && kotlin.math.abs(offset.x - prev) < 20.dp.toPx()) null else offset.x
                        }
                    }
            ) {
                val w = size.width
                val plotH = size.height - 20.dp.toPx()
                val yLabelW = 28.dp.toPx()
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

                // X-axis date labels (4 evenly spaced, edge-anchored)
                for (i in 0..3) {
                    val ms = globalMinMs + (i.toFloat() / 3f * timeRange).toLong()
                    val x = xOf(ms)
                    val label = when (window) {
                        LeaderboardWindow.WEEK, LeaderboardWindow.MONTH -> formatShortDate(ms)
                        else -> formatMonthYear(ms)
                    }
                    val lr = textMeasurer.measure(label, labelStyle)
                    val lx = when (i) {
                        0 -> yLabelW
                        3 -> w - lr.size.width
                        else -> x - lr.size.width / 2f
                    }
                    drawText(lr, labelColor, Offset(lx, plotH + 4.dp.toPx()))
                }

                // User lines
                userTimelines.forEachIndexed { idx, (user, points) ->
                    val color = palette.getOrElse(idx) { palette.last() }
                    val strokeW = if (user.isCurrentUser) 3.dp.toPx() else 1.5.dp.toPx()

                    if (points.size == 1) {
                        val (ms, lvl) = points.first()
                        drawCircle(color, 4.dp.toPx(), Offset(xOf(ms), yOf(lvl)))
                    } else {
                        val path = Path()
                        var prevY = 0f
                        points.forEachIndexed { pIdx, (ms, lvl) ->
                            val x = xOf(ms); val y = yOf(lvl)
                            if (pIdx == 0) { path.moveTo(x, y); prevY = y }
                            else { path.lineTo(x, prevY); path.lineTo(x, y); prevY = y }
                        }
                        drawPath(path, color, style = Stroke(strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        val (lastMs, lastLvl) = points.last()
                        drawCircle(color, 4.dp.toPx(), Offset(xOf(lastMs), yOf(lastLvl)))
                    }
                }

                // Tap overlay
                val sx = selectedX?.coerceIn(yLabelW, w) ?: return@Canvas
                val scrubMs = (globalMinMs + ((sx - yLabelW) / (w - yLabelW) * timeRange).toLong())
                    .coerceIn(globalMinMs, nowMillis)

                // Crosshair
                drawLine(
                    color = labelColor.copy(alpha = 0.6f),
                    start = Offset(sx, 0f),
                    end = Offset(sx, plotH),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                )

                // Intersection dots + tooltip
                val dateLabel = when (window) {
                    LeaderboardWindow.WEEK, LeaderboardWindow.MONTH -> formatShortDate(scrubMs)
                    else -> formatMonthYear(scrubMs)
                }
                val headerResult = textMeasurer.measure(dateLabel, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold))
                val userResults = userTimelines.mapIndexed { idx, (user, points) ->
                    val color = palette.getOrElse(idx) { palette.last() }
                    val lvl = points.lastOrNull { (ms, _) -> ms <= scrubMs }?.second
                        ?: points.firstOrNull()?.second
                        ?: user.level
                    drawCircle(color, 5.dp.toPx(), Offset(sx, yOf(lvl)))
                    drawCircle(Color.White, 2.5.dp.toPx(), Offset(sx, yOf(lvl)))
                    Triple(textMeasurer.measure("${user.nickname}: Lv. $lvl", labelStyle), color, lvl)
                }

                drawTooltip(
                    header = headerResult,
                    rows = userResults.map { it.first to it.second },
                    anchorX = sx,
                    canvasWidth = w,
                    bg = tooltipBg,
                    fg = tooltipFg
                )
            }

            Spacer(Modifier.height(12.dp))
            usersWithData.forEachIndexed { idx, user ->
                ChartLegendRow(
                    color = palette.getOrElse(idx) { palette.last() },
                    label = user.nickname,
                    isCurrentUser = user.isCurrentUser
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Activity window chart — cumulative line chart per user
// ---------------------------------------------------------------------------

@Composable
private fun ActivityWindowChart(
    leaderboard: Leaderboard,
    title: String,
    modifier: Modifier
) {
    if (leaderboard.entries.isEmpty()) return

    val nowMillis = Clock.System.now().toEpochMilliseconds()
    val entries = leaderboard.entries

    val bars = buildActivityBars(entries, leaderboard.metric, leaderboard.window, nowMillis)

    val subtitle = activityChartSubtitle(leaderboard.window)

    val palette = leaderboardUserPalette()
    val textMeasurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val tooltipBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val tooltipFg = MaterialTheme.colorScheme.onSurface

    val cumulativeSeries: List<List<Int>> = entries.indices.map { ui ->
        bars.map { bar -> bar.counts.getOrElse(ui) { 0 } }
            .runningFold(0) { acc, v -> acc + v }
            .drop(1)
    }
    val maxVal = cumulativeSeries.flatten().maxOrNull()?.coerceAtLeast(1) ?: 1
    val numPoints = bars.size

    var selectedIdx by remember(leaderboard.window) { mutableStateOf<Int?>(null) }
    var canvasW by remember { mutableStateOf(0f) }
    val yLabelWPx = with(LocalDensity.current) { 36.dp.toPx() }

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .onSizeChanged { canvasW = it.width.toFloat() }
                    .pointerInput(numPoints) {
                        detectTapGestures { offset ->
                            val plotW = canvasW - yLabelWPx
                            if (plotW <= 0f || numPoints <= 1) return@detectTapGestures
                            val idx = ((offset.x - yLabelWPx) / (plotW / (numPoints - 1)))
                                .roundToInt().coerceIn(0, numPoints - 1)
                            selectedIdx = if (selectedIdx == idx) null else idx
                        }
                    }
            ) {
                val w = size.width
                val yLabelW = 36.dp.toPx()
                val plotW = w - yLabelW
                val plotH = size.height - 20.dp.toPx()

                fun xOf(i: Int): Float = yLabelW + i.toFloat() / (numPoints - 1).coerceAtLeast(1) * plotW
                fun yOf(v: Int): Float = plotH * (1f - v.toFloat() / maxVal)

                // Y-axis grid + labels
                for (frac in listOf(0.25f, 0.5f, 0.75f, 1f)) {
                    val yVal = (maxVal * frac).toInt()
                    val y = plotH * (1f - frac)
                    drawLine(gridColor, Offset(yLabelW, y), Offset(w, y), 1.dp.toPx())
                    val label = if (yVal >= 1000) "${yVal / 1000}k" else "$yVal"
                    val lr = textMeasurer.measure(label, labelStyle)
                    drawText(lr, labelColor, Offset((yLabelW - lr.size.width - 4.dp.toPx()).coerceAtLeast(0f), y - lr.size.height / 2f))
                }

                // X-axis labels — evenly distributed, edge-anchored
                val labelIndices = when (leaderboard.window) {
                    LeaderboardWindow.YEAR ->
                        listOf(0, 4, 8, numPoints - 1)
                    LeaderboardWindow.ALL_TIME ->
                        List(4) { i -> (i.toFloat() / 3f * (numPoints - 1)).roundToInt() }.distinct()
                    else -> (0 until numPoints).toList()
                }
                labelIndices.forEach { bi ->
                    val x = xOf(bi)
                    val lr = textMeasurer.measure(bars[bi].label, labelStyle)
                    val lx = when (bi) {
                        0 -> yLabelW
                        numPoints - 1 -> w - lr.size.width
                        else -> x - lr.size.width / 2f
                    }
                    drawText(lr, labelColor, Offset(lx, plotH + 4.dp.toPx()))
                }

                // Lines per user
                cumulativeSeries.forEachIndexed { ui, cumValues ->
                    val color = palette.getOrElse(ui) { palette.last() }
                    val strokeW = if (entries.getOrNull(ui)?.isCurrentUser == true) 3.dp.toPx() else 1.5.dp.toPx()
                    if (cumValues.size >= 2) {
                        val path = Path()
                        cumValues.forEachIndexed { i, v ->
                            val x = xOf(i); val y = yOf(v)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color, style = Stroke(strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    val lastIdx = cumValues.lastIndex
                    if (lastIdx >= 0) {
                        drawCircle(color, 4.dp.toPx(), Offset(xOf(lastIdx), yOf(cumValues[lastIdx])))
                    }
                }

                // Tap overlay
                val snapIdx = selectedIdx ?: return@Canvas
                val snapX = xOf(snapIdx)

                // Crosshair
                drawLine(
                    color = labelColor.copy(alpha = 0.6f),
                    start = Offset(snapX, 0f),
                    end = Offset(snapX, plotH),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                )

                // Intersection dots + tooltip
                val headerResult = textMeasurer.measure(bars[snapIdx].label, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold))
                val userResults = cumulativeSeries.mapIndexed { ui, cumValues ->
                    val color = palette.getOrElse(ui) { palette.last() }
                    val v = cumValues.getOrElse(snapIdx) { 0 }
                    drawCircle(color, 5.dp.toPx(), Offset(snapX, yOf(v)))
                    drawCircle(Color.White, 2.5.dp.toPx(), Offset(snapX, yOf(v)))
                    val nickname = entries.getOrNull(ui)?.nickname ?: "?"
                    textMeasurer.measure("$nickname: $v", labelStyle) to color
                }
                drawTooltip(
                    header = headerResult,
                    rows = userResults,
                    anchorX = snapX,
                    canvasWidth = w,
                    bg = tooltipBg,
                    fg = tooltipFg
                )
            }

            Spacer(Modifier.height(12.dp))
            entries.forEachIndexed { idx, user ->
                ChartLegendRow(
                    color = palette.getOrElse(idx) { palette.last() },
                    label = user.nickname,
                    isCurrentUser = user.isCurrentUser
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTooltip(
    header: TextLayoutResult,
    rows: List<Pair<TextLayoutResult, Color>>,
    anchorX: Float,
    canvasWidth: Float,
    bg: Color,
    fg: Color
) {
    val tooltipPx = 8.dp.toPx()
    val tooltipPy = 6.dp.toPx()
    val lineGap = 3.dp.toPx()
    val allResults = listOf(header) + rows.map { it.first }
    val tooltipW = allResults.maxOf { it.size.width } + tooltipPx * 2
    val tooltipH = allResults.sumOf { it.size.height } + lineGap * (allResults.size - 1) + tooltipPy * 2
    val tooltipX = if (anchorX + tooltipW + 10.dp.toPx() > canvasWidth) anchorX - tooltipW - 10.dp.toPx() else anchorX + 10.dp.toPx()
    val tooltipY = 4.dp.toPx()
    drawRoundRect(bg, Offset(tooltipX, tooltipY), Size(tooltipW, tooltipH), CornerRadius(8.dp.toPx()))
    var ty = tooltipY + tooltipPy
    drawText(header, fg, Offset(tooltipX + tooltipPx, ty))
    ty += header.size.height + lineGap
    rows.forEach { (result, color) ->
        drawText(result, color, Offset(tooltipX + tooltipPx, ty))
        ty += result.size.height + lineGap
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
