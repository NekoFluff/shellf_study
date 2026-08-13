package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.core.data.model.CompletionProjection
import com.crazyfluff.shellfstudy.core.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.core.designsystem.theme.SubjectTypeColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object CompletionProjectionTestTags {
    const val CARD = "completion_projection_card"
    const val PROGRESS = "completion_projection_progress"
    const val SUMMARY_TEXT = "completion_projection_summary"
}

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy")

@Composable
fun CompletionProjectionCard(projection: CompletionProjection?, modifier: Modifier = Modifier) {
    if (projection == null) return
    Card(modifier = modifier.fillMaxWidth().testTag(CompletionProjectionTestTags.CARD)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Completion Time", style = MaterialTheme.typography.titleMedium)

            if (projection.itemsRemaining <= 0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Icon(imageVector = Icons.Filled.Celebration, contentDescription = null, tint = SubjectTypeColors.Vocabulary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "You've started every item in the library!",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag(CompletionProjectionTestTags.SUMMARY_TEXT)
                    )
                }
            } else {
                val fraction = if (projection.totalItems > 0) {
                    (projection.itemsSeen.toFloat() / projection.totalItems).coerceIn(0f, 1f)
                } else {
                    0f
                }

                Row(modifier = Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.size(72.dp).testTag(CompletionProjectionTestTags.PROGRESS),
                            strokeWidth = 7.dp,
                            color = SubjectTypeColors.Vocabulary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(text = "${(fraction * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.testTag(CompletionProjectionTestTags.SUMMARY_TEXT)) {
                        if (projection.dailyPace > 0) {
                            Text(
                                text = projection.projectedCompletionDate.format(DATE_FORMATTER),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "${projection.daysRemaining} days · ${projection.dailyPace} items/day",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "${projection.itemsRemaining} items left to start",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Keep up your pace to get a finish estimate",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CompletionProjectionCardPreview() {
    ShellfStudyTheme {
        CompletionProjectionCard(
            projection = CompletionProjection(
                totalItems = 9000, itemsSeen = 1200, dailyPace = 15,
                daysRemaining = 520, projectedCompletionDate = LocalDate.now().plusDays(520)
            )
        )
    }
}
