package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.model.SubjectAssignmentStats
import com.crazyfluff.shellfstudy.shared.data.model.SubjectReviewStats
import com.crazyfluff.shellfstudy.shared.data.model.formatHourOfDay
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char

object SubjectStatsTestTags {
    const val SECTION = "subject_stats_section"
}

// Includes the year: unlocked/started/passed/burned dates can span years for anything but a
// brand-new account, and "Jan 3" alone doesn't say which Jan 3.
private val DATE_FORMATTER = kotlinx.datetime.LocalDate.Format {
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    char(' ')
    day(Padding.NONE)
    chars(", ")
    year()
}

private fun formatDate(instant: Instant): String =
    DATE_FORMATTER.format(instant.toLocalDateTime(TimeZone.currentSystemDefault()).date)

private fun formatDateTime(instant: Instant): String = "${formatDate(instant)}, ${formatHourOfDay(instant)}"

/**
 * Accuracy/streak split by question type (WaniKani tracks meaning and reading separately),
 * plus a milestone list — next/last review up top since those change on every review, then the
 * fixed unlocked/started/passed/burned dates below a divider. Two cards side by side reads fine
 * even at phone width (this is the layout used inside the subject detail sheet, viewed primarily
 * on mobile).
 */
@Composable
fun SubjectStatsSection(
    assignmentStats: SubjectAssignmentStats,
    reviewStats: SubjectReviewStats?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().testTag(SubjectStatsTestTags.SECTION), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "Stats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            QuestionTypeStatsCard(
                title = "Meaning",
                correct = reviewStats?.meaningCorrect,
                incorrect = reviewStats?.meaningIncorrect,
                accuracyPercent = reviewStats?.meaningAccuracyPercent,
                currentStreak = reviewStats?.meaningCurrentStreak,
                bestStreak = reviewStats?.meaningMaxStreak,
                modifier = Modifier.weight(1f)
            )
            QuestionTypeStatsCard(
                title = "Reading",
                correct = reviewStats?.readingCorrect,
                incorrect = reviewStats?.readingIncorrect,
                accuracyPercent = reviewStats?.readingAccuracyPercent,
                currentStreak = reviewStats?.readingCurrentStreak,
                bestStreak = reviewStats?.readingMaxStreak,
                modifier = Modifier.weight(1f)
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatRow(label = "Next review", value = nextReviewText(assignmentStats.nextReviewAt))
                if (reviewStats?.lastReviewedAt != null) {
                    StatRow(label = "Last reviewed", value = formatDateTime(reviewStats.lastReviewedAt))
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                StatRow(label = "Unlocked", value = assignmentStats.unlockedAt?.let(::formatDate) ?: "Not yet")
                StatRow(label = "Started", value = assignmentStats.startedAt?.let(::formatDate) ?: "Not yet")
                StatRow(label = "Passed", value = assignmentStats.passedAt?.let(::formatDate) ?: "Not yet")
                StatRow(label = "Burned", value = assignmentStats.burnedAt?.let(::formatDate) ?: "Not yet")
            }
        }
    }
}

private fun nextReviewText(nextReviewAt: Instant?): String = when {
    nextReviewAt == null -> "—"
    nextReviewAt <= Clock.System.now() -> "Available now"
    else -> formatDateTime(nextReviewAt)
}

@Composable
private fun QuestionTypeStatsCard(
    title: String,
    correct: Int?,
    incorrect: Int?,
    accuracyPercent: Int?,
    currentStreak: Int?,
    bestStreak: Int?,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (accuracyPercent != null && correct != null && incorrect != null && currentStreak != null && bestStreak != null) {
                Text(text = "$accuracyPercent%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "$correct correct, $incorrect wrong",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Streak $currentStreak (best $bestStreak)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(text = "No reviews yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
