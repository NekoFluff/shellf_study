package com.crazyfluff.shellfstudy.shared.feature.studytime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeSplit
import com.crazyfluff.shellfstudy.shared.designsystem.theme.StreakColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.StreakColorDark
import com.crazyfluff.shellfstudy.shared.designsystem.theme.kanjiColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.radicalColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.themeAwareColor
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.isoDayNumber
import kotlin.time.Duration.Companion.milliseconds
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.format.Padding

/** Lessons take the Lessons card's blue and reviews the Reviews card's pink, so the colours mean
 *  the same thing on every screen. */
@Composable fun lessonTimeColor(): Color = radicalColor()

@Composable fun reviewTimeColor(): Color = kanjiColor()

/** "Nothing here" for heatmap cells, bar stubs and bar tracks. A tint of the content colour rather
 *  than surfaceVariant, which in the light scheme is the card's own colour and vanishes on it. */
@Composable internal fun emptyCellColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

/** A total for people to read: "45s", "23m", "1h 5m", "12h". */
fun formatStudyDuration(ms: Long): String {
    if (ms <= 0L) return "0m"
    return ms.milliseconds.toComponents { hours, minutes, seconds, _ ->
        when {
            hours == 0L && minutes == 0 -> "${seconds}s"
            hours == 0L -> "${minutes}m"
            minutes == 0 -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }
}

private val TENTH_OF_A_SECOND = 100.milliseconds

/** A total short enough to sit on a chart column: "45m" under an hour, "4.3h" under ten, "13h" above. */
fun formatCompactDuration(ms: Long): String {
    val duration = ms.milliseconds
    val tenthsOfAnHour = (duration / TENTH_OF_AN_HOUR).roundToLong()
    return when {
        duration < 1.hours -> "${duration.inWholeMinutes}m"
        tenthsOfAnHour < TENTHS_IN_TEN_HOURS && tenthsOfAnHour % TENTHS_PER_HOUR != 0L ->
            "${tenthsOfAnHour / TENTHS_PER_HOUR}.${tenthsOfAnHour % TENTHS_PER_HOUR}h"
        else -> "${(duration / 1.hours).roundToLong()}h"
    }
}

private val TENTH_OF_AN_HOUR = 6.minutes
private const val TENTHS_PER_HOUR = 10L
private const val TENTHS_IN_TEN_HOURS = 100L

/** A per-item pace: "6.2s" under a minute, "2m 5s" above. One decimal via whole tenths, since
 *  commonMain has no String.format. */
fun formatPace(ms: Long): String {
    val pace = ms.milliseconds
    if (pace < 1.minutes) {
        val tenths = ((pace + TENTH_OF_A_SECOND / 2) / TENTH_OF_A_SECOND).toLong()
        val tenthsPerSecond = (1.seconds / TENTH_OF_A_SECOND).toLong()
        return "${tenths / tenthsPerSecond}.${tenths % tenthsPerSecond}s"
    }
    return (pace + 0.5.seconds).toComponents { minutes, seconds, _ ->
        if (seconds == 0) "${minutes}m" else "${minutes}m ${seconds}s"
    }
}

/** "12,345" — commonMain has no locale number formatting. */
internal fun formatCount(count: Long): String =
    count.toString().reversed().chunked(DIGITS_PER_GROUP).joinToString(",").reversed()

private const val DIGITS_PER_GROUP = 3

internal val SHORT_DATE_FORMAT = LocalDate.Format {
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    chars(" ")
    day(Padding.NONE)
}

internal fun DayOfWeek.shortLabel(): String = DayOfWeekNames.ENGLISH_ABBREVIATED.names[isoDayNumber - 1]

/** Progress toward the daily goal, split like the dashboard's goal bar: reviews then lessons, each
 *  as its share of the goal, in the same colours as the legend under it. Past the goal the ring is
 *  full and keeps the split, scaled down together. */
@Composable
fun StudyGoalRing(
    today: StudyTimeSplit,
    goalMs: Long,
    centerText: String,
    modifier: Modifier = Modifier,
    /** A smaller second line under [centerText], e.g. the goal it counts toward. */
    subText: String? = null,
    size: Dp = 72.dp,
    strokeWidth: Dp = 7.dp
) {
    val reviewColor = reviewTimeColor()
    val lessonColor = lessonTimeColor()
    val trackColor = emptyCellColor()
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)
            val stroke = Stroke(width = strokePx)
            val scale = maxOf(goalMs, today.totalMs, 1L).toFloat()
            val reviewSweep = FULL_CIRCLE * (today.reviewMs / scale)
            val lessonSweep = FULL_CIRCLE * (today.lessonMs / scale)
            fun arc(color: Color, startAngle: Float, sweep: Float) =
                drawArc(color, startAngle, sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
            arc(trackColor, 0f, FULL_CIRCLE)
            arc(reviewColor, TWELVE_O_CLOCK, reviewSweep)
            arc(lessonColor, TWELVE_O_CLOCK + reviewSweep, lessonSweep)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = centerText, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            if (subText != null) {
                Text(
                    text = subText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

private const val FULL_CIRCLE = 360f
private const val TWELVE_O_CLOCK = -90f

/** The study streak: a flame and the day count in a small orange pill. The dashboard and the
 *  study-time screen share it so the flame always reads the same way. */
@Composable
fun StudyStreakPill(
    days: Int,
    modifier: Modifier = Modifier,
    iconSize: Dp = 12.dp,
    textStyle: TextStyle = MaterialTheme.typography.labelMedium
) {
    val accent = streakColor()
    Surface(shape = CircleShape, color = accent.copy(alpha = STREAK_PILL_ALPHA), modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = iconSize * STREAK_PILL_PADDING_RATIO, vertical = 1.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalFireDepartment,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = days.toString(), style = textStyle, color = accent)
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
private const val STREAK_PILL_PADDING_RATIO = 7f / 12f

object StudyTimeTestTags {
    const val DASHBOARD_CARD = "study_time_dashboard_card"
    const val DASHBOARD_TODAY_TOTAL = "study_time_dashboard_today_total"
    const val DASHBOARD_STREAK = "study_time_dashboard_streak"
    const val EMPTY_STATE = "study_time_empty_state"
    const val TODAY_TOTAL = "study_time_today_total"
    const val STUDY_STREAK = "study_time_study_streak"
    const val BAR_CHART = "study_time_bar_chart"
    const val SELECTED_BAR = "study_time_selected_bar"
    const val SELECTED_BAR_REVIEWS = "study_time_selected_bar_reviews"
    const val SELECTED_BAR_LESSONS = "study_time_selected_bar_lessons"
    const val PERIOD_TOTAL = "study_time_period_total"
    const val PERIOD_REVIEW_ITEMS = "study_time_period_review_items"
    const val PERIOD_LESSON_ITEMS = "study_time_period_lesson_items"
    const val PACE_REVIEW = "study_time_pace_review"
    const val PACE_LESSON = "study_time_pace_lesson"
    const val LEVELS_TOTAL = "study_time_levels_total"
    const val LEVEL_CHART = "study_time_level_chart"
    const val HEATMAP_CAPTION = "study_time_heatmap_caption"
    const val LIFETIME_TOTAL = "study_time_lifetime_total"
    const val LIFETIME_REVIEWS = "study_time_lifetime_reviews"
    const val LIFETIME_LESSONS = "study_time_lifetime_lessons"
    const val BACK_BUTTON = "study_time_back_button"

}
