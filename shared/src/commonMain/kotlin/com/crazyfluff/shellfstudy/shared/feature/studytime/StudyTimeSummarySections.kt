package com.crazyfluff.shellfstudy.shared.feature.studytime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeBucket
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeOverview
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimePeriodStats
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeReport
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeWindow
import com.crazyfluff.shellfstudy.shared.designsystem.components.PillSelector
import kotlin.math.abs
import kotlin.math.roundToInt

// Today, history and period totals — the "how much" half of the screen.

@Composable
internal fun TodayCard(overview: StudyTimeOverview, studyStreakDays: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            StudyGoalRing(
                today = overview.today,
                goalMs = overview.goalMs,
                centerText = "${(overview.goalFraction * 100).roundToInt()}%",
                size = 88.dp,
                strokeWidth = 8.dp
            )
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (studyStreakDays >= 1) {
                        StudyStreakPill(
                            days = studyStreakDays,
                            iconSize = 16.dp,
                            textStyle = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.testTag(StudyTimeTestTags.STUDY_STREAK)
                        )
                    }
                }
                Text(
                    text = formatStudyDuration(overview.today.totalMs),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.testTag(StudyTimeTestTags.TODAY_TOTAL)
                )
                val remainingMs = overview.goalMs - overview.today.totalMs
                Text(
                    text = if (remainingMs <= 0L) {
                        "Daily goal of ${formatStudyDuration(overview.goalMs)} met"
                    } else {
                        "${formatStudyDuration(remainingMs)} to your ${formatStudyDuration(overview.goalMs)} goal"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ChartLegendItem(reviewTimeColor(), "Reviews ${formatStudyDuration(overview.today.reviewMs)}")
                    ChartLegendItem(lessonTimeColor(), "Lessons ${formatStudyDuration(overview.today.lessonMs)}")
                }
            }
        }
    }
}

internal val windowLabel: (StudyTimeWindow) -> String = { window ->
    when (window) {
        StudyTimeWindow.WEEK -> "7 days"
        StudyTimeWindow.MONTH -> "30 days"
        StudyTimeWindow.YEAR -> "52 weeks"
    }
}

private fun StudyTimeWindow.previousPeriodLabel(): String = when (this) {
    StudyTimeWindow.WEEK -> "the 7 days before"
    StudyTimeWindow.MONTH -> "the 30 days before"
    StudyTimeWindow.YEAR -> "the year before"
}

