package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.core.data.model.LevelProgress
import com.crazyfluff.shellfstudy.core.data.model.SubjectTypeProgress
import com.crazyfluff.shellfstudy.core.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.core.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.core.designsystem.theme.subjectTypeLabel
import com.crazyfluff.shellfstudy.core.network.SubjectType

object LevelProgressTestTags {
    const val CARD = "level_progress_card"
    const val ROW_PREFIX = "level_progress_row_"
}

@Composable
fun LevelProgressCard(progress: LevelProgress?, modifier: Modifier = Modifier) {
    if (progress == null) return
    Card(modifier = modifier.fillMaxWidth().testTag(LevelProgressTestTags.CARD)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Level ${progress.level} Progress", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            progress.breakdown.forEach { entry ->
                SubjectTypeProgressRow(entry)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SubjectTypeProgressRow(entry: SubjectTypeProgress) {
    val fraction = if (entry.totalCount > 0) (entry.passedCount.toFloat() / entry.totalCount).coerceIn(0f, 1f) else 0f
    Column(modifier = Modifier.testTag(LevelProgressTestTags.ROW_PREFIX + entry.subjectType.name)) {
        Text(
            text = "${subjectTypeLabel(entry.subjectType)}: ${entry.passedCount} / ${entry.totalCount}",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = subjectColor(entry.subjectType)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LevelProgressCardPreview() {
    ShellfStudyTheme {
        LevelProgressCard(
            progress = LevelProgress(
                level = 12,
                breakdown = listOf(
                    SubjectTypeProgress(SubjectType.RADICAL, passedCount = 5, totalCount = 5),
                    SubjectTypeProgress(SubjectType.KANJI, passedCount = 18, totalCount = 25),
                    SubjectTypeProgress(SubjectType.VOCABULARY, passedCount = 40, totalCount = 90)
                )
            )
        )
    }
}
