package com.crazyfluff.shellfstudy.shared.feature.studytime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazyfluff.shellfstudy.shared.data.studytime.LevelStudyTime
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyHeatmap
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeBucket
import com.crazyfluff.shellfstudy.shared.designsystem.components.SegmentedBar
import kotlinx.datetime.DayOfWeek

/**
 * Stacked lesson/review bars, oldest on the left. [goalLineMs] and [averageLineMs] draw as dashed
 * reference lines when given. Tapping a bar selects it; tapping it again clears the selection.
 */
@Composable
fun StudyTimeBarChart(
    buckets: List<StudyTimeBucket>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    goalLineMs: Long? = null,
    averageLineMs: Long? = null,
    height: Dp = 140.dp
) {
    val lessonColor = lessonTimeColor()
    val reviewColor = reviewTimeColor()
    val trackColor = emptyCellColor()
    val goalColor = MaterialTheme.colorScheme.onSurface
    val averageColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val maxMs = maxOf(buckets.maxOfOrNull { it.split.totalMs } ?: 0L, goalLineMs ?: 0L, 60_000L)

    // The detector is installed once, so it reads the latest bar count and selection through this
    // rather than capturing whichever composition started it.
    val tapState by rememberUpdatedState(Triple(buckets.size, selectedIndex, onSelect))

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .testTag(StudyTimeTestTags.BAR_CHART)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val (count, selected, select) = tapState
                    if (count == 0) return@detectTapGestures
                    val index = (offset.x / (size.width.toFloat() / count)).toInt().coerceIn(0, count - 1)
                    select(if (index == selected) null else index)
                }
            }
    ) {
        if (buckets.isEmpty()) return@Canvas
        val slot = size.width / buckets.size
        val gap = (slot * 0.25f).coerceAtMost(6.dp.toPx())
        val barWidth = slot - gap
        val radius = CornerRadius((barWidth / 2f).coerceAtMost(4.dp.toPx()))
        fun heightOf(ms: Long) = size.height * (ms.toFloat() / maxMs)

        buckets.forEachIndexed { index, bucket ->
            val x = index * slot + gap / 2f
            val alpha = if (selectedIndex == null || selectedIndex == index) 1f else 0.3f
            if (bucket.split.totalMs == 0L) {
                val stub = 2.dp.toPx()
                drawRoundRect(trackColor, Offset(x, size.height - stub), Size(barWidth, stub), radius)
                return@forEachIndexed
            }
            drawStudyBar(
                x = x,
                width = barWidth,
                reviewHeight = heightOf(bucket.split.reviewMs),
                lessonHeight = heightOf(bucket.split.lessonMs),
                reviewColor = reviewColor.copy(alpha = alpha),
                lessonColor = lessonColor.copy(alpha = alpha),
                radius = radius
            )
        }

        fun yOf(ms: Long) = size.height - heightOf(ms)
        goalLineMs?.takeIf { it > 0L }?.let { drawReferenceLine(yOf(it), goalColor, dash = 8.dp.toPx()) }
        averageLineMs?.takeIf { it > 0L }?.let { drawReferenceLine(yOf(it), averageColor, dash = 3.dp.toPx()) }
    }
}

/** One day's column: reviews at the base, the habit every day has, with lessons stacked on top — both
 *  clipped to a single rounded-top outline so the bar reads as one column. */
private fun DrawScope.drawStudyBar(
    x: Float,
    width: Float,
    reviewHeight: Float,
    lessonHeight: Float,
    reviewColor: Color,
    lessonColor: Color,
    radius: CornerRadius
) {
    val top = size.height - reviewHeight - lessonHeight
    val outline = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(Offset(x, top), Size(width, reviewHeight + lessonHeight)),
                topLeft = radius,
                topRight = radius,
                bottomLeft = CornerRadius.Zero,
                bottomRight = CornerRadius.Zero
            )
        )
    }
    clipPath(outline) {
        drawRect(reviewColor, Offset(x, size.height - reviewHeight), Size(width, reviewHeight))
        drawRect(lessonColor, Offset(x, top), Size(width, lessonHeight))
    }
}

