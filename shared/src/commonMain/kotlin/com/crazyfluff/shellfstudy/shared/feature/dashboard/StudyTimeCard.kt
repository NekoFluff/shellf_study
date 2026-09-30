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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.studytime.QueueEstimate
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeOverview
import com.crazyfluff.shellfstudy.shared.feature.studytime.ChartLegendItem
import com.crazyfluff.shellfstudy.shared.feature.studytime.StudyGoalRing
import com.crazyfluff.shellfstudy.shared.feature.studytime.StudyTimeBarChart
import com.crazyfluff.shellfstudy.shared.feature.studytime.StudyTimeTestTags
import com.crazyfluff.shellfstudy.shared.feature.studytime.formatStudyDuration
import com.crazyfluff.shellfstudy.shared.feature.studytime.lessonTimeColor
import com.crazyfluff.shellfstudy.shared.feature.studytime.reviewTimeColor

/**
 * Time on the current WaniKani level, today's study time against the daily goal (the ring), the past
 * week at a glance, and how long today's plan should take at the learner's own pace. Tapping it opens
 * the full study-time screen.
 */
@Composable
fun StudyTimeCard(
    overview: StudyTimeOverview,
    planEstimate: QueueEstimate?,
    reviewCount: Int,
    lessonsLeftForGoal: Int,
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
                    subText = "of ${formatStudyDuration(overview.goalMs)}",
                    modifier = Modifier.testTag(StudyTimeTestTags.DASHBOARD_RING)
                )
                Spacer(modifier = Modifier.width(16.dp))
                TodaySummary(
                    overview = overview,
                    levelTime = levelTimeThisLevel(level?.let { overview.levelTotalsMs[it] }),
                    modifier = Modifier.weight(1f)
                )
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
            if (planEstimate != null && planEstimate.totalMs > 0L) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = todaysPlanText(
                        estimate = planEstimate,
                        reviewCount = reviewCount,
                        lessonsLeftForGoal = lessonsLeftForGoal,
                        hasOwnPace = overview.pace.reviewMsPerItem != null
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(StudyTimeTestTags.DASHBOARD_ESTIMATE)
                )
            }
        }
    }
}

@Composable
private fun TodaySummary(
    overview: StudyTimeOverview,
    levelTime: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.testTag(StudyTimeTestTags.DASHBOARD_TODAY)) {
        // No "Level 8 · day 9" here: the greeting at the top of the dashboard already says it.
        if (levelTime != null) {
            Text(
                text = levelTime,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                modifier = Modifier.testTag(StudyTimeTestTags.DASHBOARD_LEVEL_TIME)
            )
        }
        if (overview.hasAnyData) {
            Spacer(modifier = Modifier.height(4.dp))
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

internal fun todaysPlanText(
    estimate: QueueEstimate,
    reviewCount: Int,
    lessonsLeftForGoal: Int,
    hasOwnPace: Boolean
): String {
    val parts = buildList {
        if (reviewCount > 0) add("$reviewCount ${if (reviewCount == 1) "review" else "reviews"}")
        if (lessonsLeftForGoal > 0) add("$lessonsLeftForGoal ${if (lessonsLeftForGoal == 1) "lesson" else "lessons"}")
    }
    val pace = if (hasOwnPace) "at your pace" else "at a typical pace"
    return "Today's plan: ${parts.joinToString(" + ")} ≈ ${formatStudyDuration(estimate.totalMs)} $pace"
}
