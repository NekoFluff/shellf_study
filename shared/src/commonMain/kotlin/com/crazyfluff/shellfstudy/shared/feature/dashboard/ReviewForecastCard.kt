package com.crazyfluff.shellfstudy.shared.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.crazyfluff.shellfstudy.shared.data.model.ItemSpreadBucket
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecast
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastColorMode
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastWindow
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.data.model.bucketMomentPhrase
import com.crazyfluff.shellfstudy.shared.data.model.formatBucketDate
import com.crazyfluff.shellfstudy.shared.data.model.reviewForecastSummary
import com.crazyfluff.shellfstudy.shared.designsystem.theme.kanjiColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.srsStageColor
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

/** "3p"/"11a" — deliberately compact so it fits a narrow axis-label slot on one line. Only used for
 *  [ReviewForecastWindow.DAY]'s hourly buckets; every wider window uses [formatBucketDate] instead
 *  (see [axisLabelFor]), since a bucket spanning a day or more isn't identified by an hour-of-day. */
private fun compactHourLabel(instant: Instant): String {
    val hour = instant.toLocalDateTime(TimeZone.currentSystemDefault()).hour
    val hour12 = if (hour % 12 == 0) 12 else hour % 12
    val suffix = if (hour < 12) "a" else "p"
    return "$hour12$suffix"
}

/** Picks the axis label format that actually distinguishes one bucket from its neighbor at
 *  [bucketHours]' granularity: an hour-of-day for [ReviewForecastWindow.DAY]'s hourly buckets, a
 *  short date (shared with the summary sentence's [bucketMomentPhrase]) for every wider window —
 *  3d/7d/30d/4mo all use it, so a bucket several hours or days wide is identified by which day it
 *  falls on rather than a repeating, uninformative hour-of-day. */
