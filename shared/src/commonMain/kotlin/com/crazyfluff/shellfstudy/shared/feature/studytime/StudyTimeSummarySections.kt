package com.crazyfluff.shellfstudy.shared.feature.studytime

import androidx.compose.foundation.layout.Arrangement
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
internal fun TodayCard(overview: StudyTimeOverview) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            StudyGoalRing(
                fraction = overview.goalFraction,
                centerText = "${(overview.goalFraction * 100).roundToInt()}%",
                size = 88.dp,
                strokeWidth = 8.dp
            )
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChartLegendItem(reviewTimeColor(), "Reviews ${formatStudyDuration(overview.today.reviewMs)}")
                    ChartLegendItem(lessonTimeColor(), "Lessons ${formatStudyDuration(overview.today.lessonMs)}")
                }
                if (overview.goalStreakDays > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${overview.goalStreakDays}-day goal streak",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag(StudyTimeTestTags.GOAL_STREAK)
                    )
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
            label = windowLabel,
            modifier = Modifier.testTag(StudyTimeTestTags.windowPill(window.name))
        )
        Spacer(modifier = Modifier.height(12.dp))
        val selected = selectedBarIndex?.let { report.buckets.getOrNull(it) }
        Text(
            text = selected?.let { bucketDetail(it, isDaily) } ?: "Tap a bar for details",
            style = MaterialTheme.typography.bodySmall,
            color = with(MaterialTheme.colorScheme) { if (selected != null) onSurface else onSurfaceVariant },
            modifier = Modifier.testTag(StudyTimeTestTags.SELECTED_BAR)
        )
        Spacer(modifier = Modifier.height(8.dp))
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

private fun bucketDetail(bucket: StudyTimeBucket, isDaily: Boolean): String {
    val date = if (isDaily) {
        "${bucket.start.dayOfWeek.shortLabel()}, ${SHORT_DATE_FORMAT.format(bucket.start)}"
    } else {
        "Week of ${SHORT_DATE_FORMAT.format(bucket.start)}"
    }
    val split = bucket.split
    if (split.totalMs == 0L) return "$date: no study"
    return "$date: ${formatStudyDuration(split.totalMs)} " +
        "(reviews ${formatStudyDuration(split.reviewMs)}, lessons ${formatStudyDuration(split.lessonMs)})"
}

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
                Modifier.weight(1f).testTag(StudyTimeTestTags.PERIOD_AVERAGE)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatTile(
                "Items reviewed",
                stats.reviewItems.toString(),
                Modifier.weight(1f).testTag(StudyTimeTestTags.PERIOD_REVIEW_ITEMS)
            )
            StatTile(
                "Lessons finished",
                stats.lessonItems.toString(),
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(StudyTimeTestTags.PERIOD_CHANGE)
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
