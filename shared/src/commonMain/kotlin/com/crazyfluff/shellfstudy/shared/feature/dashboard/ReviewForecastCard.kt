package com.crazyfluff.shellfstudy.shared.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecast
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastBucket
import com.crazyfluff.shellfstudy.shared.data.model.formatHourOfDay
import com.crazyfluff.shellfstudy.shared.data.model.reviewForecastSummary
import com.crazyfluff.shellfstudy.shared.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.shared.designsystem.theme.SubjectTypeColors
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

@Composable
fun ReviewForecastCard(forecast: ReviewForecast?, modifier: Modifier = Modifier) {
    // Index into the combined "now + 24 buckets" bar list; null means nothing tapped, showing the
    // default summary instead of a specific bar's detail.
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Card(modifier = modifier.fillMaxWidth().testTag(ReviewForecastTestTags.CARD)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Review Forecast", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = summaryText(forecast, selectedIndex),
                style = MaterialTheme.typography.bodyMedium,
                // A fixed brand color rather than MaterialTheme.colorScheme.secondary: in the dark
                // scheme, secondary maps to a pale tint (see DashboardScreen's SummaryCard comment)
                // that reads as washed out against a surfaceVariant track and doesn't match the
                // vivid Kanji color the "Reviews" card elsewhere on this screen uses for the same concept.
                color = SubjectTypeColors.Kanji,
                modifier = Modifier.testTag(ReviewForecastTestTags.SUMMARY)
            )
            Spacer(modifier = Modifier.height(12.dp))

            when {
                forecast == null -> ReviewForecastBarChart(forecast = null, selectedIndex = null, onSelect = {})
                forecast.reviewsAvailableNow == 0 && forecast.buckets.all { it.newlyAvailableCount == 0 } -> {
                    Text(
                        text = "All caught up — nothing due in the next 24 hours.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(ReviewForecastTestTags.EMPTY_STATE)
                    )
                }
                else -> {
                    ReviewForecastBarChart(
                        forecast = forecast,
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

private fun summaryText(forecast: ReviewForecast?, selectedIndex: Int?): String {
    if (forecast == null) return "Loading…"
    if (selectedIndex != null) {
        return if (selectedIndex == 0) {
            "${forecast.reviewsAvailableNow} due right now"
        } else {
            val bucket = forecast.buckets[selectedIndex - 1]
            val time = formatHourOfDay(bucket.availableAt)
            // Cumulative — everything due by this point in time, not just what newly becomes
            // available in this one hour's bucket — since "how many reviews would I have if I
            // waited until X" is the more useful number to plan around.
            val totalByThen = forecast.reviewsAvailableNow + forecast.buckets.take(selectedIndex).sumOf { it.newlyAvailableCount }
            "$totalByThen total due by $time"
        }
    }
    return reviewForecastSummary(forecast)
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

    val counts: List<Int> = listOf(forecast?.reviewsAvailableNow ?: 0) +
        (forecast?.buckets?.map { it.newlyAvailableCount } ?: List(24) { 0 })
    val countsByType: List<Map<SubjectType, Int>> = listOf(forecast?.availableNowCountsByType ?: emptyMap()) +
        (forecast?.buckets?.map { it.countsByType } ?: List(24) { emptyMap() })
    val maxCount = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val barCount = counts.size

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .testTag(ReviewForecastTestTags.CHART)
            .pointerInput(barCount) {
                if (forecast != null) {
                    detectTapGestures { offset ->
                        val gap = 2.dp.toPx()
                        val barWidth = (size.width - gap * (barCount - 1)) / barCount
                        val index = (offset.x / (barWidth + gap)).toInt().coerceIn(0, barCount - 1)
                        onSelect(index)
                    }
                }
            }
    ) {
        // A light baseline grid (25%/50%/75%) gives a visual reference for bar heights instead of
        // leaving viewers to guess magnitude from bar height alone.
        listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
            val y = size.height * (1 - fraction)
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }

        val gap = 2.dp.toPx()
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        counts.forEachIndexed { index, count ->
            val barHeight = if (forecast == null) 4.dp.toPx() else (size.height * (count.toFloat() / maxCount)).coerceAtLeast(2f)
            val x = index * (barWidth + gap)
            val isDimmed = selectedIndex != null && selectedIndex != index
            // Full strength for "now" (index 0), a lighter tint for future bars — same distinction
            // the old single-hue bars made — dimmed further still once another bar is selected.
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
    Row(modifier = Modifier.fillMaxWidth()) {
        // 25 slots (now + 24 hourly buckets), matching the bar chart's layout exactly so labels
        // line up under the bars they describe. Only every 4th slot gets a label to avoid clutter.
        repeat(25) { index ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val label = when {
                    index == 0 -> "now"
                    index % 4 == 0 -> compactHourLabel(forecast.buckets[index - 1].availableAt)
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