private fun axisLabelFor(instant: Instant, bucketHours: Int): String =
    if (bucketHours <= 1) compactHourLabel(instant) else formatBucketDate(instant)

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
    selectedColorMode: ReviewForecastColorMode = ReviewForecastColorMode.SUBJECT_TYPE,
    onColorModeChange: (ReviewForecastColorMode) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Index into the combined "now + N buckets" bar list; null means nothing tapped, showing the
    // default summary instead of a specific bar's detail. Keyed on selectedWindow so switching the
    // forecast window (which changes the bucket count) can't leave a stale index pointing at a bar
    // that no longer exists. Not keyed on selectedColorMode: that only changes segment coloring
    // within each bar, never the bar count, so a tapped index stays valid across it.
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
            Spacer(modifier = Modifier.height(2.dp))
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
            Spacer(modifier = Modifier.height(4.dp))
            ReviewForecastColorModeChips(selectedColorMode = selectedColorMode, onColorModeChange = onColorModeChange)
            Spacer(modifier = Modifier.height(4.dp))

            when {
                forecast == null ->
                    ReviewForecastBarChart(
                        forecast = null,
                        bucketCount = selectedWindow.bucketCount,
                        colorMode = selectedColorMode,
                        selectedIndex = null,
                        onSelect = {}
                    )
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
                        colorMode = selectedColorMode,
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

/** Compact pill toggle between the bar chart's two color breakdowns — mirrors LeaderboardCard's
 *  metric pills. Both breakdowns are always present on [ReviewForecast], so switching here never
 *  needs a re-fetch, unlike the window dropdown. */
@Composable
private fun ReviewForecastColorModeChips(
    selectedColorMode: ReviewForecastColorMode,
    onColorModeChange: (ReviewForecastColorMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReviewForecastColorMode.entries.forEach { mode ->
            val selected = mode == selectedColorMode
            FilterChip(
                selected = selected,
                onClick = { onColorModeChange(mode) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                ),
                border = if (selected) null else FilterChipDefaults.filterChipBorder(enabled = true, selected = false),
                label = { Text(text = mode.label, style = MaterialTheme.typography.labelSmall) }
            )
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
        // A plain clickable Row rather than TextButton: Material3 enforces a ~40dp minimum button
        // height (well above this trigger's own label+icon size), which was inflating the title
        // row it sits in — the title, vertically centered against that taller sibling, ended up
        // with several extra dp of dead space below it that no amount of shrinking the explicit
        // Spacer below could remove, since that space was inside the row, not in the Spacer.
        Row(
            modifier = Modifier.clickable(onClick = { expanded = true }),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            val bucketHours = forecast.buckets.first().hoursFromNow
            val moment = bucketMomentPhrase(bucket.availableAt, bucketHours)
            val newlyAvailable = bucket.newlyAvailableCount
            val totalByThen = forecast.reviewsAvailableNow + forecast.buckets.take(selectedIndex).sumOf { it.newlyAvailableCount }
            "$newlyAvailable $moment · $totalByThen total"
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

/** Stacking order for the "next SRS stage" breakdown — earliest stage at the bottom, most advanced
 *  (Burned) at the top, matching [countsByStackOrder]'s low-to-high convention. LOCKED is omitted:
 *  an assignment already in the forecast is unlocked, so it can never be the *next* stage. */
private fun countsByStageStackOrder(countsByStage: Map<ItemSpreadBucket, Int>): List<Pair<ItemSpreadBucket, Int>> = listOf(
    ItemSpreadBucket.APPRENTICE to (countsByStage[ItemSpreadBucket.APPRENTICE] ?: 0),
    ItemSpreadBucket.GURU to (countsByStage[ItemSpreadBucket.GURU] ?: 0),
    ItemSpreadBucket.MASTER to (countsByStage[ItemSpreadBucket.MASTER] ?: 0),
    ItemSpreadBucket.ENLIGHTENED to (countsByStage[ItemSpreadBucket.ENLIGHTENED] ?: 0),
    ItemSpreadBucket.BURNED to (countsByStage[ItemSpreadBucket.BURNED] ?: 0)
)

/** Representative [SrsStage] for each bucket's color, matching [srsStageColor]'s per-stage palette
 *  and ItemSpreadCard's own bucket→representative-stage choices. */
private fun representativeStage(bucket: ItemSpreadBucket): SrsStage = when (bucket) {
    ItemSpreadBucket.LOCKED -> SrsStage.LOCKED
    ItemSpreadBucket.APPRENTICE -> SrsStage.APPRENTICE_1
    ItemSpreadBucket.GURU -> SrsStage.GURU_1
    ItemSpreadBucket.MASTER -> SrsStage.MASTER
    ItemSpreadBucket.ENLIGHTENED -> SrsStage.ENLIGHTENED
    ItemSpreadBucket.BURNED -> SrsStage.BURNED
}

/** Everything the tap handler in [ReviewForecastBarChart] needs to resolve a screen offset to a
 *  bar index, bundled so one [rememberUpdatedState] keeps all of it current — see that composable's
 *  comment for why the handler can't just capture these values directly. */
private data class BarTapGeometry(
    val isSelectable: Boolean,
    val barCount: Int,
    val yLabelColumnWidth: Float,
    val onSelect: (Int) -> Unit
)

@Composable
private fun ReviewForecastBarChart(
    forecast: ReviewForecast?,
    bucketCount: Int,
    colorMode: ReviewForecastColorMode,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit
) {
    // Resolved here (composable scope) rather than inside the Canvas draw lambda, since
    // subjectColor()/srsStageColor() are theme-aware (dark/e-ink) and DrawScope isn't a composable
    // context. Base colors only — the alpha (dimmed/full/lighter) that depends on tap state and bar
    // position is applied per-segment down in the draw loop below.
    val typeColors = mapOf(
        SubjectType.RADICAL to subjectColor(SubjectType.RADICAL),
        SubjectType.KANJI to subjectColor(SubjectType.KANJI),
        SubjectType.VOCABULARY to subjectColor(SubjectType.VOCABULARY)
    )
    val stageColors = mapOf(
        ItemSpreadBucket.APPRENTICE to srsStageColor(representativeStage(ItemSpreadBucket.APPRENTICE)),
        ItemSpreadBucket.GURU to srsStageColor(representativeStage(ItemSpreadBucket.GURU)),
        ItemSpreadBucket.MASTER to srsStageColor(representativeStage(ItemSpreadBucket.MASTER)),
        ItemSpreadBucket.ENLIGHTENED to srsStageColor(representativeStage(ItemSpreadBucket.ENLIGHTENED)),
        ItemSpreadBucket.BURNED to srsStageColor(representativeStage(ItemSpreadBucket.BURNED))
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
    // Each bar's segments pre-resolved to (base color, count) pairs, bottom-to-top, so the draw
    // loop below stays agnostic to whether the underlying key was a SubjectType or an
    // ItemSpreadBucket — it only ever deals in Color from this point on.
    val segmentsByBar: List<List<Pair<Color, Int>>> = when (colorMode) {
        ReviewForecastColorMode.SUBJECT_TYPE ->
            (listOf(forecast?.availableNowCountsByType ?: emptyMap()) +
                (forecast?.buckets?.map { it.countsByType } ?: List(bucketCount) { emptyMap() }))
                .map { byType -> countsByStackOrder(byType).map { (type, count) -> typeColors.getValue(type) to count } }
        ReviewForecastColorMode.SRS_STAGE ->
            (listOf(forecast?.availableNowCountsByNextStage ?: emptyMap()) +
                (forecast?.buckets?.map { it.countsByNextStage } ?: List(bucketCount) { emptyMap() }))
                .map { byStage -> countsByStageStackOrder(byStage).map { (stage, count) -> stageColors.getValue(stage) to count } }
    }
    val maxCount = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val barCount = counts.size

    // Pre-measure all three Y-axis labels to determine how wide the label column needs to be.
    val yFractions = listOf(0.25f, 0.5f, 0.75f)
    val yMeasured = yFractions.map { fraction ->
        textMeasurer.measure((maxCount * fraction).toInt().toString(), style = yLabelStyle)
    }
    val yLabelColumnWidth = yMeasured.maxOf { it.size.width }.toFloat()
    val yLabelGap = 4.dp

    // The gesture detector below is installed once (`pointerInput(Unit)`) and left running for the
    // composable's whole lifetime, so it never captures a stale barCount/yLabelColumnWidth/onSelect
    // from whichever composition happened to be active when it started — rememberUpdatedState keeps
    // this bundle current on every read instead. A key like `pointerInput(barCount)` looks safe but
    // isn't: DAY and THREE_DAYS both have 24 buckets, so switching between them never restarts the
    // detector, yet yLabelColumnWidth (driven by the data's max count, not the window) still moves —
    // taps silently drift out of alignment with the bars actually drawn until something else happens
    // to change barCount.
    val currentTapGeometry by rememberUpdatedState(
        BarTapGeometry(isSelectable = forecast != null, barCount = barCount, yLabelColumnWidth = yLabelColumnWidth, onSelect = onSelect)
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .testTag(ReviewForecastTestTags.CHART)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val geometry = currentTapGeometry
                    if (!geometry.isSelectable) return@detectTapGestures
                    val gap = 2.dp.toPx()
                    val barsWidth = size.width - geometry.yLabelColumnWidth - yLabelGap.toPx()
                    val barWidth = (barsWidth - gap * (geometry.barCount - 1)) / geometry.barCount
                    val index = (offset.x / (barWidth + gap)).toInt().coerceIn(0, geometry.barCount - 1)
                    geometry.onSelect(index)
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
            // Baseline (nothing selected): "now" pops at full strength, future bars sit at a
            // lighter tint. Once a bar is tapped, IT pops to full strength instead — even a future
            // bar, not just "now" — and every other bar (now included) fades further to make room.
            val alpha = when {
                selectedIndex == index -> 1f
                selectedIndex != null -> 0.2f
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
                    segments = segmentsByBar[index].map { (color, segmentCount) -> color.copy(alpha = alpha) to segmentCount },
                    total = count
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

/** Draws [segments] (a color and its count within this bar) stacked bottom-to-top within the bar's
 *  own rounded-top outline — order and color already decided by the caller (see
 *  [countsByStackOrder]/[countsByStageStackOrder]) — so the bar's overall (volume-scaled) height is
 *  unchanged, but its composition by whichever breakdown is selected is now visible. */
private fun DrawScope.drawStackedBar(
    x: Float,
    width: Float,
    barHeight: Float,
    segments: List<Pair<Color, Int>>,
    total: Int
) {
    val top = size.height - barHeight
    val path = roundedTopBarPath(x, top, width, barHeight)
    clipPath(path) {
        var yOffset = size.height
        segments.forEach { (color, segmentCount) ->
            if (segmentCount <= 0) return@forEach
            val segmentHeight = barHeight * (segmentCount.toFloat() / total)
            val segmentTop = yOffset - segmentHeight
            drawRect(color = color, topLeft = Offset(x, segmentTop), size = Size(width, segmentHeight))
            yOffset = segmentTop
        }
    }
}
