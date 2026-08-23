package com.crazyfluff.shellfstudy.shared.feature.dashboard

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecast
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastWindow
import com.crazyfluff.shellfstudy.shared.data.model.formatHourOfDay
import com.crazyfluff.shellfstudy.shared.data.model.reviewForecastSummary
import com.crazyfluff.shellfstudy.shared.designsystem.theme.kanjiColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

object ReviewForecastTestTags {
    const val CARD = "review_forecast_card"
    const val CHART = "review_forecast_chart"
    const val EMPTY_STATE = "review_forecast_empty_state"
    const val SUMMARY = "review_forecast_summary"
}

/** "3p"/"11a" — deliberately compact so it fits a narrow axis-label slot on one line. */
private fun compactHourLabel(instant: Instant): String {
    val hour = instant.toLocalDateTime(TimeZone.currentSystemDefault()).hour
    val hour12 = if (hour % 12 == 0) 12 else hour % 12
    val suffix = if (hour < 12) "a" else "p"
    return "$hour12$suffix"
}

/** "Mon"/"Tue" — every bucket in a day-granularity window (e.g. [ReviewForecastWindow.WEEK]) lands
 *  on the same hour-of-day (see [AssignmentRepository.observeReviewForecast]'s "now"-rolling, not
 *  midnight-aligned, bucketing), so [compactHourLabel] would print the same "2p" under every bar. */
private fun compactWeekdayLabel(instant: Instant): String =
    instant.toLocalDateTime(TimeZone.currentSystemDefault()).dayOfWeek.name.take(3)
        .lowercase().replaceFirstChar { it.uppercase() }

/** "3/15" — for a bucket wider than a day (e.g. [ReviewForecastWindow.FOUR_MONTHS]'s ~10-day
 *  buckets), even a weekday name doesn't identify it; a short date does. */
private fun compactDateLabel(instant: Instant): String {
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.monthNumber}/${local.dayOfMonth}"
}

/** Picks the axis label format that actually distinguishes one bucket from its neighbor at
 *  [bucketHours]' granularity — see [compactHourLabel]/[compactWeekdayLabel]/[compactDateLabel]. */
private fun axisLabelFor(instant: Instant, bucketHours: Int): String = when {
    bucketHours < 24 -> compactHourLabel(instant)
    bucketHours == 24 -> compactWeekdayLabel(instant)
    else -> compactDateLabel(instant)
}

/** How many bar slots to skip between axis labels so a 7-bar week and a 30-bar month both end up
 *  with roughly this many labels shown, instead of a fixed stride cramming or starving either one. */
private const val TARGET_AXIS_LABEL_COUNT = 6

private fun axisLabelIntervalFor(bucketCount: Int): Int =
    ((bucketCount + TARGET_AXIS_LABEL_COUNT - 1) / TARGET_AXIS_LABEL_COUNT).coerceAtLeast(1)

@Composable
fun ReviewForecastCard(
    forecast: ReviewForecast?,
    selectedWindow: ReviewForecastWindow = ReviewForecastWindow.DAY,
    onWindowChange: (ReviewForecastWindow) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Index into the combined "now + N buckets" bar list; null means nothing tapped, showing the
    // default summary instead of a specific bar's detail. Keyed on selectedWindow so switching the
    // forecast window (which changes the bucket count) can't leave a stale index pointing at a bar
    // that no longer exists.
    var selectedIndex by remember(selectedWindow) { mutableStateOf<Int?>(null) }

    Card(modifier = modifier.fillMaxWidth().testTag(ReviewForecastTestTags.CARD)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Review Forecast", style = MaterialTheme.typography.titleMedium)
                ReviewForecastWindowDropdownButton(selectedWindow = selectedWindow, onWindowChange = onWindowChange)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = summaryText(forecast, selectedIndex, selectedWindow),
                style = MaterialTheme.typography.bodyMedium,
                // A fixed brand color rather than MaterialTheme.colorScheme.secondary: in the dark
                // scheme, secondary maps to a pale tint (see DashboardScreen's SummaryCard comment)
                // that reads as washed out against a surfaceVariant track and doesn't match the
                // vivid Kanji color the "Reviews" card elsewhere on this screen uses for the same concept.
                color = kanjiColor(),
                modifier = Modifier.testTag(ReviewForecastTestTags.SUMMARY)
            )
            Spacer(modifier = Modifier.height(12.dp))

            when {
                forecast == null ->
                    ReviewForecastBarChart(forecast = null, bucketCount = selectedWindow.bucketCount, selectedIndex = null, onSelect = {})
                forecast.reviewsAvailableNow == 0 && forecast.buckets.all { it.newlyAvailableCount == 0 } -> {
                    Text(
                        text = "All caught up.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(ReviewForecastTestTags.EMPTY_STATE)
                    )
                }
                else -> {
                    ReviewForecastBarChart(
                        forecast = forecast,
                        bucketCount = selectedWindow.bucketCount,
                        selectedIndex = selectedIndex,
                        onSelect = { index -> selectedIndex = if (selectedIndex == index) null else index }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ReviewForecastAxisLabels(forecast)
                }
            }
        }
    }
}

