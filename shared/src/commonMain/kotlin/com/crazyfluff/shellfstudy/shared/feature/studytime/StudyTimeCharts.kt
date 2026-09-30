package com.crazyfluff.shellfstudy.shared.feature.studytime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazyfluff.shellfstudy.shared.data.studytime.LevelStudyTime
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyHeatmap
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeBucket
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

/** Narrowest a level's column gets before the chart scrolls sideways instead. */
private val MIN_LEVEL_SLOT = 28.dp

/** The chart always spans at least this many levels (one WaniKani stage), padding with upcoming ones,
 *  so a single recorded level is a slim column rather than one bar the width of the card. */
private const val MIN_LEVEL_SLOTS = 10

private const val MAX_WANIKANI_LEVEL = 60

/** One column position: a level and its recorded time, or null for a level with none (not reached
 *  yet, or passed before tracking began). */
private data class LevelSlot(val level: Int, val time: LevelStudyTime?)

/** Every level from the first recorded one to the current one, then upcoming levels until there are
 *  [MIN_LEVEL_SLOTS]. */
private fun levelSlots(levels: List<LevelStudyTime>, currentLevel: Int?): List<LevelSlot> {
    if (levels.isEmpty()) return emptyList()
    val byLevel = levels.associateBy { it.level }
    val first = byLevel.keys.min()
    val reached = maxOf(byLevel.keys.max(), currentLevel ?: 0)
    val last = maxOf(reached, first + MIN_LEVEL_SLOTS - 1).coerceAtMost(MAX_WANIKANI_LEVEL)
    return (first..last).map { LevelSlot(it, byLevel[it]) }
}

/**
 * One stacked column per WaniKani level, lowest on the left, like the history chart, each topped with
 * its time ("13h", "4.3h", "45m") so every level reads at a glance. Levels without recorded time are
 * faint stubs, which also pad a short history out to [MIN_LEVEL_SLOTS] with the levels ahead. Columns
 * share the card's width until they'd get narrower than [MIN_LEVEL_SLOT]; past that the chart scrolls
 * sideways and opens on the newest levels.
 */
@Composable
fun LevelStudyTimeChart(
    levels: List<LevelStudyTime>,
    currentLevel: Int?,
    modifier: Modifier = Modifier,
    height: Dp = 130.dp
) {
    val slots = remember(levels, currentLevel) { levelSlots(levels, currentLevel) }
    val lessonColor = lessonTimeColor()
    val reviewColor = reviewTimeColor()
    val stubColor = emptyCellColor()
    val valueStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
    val measurer = rememberTextMeasurer()
    val maxMs = levels.maxOfOrNull { it.split.totalMs }?.coerceAtLeast(1L) ?: 1L
    val scroll = rememberScrollState()
    // Newest levels are what the learner cares about, so a scrolling chart starts at its right end.
    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    // The values are drawn, not composed as Text, so they're described here for screen readers.
    val description = remember(levels) {
        levels.sortedBy { it.level }.joinToString { "Level ${it.level}: ${formatStudyDuration(it.split.totalMs)}" }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag(StudyTimeTestTags.LEVEL_CHART)
            .semantics { contentDescription = description }
    ) {
        val slot = if (slots.isEmpty()) MIN_LEVEL_SLOT else maxOf(maxWidth / slots.size, MIN_LEVEL_SLOT)
        Column(modifier = Modifier.horizontalScroll(scroll)) {
            Canvas(modifier = Modifier.width(slot * slots.size).height(height)) {
                val slotPx = slot.toPx()
                val gap = (slotPx * 0.25f).coerceAtMost(6.dp.toPx())
                val barWidth = slotPx - gap
                val radius = CornerRadius((barWidth / 2f).coerceAtMost(4.dp.toPx()))
                // Headroom so the tallest column's value still fits above it.
                val valueHeight = measurer.measure("0h", valueStyle).size.height + 2.dp.toPx()
                val barArea = size.height - valueHeight
                slots.forEachIndexed { index, column ->
                    val x = index * slotPx + gap / 2f
                    val time = column.time
                    if (time == null) {
                        val stub = 3.dp.toPx()
                        drawRoundRect(stubColor, Offset(x, size.height - stub), Size(barWidth, stub), radius)
                        return@forEachIndexed
                    }
                    val reviewHeight = barArea * (time.split.reviewMs.toFloat() / maxMs)
                    val lessonHeight = barArea * (time.split.lessonMs.toFloat() / maxMs)
                    drawStudyBar(x, barWidth, reviewHeight, lessonHeight, reviewColor, lessonColor, radius)
                    val value = measurer.measure(formatCompactDuration(time.split.totalMs), valueStyle)
                    drawText(
                        value,
                        topLeft = Offset(
                            x + (barWidth - value.size.width) / 2f,
                            size.height - reviewHeight - lessonHeight - value.size.height - 2.dp.toPx()
                        )
                    )
                }
            }
            LevelLabels(slots = slots, slotWidth = slot, currentLevel = currentLevel)
        }
    }
}

/** Level numbers under the columns: the current level strongest, levels with no time faintest. */
@Composable
private fun LevelLabels(slots: List<LevelSlot>, slotWidth: Dp, currentLevel: Int?) {
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
    Row {
        slots.forEach { column ->
            Box(modifier = Modifier.width(slotWidth), contentAlignment = Alignment.Center) {
                Text(
                    text = column.level.toString(),
                    style = labelStyle,
                    color = with(MaterialTheme.colorScheme) {
                        when {
                            column.level == currentLevel -> onSurface
                            column.time == null -> onSurfaceVariant.copy(alpha = 0.5f)
                            else -> onSurfaceVariant
                        }
                    },
                    maxLines = 1
                )
            }
        }
    }
}
