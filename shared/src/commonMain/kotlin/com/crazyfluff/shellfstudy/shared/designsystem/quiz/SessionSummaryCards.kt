package com.crazyfluff.shellfstudy.shared.designsystem.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.designsystem.theme.EinkStageColors
import com.crazyfluff.shellfstudy.shared.designsystem.theme.SrsStageColors
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.themeAwareColor
import com.crazyfluff.shellfstudy.shared.network.SubjectType

/** One row of a [SessionSlowestAnswersCard] — a single graded answer, already reduced to display-ready fields. */
data class SessionAnswerRow(
    val label: String,
    val typeLabel: String,
    val elapsedMs: Long,
    val isCorrect: Boolean,
    val subjectId: Long,
    val subjectType: SubjectType
)

/** One chip of a [SessionMissedItemsCard]. */
data class SessionMissedItemRow(val label: String, val subjectId: Long, val subjectType: SubjectType)

@Composable
fun SessionOverviewCard(
    itemsLabel: String,
    itemsCount: Int,
    correctFirstTry: Int,
    cardTestTag: String,
    itemsTextTestTag: String,
    correctFirstTryTextTestTag: String,
    modifier: Modifier = Modifier
) {
    val accuracyPercent = if (itemsCount == 0) 0 else correctFirstTry * 100 / itemsCount
    Card(modifier = modifier.testTag(cardTestTag)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { accuracyPercent / 100f },
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp,
                    color = themeAwareColor(SrsStageColors.Enlightened, EinkStageColors.Enlightened),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text("$accuracyPercent%", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "$itemsLabel: $itemsCount",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag(itemsTextTestTag)
                )
                Text(
                    text = "Correct on first try: $correctFirstTry of $itemsCount ($accuracyPercent%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(correctFirstTryTextTestTag)
                )
            }
        }
    }
}

@Composable
fun SessionTimingCard(
    totalElapsedMs: Long,
    averageTimePerItemMs: Long,
    averageLabel: String,
    cardTestTag: String,
    totalTimeTestTag: String,
    averageTimeTestTag: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.testTag(cardTestTag)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Timing", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Total time: ${formatDuration(totalElapsedMs)}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(totalTimeTestTag)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$averageLabel: ${formatDuration(averageTimePerItemMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(averageTimeTestTag)
            )
        }
    }
}

@Composable
fun SessionSlowestAnswersCard(
    answers: List<SessionAnswerRow>,
    onSubjectClick: (Long) -> Unit,
    cardTestTag: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.testTag(cardTestTag)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Slowest answers", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            answers.forEach { answer ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onSubjectClick(answer.subjectId) }
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(subjectColor(answer.subjectType))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${answer.label} (${answer.typeLabel}) — ${formatDuration(answer.elapsedMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (answer.isCorrect) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = if (answer.isCorrect) "Correct" else "Incorrect",
                        tint = if (answer.isCorrect) {
                            themeAwareColor(SrsStageColors.Enlightened, EinkStageColors.Enlightened)
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SessionMissedItemsCard(
    items: List<SessionMissedItemRow>,
    onSubjectClick: (Long) -> Unit,
    cardTestTag: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.testTag(cardTestTag)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Missed items", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    val color = subjectColor(item.subjectType)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSubjectClick(item.subjectId) }
                            .background(color.copy(alpha = 0.12f))
                            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(text = item.label, color = color, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "%d:%02d".format(minutes, seconds) else "${seconds}s"
}
