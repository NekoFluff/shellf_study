package com.crazyfluff.shellfstudy.shared.feature.studytime

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.studytime.LevelStudyTime
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyHeatmap
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyPace
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeAggregator
import kotlin.math.abs

// Pace, time per level and when you study — the "how and when" half of the screen.

@Composable
internal fun PaceCard(pace: StudyPace) {
    SectionCard(title = "Your pace", subtitle = "Per item · last ${StudyTimeAggregator.PACE_SPAN_DAYS} days") {
        Row(modifier = Modifier.fillMaxWidth()) {
            PaceTile(
                label = "Review",
                color = reviewTimeColor(),
                current = pace.reviewMsPerItem,
                previous = pace.previousReviewMsPerItem,
                modifier = Modifier.weight(1f).testTag(StudyTimeTestTags.PACE_REVIEW)
            )
            // Lesson time includes reading the explanations, not just the quiz.
            PaceTile(
                label = "Lesson",
                color = lessonTimeColor(),
                current = pace.lessonMsPerItem,
                previous = pace.previousLessonMsPerItem,
                modifier = Modifier.weight(1f).testTag(StudyTimeTestTags.PACE_LESSON)
            )
        }
    }
}

@Composable
private fun PaceTile(label: String, color: Color, current: Long?, previous: Long?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        ChartLegendItem(color, label)
        Text(text = current?.let(::formatPace) ?: "–", style = MaterialTheme.typography.headlineSmall)
        val trend = if (current == null) "No data yet" else paceTrend(current, previous)
        if (trend != null) {
            Text(
                text = trend,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** "▼ 1.2s faster" against the span before, or null when there's nothing to compare yet. */
internal fun paceTrend(current: Long?, previous: Long?): String? {
    if (current == null || previous == null) return null
    val difference = current - previous
    return when {
        difference == 0L -> "No change"
        difference < 0L -> "▼ ${formatPace(abs(difference))} faster"
        else -> "▲ ${formatPace(difference)} slower"
    }
}

@Composable
internal fun LevelsCard(levels: List<LevelStudyTime>, currentLevel: Int?) {
    SectionCard(title = "Time per level", subtitle = "Since tracking began") {
        LevelStudyTimeList(levels = levels, currentLevel = currentLevel)
        levels.firstOrNull { it.level == currentLevel }?.let { current ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Level ${current.level} so far: ${formatStudyDuration(current.split.totalMs)} over " +
                    "${current.activeDays} ${if (current.activeDays == 1) "day" else "days"}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
internal fun HeatmapCard(heatmap: StudyHeatmap) {
    SectionCard(title = "When you study", subtitle = "Last ${StudyTimeAggregator.HEATMAP_DAYS} days") {
        heatmapCaption(heatmap)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(StudyTimeTestTags.HEATMAP_CAPTION)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        StudyHeatmapGrid(heatmap = heatmap, modifier = Modifier.fillMaxWidth())
    }
}

internal fun heatmapCaption(heatmap: StudyHeatmap): String? {
    val part = heatmap.peakPartOfDay ?: return null
    val days = if (heatmap.prefersWeekends == true) "weekends" else "weekdays"
    return "You study most ${part.phrase}, and more on $days."
}
