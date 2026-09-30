package com.crazyfluff.shellfstudy.shared.feature.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeOverview
import com.crazyfluff.shellfstudy.shared.feature.studytime.ChartLegendItem
import com.crazyfluff.shellfstudy.shared.feature.studytime.StudyGoalRing
import com.crazyfluff.shellfstudy.shared.feature.studytime.StudyTimeBarChart
import com.crazyfluff.shellfstudy.shared.feature.studytime.StudyTimeTestTags
import com.crazyfluff.shellfstudy.shared.feature.studytime.formatStudyDuration
import com.crazyfluff.shellfstudy.shared.feature.studytime.lessonTimeColor
import com.crazyfluff.shellfstudy.shared.feature.studytime.reviewTimeColor

/**
 * Time on the current WaniKani level, today's study time against the daily goal (the ring), and the
 * past week at a glance. Tapping it opens the full study-time screen.
 */
@Composable
fun StudyTimeCard(
    overview: StudyTimeOverview,
    level: Int?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth().testTag(StudyTimeTestTags.DASHBOARD_CARD)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Study Time", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StudyGoalRing(
                    fraction = overview.goalFraction,
                    centerText = formatStudyDuration(overview.today.totalMs),
                    subText = "of ${formatStudyDuration(overview.goalMs)}"
                )
                Spacer(modifier = Modifier.width(16.dp))
                TodaySummary(overview = overview, modifier = Modifier.weight(1f))
                if (overview.hasAnyData) {
                    Spacer(modifier = Modifier.width(12.dp))
                    StudyTimeBarChart(
                        buckets = overview.lastSevenDays,
                        selectedIndex = null,
                        onSelect = {},
                        height = 48.dp,
                        modifier = Modifier.width(72.dp)
                    )
                }
            }
            // Its own full-width line: squeezed between the ring and the mini-chart it didn't fit on a
            // phone. No "Level 8 · day 9" either; the greeting at the top of the dashboard says that.
            levelTimeThisLevel(level?.let { overview.levelTotalsMs[it] })?.let { levelTime ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = levelTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(StudyTimeTestTags.DASHBOARD_LEVEL_TIME)
                )
            }
        }
    }
}

@Composable
private fun TodaySummary(overview: StudyTimeOverview, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        if (overview.hasAnyData) {
            ChartLegendItem(reviewTimeColor(), "Reviews ${formatStudyDuration(overview.today.reviewMs)}")
            ChartLegendItem(lessonTimeColor(), "Lessons ${formatStudyDuration(overview.today.lessonMs)}")
        } else {
            Text(
                text = "Starts with your next session",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // A one-day streak is just "met the goal today", which the full ring already says.
        if (overview.goalStreakDays > 1) {
            Text(
                text = "${overview.goalStreakDays}-day goal streak",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** "4h 24m this level": everything recorded on the current WaniKani level, or null before anything
 *  is. Counts only what was recorded since tracking began, the same as the screen's per-level list. */
internal fun levelTimeThisLevel(levelMs: Long?): String? =
    levelMs?.takeIf { it > 0L }?.let { "${formatStudyDuration(it)} this level" }
