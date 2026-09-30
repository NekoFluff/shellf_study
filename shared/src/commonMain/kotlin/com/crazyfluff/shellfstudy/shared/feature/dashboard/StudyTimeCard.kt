package com.crazyfluff.shellfstudy.shared.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeOverview
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeSplit
import com.crazyfluff.shellfstudy.shared.designsystem.theme.StreakColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.StreakColorDark
import com.crazyfluff.shellfstudy.shared.designsystem.theme.themeAwareColor
import com.crazyfluff.shellfstudy.shared.feature.studytime.StudyTimeBarChart
import com.crazyfluff.shellfstudy.shared.feature.studytime.StudyTimeTestTags
import com.crazyfluff.shellfstudy.shared.feature.studytime.emptyCellColor
import com.crazyfluff.shellfstudy.shared.feature.studytime.formatStudyDuration
import com.crazyfluff.shellfstudy.shared.feature.studytime.lessonTimeColor
import com.crazyfluff.shellfstudy.shared.feature.studytime.reviewTimeColor

/**
 * A slim strip: today's study time against the daily goal, a bar toward it split into reviews and
 * lessons, the run of days studied, and the past week as a sparkline. Tapping it opens the full study-time
 * screen, which has the breakdown this leaves out.
 */
@Composable
fun StudyTimeCard(
    overview: StudyTimeOverview,
    /** Consecutive days with a review or lesson, not [StudyTimeOverview.goalStreakDays]: people read
     *  a streak as "days I studied", and a goal streak left them at nothing after two days of reviews. */
    studyStreakDays: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth().testTag(StudyTimeTestTags.DASHBOARD_CARD)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Study Time",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TodayLine(overview, studyStreakDays)
                Spacer(modifier = Modifier.height(6.dp))
                SplitGoalBar(today = overview.today, goalMs = overview.goalMs)
            }
            if (overview.hasAnyData) {
                Spacer(modifier = Modifier.width(16.dp))
                // Bottom-aligned so the bars stand on the same line as the goal bar; centred, the
                // chart's floor floated between the text and the bar.
                StudyTimeBarChart(
                    buckets = overview.lastSevenDays,
                    selectedIndex = null,
                    onSelect = {},
                    goalLineMs = overview.goalMs,
                    goalLineDash = 3.dp,
                    height = 30.dp,
                    highlightIndex = overview.lastSevenDays.lastIndex,
                    selectable = false,
                    modifier = Modifier.width(64.dp).align(Alignment.Bottom)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** "21m of 30m today", or an invitation before anything is recorded, with the streak at the end. */
@Composable
private fun TodayLine(overview: StudyTimeOverview, studyStreakDays: Int) {
    Row {
        Text(
            text = formatStudyDuration(overview.today.totalMs),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.alignByBaseline().testTag(StudyTimeTestTags.DASHBOARD_TODAY_TOTAL)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = todaySuffix(overview),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.alignByBaseline()
        )
        Spacer(modifier = Modifier.weight(1f))
        if (studyStreakDays >= 1) {
            StudyStreak(days = studyStreakDays, modifier = Modifier.alignByBaseline())
        }
    }
}

/** A small orange pill: a flame and the day count. */
@Composable
private fun StudyStreak(days: Int, modifier: Modifier = Modifier) {
    val accent = streakColor()
    Surface(
        shape = CircleShape,
        color = accent.copy(alpha = STREAK_PILL_ALPHA),
        modifier = modifier.testTag(StudyTimeTestTags.DASHBOARD_STREAK)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalFireDepartment,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = days.toString(), style = MaterialTheme.typography.labelMedium, color = accent)
        }
    }
}

/** Deep orange on the light card, a lighter orange on the dark one, black on e-ink. */
@Composable
private fun streakColor(): Color = themeAwareColor(
    default = StreakColor,
    einkValue = Color.Black,
    darkValue = StreakColorDark
)

private const val STREAK_PILL_ALPHA = 0.16f

/** Reviews then lessons, each as its share of the goal. Past the goal the bar is full and keeps
 *  the split, scaled down together. */
@Composable
private fun SplitGoalBar(today: StudyTimeSplit, goalMs: Long) {
    val reviewColor = reviewTimeColor()
    val lessonColor = lessonTimeColor()
    val scale = maxOf(goalMs, today.totalMs, 1L).toFloat()
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(emptyCellColor())
    ) {
        val reviewWidth = size.width * (today.reviewMs / scale)
        val lessonWidth = size.width * (today.lessonMs / scale)
        drawRect(reviewColor, Offset.Zero, Size(reviewWidth, size.height))
        drawRect(lessonColor, Offset(reviewWidth, 0f), Size(lessonWidth, size.height))
    }
}

/** What follows today's total: "of 30m today", "today" without a goal, and before any session an
 *  invitation instead. */
internal fun todaySuffix(overview: StudyTimeOverview): String = when {
    !overview.hasAnyData -> "Starts with your next session"
    overview.goalMs > 0L -> "of ${formatStudyDuration(overview.goalMs)} today"
    else -> "today"
}