@Composable
private fun ReviewForecastWindowDropdownButton(
    selectedWindow: ReviewForecastWindow,
    onWindowChange: (ReviewForecastWindow) -> Unit,
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
                contentDescription = "Change forecast window",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReviewForecastWindow.entries.forEach { window ->
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

private fun summaryText(forecast: ReviewForecast?, selectedIndex: Int?, selectedWindow: ReviewForecastWindow): String {
    if (forecast == null) return "Loading…"
    if (selectedIndex != null) {
        return if (selectedIndex == 0) {
            "${forecast.reviewsAvailableNow} due now"
        } else {
            val bucket = forecast.buckets[selectedIndex - 1]
            val time = formatHourOfDay(bucket.availableAt)
            val atHour = bucket.newlyAvailableCount
            val totalByThen = forecast.reviewsAvailableNow + forecast.buckets.take(selectedIndex).sumOf { it.newlyAvailableCount }
            "$atHour at $time · $totalByThen total"
        }
    }
    return reviewForecastSummary(forecast, selectedWindow.label)
}

/** Stacking order for a bar's colored segments — radicals at the bottom, vocabulary at the top,
 *  matching the dashboard's other subject-type breakdowns (e.g. ItemSpreadCard). Kana-only
 *  vocabulary shares vocabulary's color/segment (see [subjectColor]), so its count folds in here. */
private fun countsByStackOrder(countsByType: Map<SubjectType, Int>): List<Pair<SubjectType, Int>> = listOf(
    SubjectType.RADICAL to (countsByType[SubjectType.RADICAL] ?: 0),
    SubjectType.KANJI to (countsByType[SubjectType.KANJI] ?: 0),
    SubjectType.VOCABULARY to (countsByType[SubjectType.VOCABULARY] ?: 0) + (countsByType[SubjectType.KANA_VOCABULARY] ?: 0)
)

@Composable
private fun ReviewForecastBarChart(
    forecast: ReviewForecast?,
    bucketCount: Int,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit
) {
    // Resolved here (composable scope) rather than inside the Canvas draw lambda, since
    // subjectColor() is theme-aware (dark/e-ink) and DrawScope isn't a composable context.
    val typeColors = mapOf(
        SubjectType.RADICAL to subjectColor(SubjectType.RADICAL),
        SubjectType.KANJI to subjectColor(SubjectType.KANJI),
        SubjectType.VOCABULARY to subjectColor(SubjectType.VOCABULARY)
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val yLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val textMeasurer = rememberTextMeasurer()
    val yLabelStyle = TextStyle(fontSize = 9.sp, color = yLabelColor)

    // bucketCount only matters for the forecast == null skeleton below (drawn while genuinely
    // loading, sized to whichever window is currently selected) — once real data arrives, bar
    // count always comes from forecast.buckets.size itself, never from the selected window, so a
    // forecast that hasn't caught up to a just-changed window can't be indexed past its own end.
    val counts: List<Int> = listOf(forecast?.reviewsAvailableNow ?: 0) +
        (forecast?.buckets?.map { it.newlyAvailableCount } ?: List(bucketCount) { 0 })
    val countsByType: List<Map<SubjectType, Int>> = listOf(forecast?.availableNowCountsByType ?: emptyMap()) +
        (forecast?.buckets?.map { it.countsByType } ?: List(bucketCount) { emptyMap() })
    val maxCount = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val barCount = counts.size

    // Pre-measure all three Y-axis labels to determine how wide the label column needs to be.
    val yFractions = listOf(0.25f, 0.5f, 0.75f)
    val yMeasured = yFractions.map { fraction ->
        textMeasurer.measure((maxCount * fraction).toInt().toString(), style = yLabelStyle)
    }
    val yLabelColumnWidth = yMeasured.maxOf { it.size.width }.toFloat()
    val yLabelGap = 4.dp

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .testTag(ReviewForecastTestTags.CHART)
            .pointerInput(barCount) {
                if (forecast != null) {
                    detectTapGestures { offset ->
                        val gap = 2.dp.toPx()
                        val barsWidth = size.width - yLabelColumnWidth - yLabelGap.toPx()
                        val barWidth = (barsWidth - gap * (barCount - 1)) / barCount
                        val index = (offset.x / (barWidth + gap)).toInt().coerceIn(0, barCount - 1)
                        onSelect(index)
                    }
                }
            }
    ) {
        val barsRight = size.width - yLabelColumnWidth - yLabelGap.toPx()

        // Grid lines span only the bar area, stopping before the Y-axis label column.
        yFractions.forEachIndexed { i, fraction ->
            val y = size.height * (1 - fraction)
            drawLine(gridColor, Offset(0f, y), Offset(barsRight, y), strokeWidth = 1.dp.toPx())

            // Y-axis label, vertically centered on its grid line.
            val measured = yMeasured[i]
            val labelX = size.width - measured.size.width.toFloat()
            val labelY = y - measured.size.height / 2f
            drawText(measured, topLeft = Offset(labelX, labelY))
        }

        val gap = 2.dp.toPx()
        val barWidth = (barsRight - gap * (barCount - 1)) / barCount
        counts.forEachIndexed { index, count ->
            val barHeight = if (forecast == null) 4.dp.toPx() else (size.height * (count.toFloat() / maxCount)).coerceAtLeast(2f)
            val x = index * (barWidth + gap)
            val isDimmed = selectedIndex != null && selectedIndex != index
            // Full strength for "now" (index 0), a lighter tint for future bars — dimmed further
            // still once another bar is selected.
            val alpha = when {
                isDimmed -> 0.2f
                index == 0 -> 1f
                else -> 0.55f
            }
            if (forecast == null || count == 0) {
                drawRoundedTopBar(x = x, width = barWidth, barHeight = barHeight, color = trackColor)
            } else {
                drawStackedBar(
                    x = x,
                    width = barWidth,
                    barHeight = barHeight,
                    segments = countsByStackOrder(countsByType[index]),
                    total = count,
                    colorFor = { type -> typeColors.getValue(type).copy(alpha = alpha) }
                )
            }
        }
    }
}

@Composable
private fun ReviewForecastAxisLabels(forecast: ReviewForecast) {
    // Derived from forecast.buckets itself — not the selected window — so a forecast that hasn't
    // caught up to a just-changed window (see ReviewForecastBarChart's comment) renders consistent,
    // in-bounds labels for the data it actually has, just briefly in the outgoing window's format.
    val bucketHours = forecast.buckets.firstOrNull()?.hoursFromNow ?: 1
    val labelInterval = axisLabelIntervalFor(forecast.buckets.size)
    Row(modifier = Modifier.fillMaxWidth()) {
        // now + one slot per bucket, matching the bar chart's layout exactly so labels line up
        // under the bars they describe. Only every labelInterval-th slot gets a label.
        repeat(forecast.buckets.size + 1) { index ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val label = when {
                    index == 0 -> "now"
                    index % labelInterval == 0 -> axisLabelFor(forecast.buckets[index - 1].availableAt, bucketHours)
                    else -> null
                }
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible
                    )
                }
            }
        }
    }
}