/** A dashed horizontal line across the chart at [y] — the goal and the average. */
private fun DrawScope.drawReferenceLine(y: Float, color: Color, dash: Float) {
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))
    )
}

/** A small colour key: swatch plus label. [dashed] draws a line swatch for reference lines. */
@Composable
fun ChartLegendItem(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dashed) {
            Canvas(modifier = Modifier.size(width = 14.dp, height = 10.dp)) {
                drawLine(
                    color = color,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))
                )
            }
        } else {
            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Weekday rows × hour columns, deeper colour for more time. */
@Composable
fun StudyHeatmapGrid(heatmap: StudyHeatmap, modifier: Modifier = Modifier) {
    val accent = reviewTimeColor()
    val empty = emptyCellColor()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val rowHeight = 14.dp
    val maxMs = heatmap.maxMs.coerceAtLeast(1L)

    Column(modifier = modifier.testTag(StudyTimeTestTags.HEATMAP)) {
        Row {
            Column(modifier = Modifier.width(28.dp)) {
                DayOfWeek.entries.forEach { day ->
                    Box(modifier = Modifier.height(rowHeight + 2.dp), contentAlignment = Alignment.CenterStart) {
                        Text(text = day.shortLabel(), style = labelStyle, color = labelColor)
                    }
                }
            }
            Canvas(modifier = Modifier.weight(1f).height((rowHeight + 2.dp) * DayOfWeek.entries.size)) {
                val cellGap = 2.dp.toPx()
                val cellWidth = (size.width - cellGap * (StudyHeatmap.HOURS - 1)) / StudyHeatmap.HOURS
                val cellHeight = rowHeight.toPx()
                val radius = CornerRadius(2.dp.toPx())
                DayOfWeek.entries.forEachIndexed { row, day ->
                    for (hour in 0 until StudyHeatmap.HOURS) {
                        val ms = heatmap.at(day, hour)
                        // A square root keeps a single long session from washing every other cell
                        // out to near-blank.
                        val intensity = kotlin.math.sqrt(ms.toFloat() / maxMs)
                        val color = if (ms == 0L) empty else accent.copy(alpha = 0.15f + 0.85f * intensity)
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(hour * (cellWidth + cellGap), row * (cellHeight + cellGap)),
                            size = Size(cellWidth, cellHeight),
                            cornerRadius = radius
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.padding(start = 28.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("12am", "6am", "12pm", "6pm", "11pm").forEach { label ->
                Text(text = label, style = labelStyle, color = labelColor)
            }
        }
    }
}

/** One bar per WaniKani level, scaled to the busiest level and split into lessons and reviews. */
@Composable
fun LevelStudyTimeList(levels: List<LevelStudyTime>, currentLevel: Int?, modifier: Modifier = Modifier) {
    val lessonColor = lessonTimeColor()
    val reviewColor = reviewTimeColor()
    val track = emptyCellColor()
    val maxMs = levels.maxOfOrNull { it.split.totalMs }?.coerceAtLeast(1L) ?: 1L
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        levels.forEach { level ->
            val isCurrent = level.level == currentLevel
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().testTag(StudyTimeTestTags.levelRow(level.level))
            ) {
                Text(
                    text = "Lv ${level.level}",
                    style = with(MaterialTheme.typography) { if (isCurrent) labelLarge else labelMedium },
                    color = with(MaterialTheme.colorScheme) { if (isCurrent) onSurface else onSurfaceVariant },
                    modifier = Modifier.width(44.dp)
                )
                // Seconds keep the Int counts SegmentedBar takes well within range.
                val lessonSeconds = level.split.lessonMs.toWholeSeconds()
                val reviewSeconds = level.split.reviewMs.toWholeSeconds()
                val restSeconds = (maxMs - level.split.totalMs).toWholeSeconds()
                SegmentedBar(
                    segments = listOf(reviewColor to reviewSeconds, lessonColor to lessonSeconds, track to restSeconds),
                    height = if (isCurrent) 12.dp else 10.dp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatStudyDuration(level.split.totalMs),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.width(56.dp)
                )
            }
        }
    }
}
