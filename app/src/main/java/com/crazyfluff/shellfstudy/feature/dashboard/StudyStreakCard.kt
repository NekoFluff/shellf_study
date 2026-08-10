package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.core.data.model.StudyStreak
import com.crazyfluff.shellfstudy.core.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.core.designsystem.theme.SrsStageColors

object StudyStreakTestTags {
    const val CARD = "study_streak_card"
    const val STREAK_COUNT = "study_streak_count"
    const val WEEK_ROW = "study_streak_week_row"
}

@Composable
fun StudyStreakCard(streak: StudyStreak?, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().testTag(StudyStreakTestTags.CARD)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Study Streak", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (streak == null || (streak.currentStreakDays == 0 && !streak.isActiveToday)) {
                Text(
                    text = "Complete a review to start your streak!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = SrsStageColors.Apprentice
                    )
                    Text(
                        text = "${streak.currentStreakDays} day streak",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.testTag(StudyStreakTestTags.STREAK_COUNT)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                WeekActivityRow(activeDays = streak.activeDaysLast7)
            }
        }
    }
}

@Composable
private fun WeekActivityRow(activeDays: List<Boolean>) {
    val activeColor = SrsStageColors.Apprentice
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant
    Row(modifier = Modifier.testTag(StudyStreakTestTags.WEEK_ROW)) {
        activeDays.forEach { active ->
            Canvas(modifier = Modifier.size(16.dp).padding(2.dp)) {
                if (active) {
                    drawCircle(color = activeColor)
                } else {
                    drawCircle(color = inactiveColor, style = Stroke(width = 2f))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StudyStreakCardPreview() {
    ShellfStudyTheme {
        StudyStreakCard(
            streak = StudyStreak(
                currentStreakDays = 12,
                longestStreakDays = 30,
                isActiveToday = true,
                activeDaysLast7 = listOf(true, true, false, true, true, true, true)
            )
        )
    }
}