private fun DrawScope.roundedTopBarPath(x: Float, top: Float, width: Float, barHeight: Float): Path {
    val radius = (width / 2f).coerceAtMost(6.dp.toPx())
    return Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(offset = Offset(x, top), size = Size(width, barHeight)),
                topLeft = CornerRadius(radius, radius),
                topRight = CornerRadius(radius, radius),
                bottomLeft = CornerRadius.Zero,
                bottomRight = CornerRadius.Zero
            )
        )
    }
}

private fun DrawScope.drawRoundedTopBar(x: Float, width: Float, barHeight: Float, color: Color) {
    val path = roundedTopBarPath(x, size.height - barHeight, width, barHeight)
    drawPath(path, color = color)
}

/** Draws [segments] (subject type to its count within this bar) stacked bottom-to-top within the
 *  bar's own rounded-top outline — radicals at the bottom, vocabulary at the top — so the bar's
 *  overall (volume-scaled) height is unchanged, but its composition by subject type is now visible. */
private fun DrawScope.drawStackedBar(
    x: Float,
    width: Float,
    barHeight: Float,
    segments: List<Pair<SubjectType, Int>>,
    total: Int,
    colorFor: (SubjectType) -> Color
) {
    val top = size.height - barHeight
    val path = roundedTopBarPath(x, top, width, barHeight)
    clipPath(path) {
        var yOffset = size.height
        segments.forEach { (type, typeCount) ->
            if (typeCount <= 0) return@forEach
            val segmentHeight = barHeight * (typeCount.toFloat() / total)
            val segmentTop = yOffset - segmentHeight
            drawRect(color = colorFor(type), topLeft = Offset(x, segmentTop), size = Size(width, segmentHeight))
            yOffset = segmentTop
        }
    }
}