@Composable
internal fun HistoryCard(
    report: StudyTimeReport,
    selectedBarIndex: Int?,
    onWindowSelect: (StudyTimeWindow) -> Unit,
    onBarSelect: (Int?) -> Unit
) {
    val window = report.window
    val isDaily = window.daysPerBucket == 1
    SectionCard(title = "History") {
        PillSelector(
            options = StudyTimeWindow.entries,
            selected = window,
            onSelect = onWindowSelect,
            label = windowLabel
        )
        Spacer(modifier = Modifier.height(12.dp))
        val selected = selectedBarIndex?.let { report.buckets.getOrNull(it) }
        BarDetailPanel(selected, isDaily)
        Spacer(modifier = Modifier.height(12.dp))
        StudyTimeBarChart(
            buckets = report.buckets,
            selectedIndex = selectedBarIndex,
            onSelect = onBarSelect,
            // A daily goal only lines up with daily bars; on weekly bars it would sit a seventh as high
            // as it should.
            goalLineMs = if (isDaily) report.overview.goalMs else null,
            averageLineMs = report.stats.dailyAverageMs * window.daysPerBucket
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            report.buckets.firstOrNull()?.let {
                Text(SHORT_DATE_FORMAT.format(it.start), style = MaterialTheme.typography.labelSmall)
            }
            Text(if (isDaily) "Today" else "This week", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChartLegendItem(reviewTimeColor(), "Reviews")
            ChartLegendItem(lessonTimeColor(), "Lessons")
            if (isDaily) ChartLegendItem(MaterialTheme.colorScheme.onSurface, "Goal", dashed = true)
            ChartLegendItem(MaterialTheme.colorScheme.onSurfaceVariant, "Average", dashed = true)
        }
    }
}

/** The tapped bar: its date and total, then time and items for reviews and lessons side by side. Laid
 *  out the same with nothing tapped, so the chart under it doesn't jump on the first tap. */
@Composable
private fun BarDetailPanel(bucket: StudyTimeBucket?, isDaily: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(emptyCellColor())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = bucket?.let { bucketDate(it, isDaily) } ?: "Tap a bar for details",
                style = MaterialTheme.typography.labelLarge,
                color = with(MaterialTheme.colorScheme) { if (bucket != null) onSurface else onSurfaceVariant },
                modifier = Modifier.weight(1f).testTag(StudyTimeTestTags.SELECTED_BAR)
            )
            Text(
                text = bucket?.let { formatStudyDuration(it.split.totalMs) } ?: "",
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BarDetailColumn(
                color = reviewTimeColor(),
                label = "Reviews",
                ms = bucket?.split?.reviewMs,
                count = bucket?.items?.reviews?.let { countLabel(it, "review") },
                modifier = Modifier.weight(1f).testTag(StudyTimeTestTags.SELECTED_BAR_REVIEWS)
            )
            BarDetailColumn(
                color = lessonTimeColor(),
                label = "Lessons",
                ms = bucket?.split?.lessonMs,
                count = bucket?.items?.lessons?.let { countLabel(it, "lesson") },
                modifier = Modifier.weight(1f).testTag(StudyTimeTestTags.SELECTED_BAR_LESSONS)
            )
        }
    }
}

/** A swatch and label over the time and item count, or dashes with nothing tapped. */
@Composable
private fun BarDetailColumn(color: Color, label: String, ms: Long?, count: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ChartLegendItem(color, label)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = ms?.let(::formatStudyDuration) ?: "–", style = MaterialTheme.typography.titleMedium)
        Text(
            text = count ?: " ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun bucketDate(bucket: StudyTimeBucket, isDaily: Boolean): String = if (isDaily) {
    "${bucket.start.dayOfWeek.shortLabel()}, ${SHORT_DATE_FORMAT.format(bucket.start)}"
} else {
    "Week of ${SHORT_DATE_FORMAT.format(bucket.start)}"
}

private fun countLabel(count: Int, noun: String): String =
    "${formatCount(count.toLong())} $noun${if (count == 1) "" else "s"}"

@Composable
internal fun PeriodStatsCard(stats: StudyTimePeriodStats, window: StudyTimeWindow) {
    SectionCard(title = "Last ${windowLabel(window)}") {
        Row(modifier = Modifier.fillMaxWidth()) {
            StatTile(
                "Total",
                formatStudyDuration(stats.total.totalMs),
                Modifier.weight(1f).testTag(StudyTimeTestTags.PERIOD_TOTAL)
            )
            StatTile(
                "Daily average",
                formatStudyDuration(stats.dailyAverageMs),
                Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatTile(
                "Items reviewed",
                formatCount(stats.reviewItems.toLong()),
                Modifier.weight(1f).testTag(StudyTimeTestTags.PERIOD_REVIEW_ITEMS)
            )
            StatTile(
                "Lessons finished",
                formatCount(stats.lessonItems.toLong()),
                Modifier.weight(1f).testTag(StudyTimeTestTags.PERIOD_LESSON_ITEMS)
            )
        }
        stats.changeFraction?.let { change ->
            Spacer(modifier = Modifier.height(12.dp))
            val percent = (abs(change) * 100).roundToInt()
            val arrow = if (change >= 0) "▲" else "▼"
            Text(
                text = "$arrow $percent% ${if (change >= 0) "more" else "less"} than ${window.previousPeriodLabel()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    // Merged so a screen reader reads "Total, 1h" as one item.
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.titleLarge)
    }
}
