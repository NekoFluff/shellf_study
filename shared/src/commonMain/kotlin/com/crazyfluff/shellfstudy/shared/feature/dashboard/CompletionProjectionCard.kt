package com.crazyfluff.shellfstudy.shared.feature.dashboard

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
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.model.CompletionProjection
import com.crazyfluff.shellfstudy.shared.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.shared.designsystem.theme.vocabularyColor
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

object CompletionProjectionTestTags {
    const val CARD = "completion_projection_card"
    const val PROGRESS = "completion_projection_progress"
    const val SUMMARY_TEXT = "completion_projection_summary"
}

private val DATE_FORMATTER = kotlinx.datetime.LocalDate.Format {
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    char(' ')
    day(Padding.NONE)
    chars(", ")
    year()
}

@Composable
fun CompletionProjectionCard(projection: CompletionProjection, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().testTag(CompletionProjectionTestTags.CARD)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Completion Date", style = MaterialTheme.typography.titleMedium)

            if (projection.itemsRemaining <= 0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Icon(imageVector = Icons.Filled.Celebration, contentDescription = null, tint = vocabularyColor())
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
                            color = vocabularyColor(),
                            trackColor = MaterialTheme.colorScheme.outline
                        )
                        Text(text = "${(fraction * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.testTag(CompletionProjectionTestTags.SUMMARY_TEXT)) {
                        if (projection.dailyPace > 0) {
                            Text(
                                text = DATE_FORMATTER.format(projection.projectedCompletionDate),
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
