package com.crazyfluff.shellfstudy.feature.dashboard

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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazyfluff.shellfstudy.core.data.model.ReviewForecast
import com.crazyfluff.shellfstudy.core.data.model.ReviewForecastBucket
import com.crazyfluff.shellfstudy.core.data.model.reviewForecastSummary
import com.crazyfluff.shellfstudy.core.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.core.designsystem.theme.SubjectTypeColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ReviewForecastTestTags {
    const val CARD = "review_forecast_card"
    const val CHART = "review_forecast_chart"
    const val EMPTY_STATE = "review_forecast_empty_state"
    const val SUMMARY = "review_forecast_summary"
}

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("h a", Locale.getDefault())

/** "3p"/"11a" — deliberately compact so it fits a narrow axis-label slot on one line. */
private fun compactHourLabel(instant: Instant): String {
    val time = instant.atZone(ZoneId.systemDefault())
    val hour12 = if (time.hour % 12 == 0) 12 else time.hour % 12
    val suffix = if (time.hour < 12) "a" else "p"
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
            val time = TIME_FORMATTER.format(bucket.availableAt.atZone(ZoneId.systemDefault()))
            if (bucket.newlyAvailableCount == 0) "No new reviews at $time" else "${bucket.newlyAvailableCount} new at $time"
        }
    }
    return reviewForecastSummary(forecast)
}

@Composable
private fun ReviewForecastBarChart(
    forecast: ReviewForecast?,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit
) {
    // Fixed brand color (matches the Reviews summary card) rather than colorScheme.secondary,
    // which resolves to a pale, low-contrast tint in the dark theme.
    val nowColor = SubjectTypeColors.Kanji
    val futureColor = SubjectTypeColors.Kanji.copy(alpha = 0.55f)
    val dimmedColor = SubjectTypeColors.Kanji.copy(alpha = 0.2f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    val counts: List<Int> = listOf(forecast?.reviewsAvailableNow ?: 0) +
        (forecast?.buckets?.map { it.newlyAvailableCount } ?: List(24) { 0 })
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
            val color = when {
                forecast == null -> trackColor
                selectedIndex != null && selectedIndex != index -> dimmedColor
                index == 0 -> nowColor
                else -> futureColor
            }
            drawRoundedTopBar(x = x, width = barWidth, barHeight = barHeight, color = color)
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

private fun DrawScope.drawRoundedTopBar(x: Float, width: Float, barHeight: Float, color: Color) {
    val top = size.height - barHeight
    val radius = (width / 2f).coerceAtMost(6.dp.toPx())
    val path = Path().apply {
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
    drawPath(path, color = color)
}

@Preview(showBackground = true)
@Composable
private fun ReviewForecastCardPreview() {
    ShellfStudyTheme {
        ReviewForecastCard(
            forecast = ReviewForecast(
                reviewsAvailableNow = 5,
                buckets = (1..24).map { hour ->
                    ReviewForecastBucket(hoursFromNow = hour, availableAt = Instant.now().plusSeconds(hour * 3600L), newlyAvailableCount = (hour % 6))
                }
            )
        )
    }
}
