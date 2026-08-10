package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.core.data.model.ReviewsCompletedStats
import com.crazyfluff.shellfstudy.core.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.core.designsystem.theme.SrsStageColors

object ReviewsCompletedTestTags {
    const val CARD = "reviews_completed_card"
    const val TODAY = "reviews_completed_today"
}

@Composable
fun ReviewsCompletedCard(stats: ReviewsCompletedStats?, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().testTag(ReviewsCompletedTestTags.CARD)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = SrsStageColors.Guru
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.testTag(ReviewsCompletedTestTags.TODAY)) {
                Text(
                    text = stats?.today?.toString() ?: "—",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(text = "Reviews completed today", style = MaterialTheme.typography.bodyMedium)
                Text(
                    // A real limitation, not a bug: WaniKani's API no longer exposes individual
                    // review history, so this can only count reviews submitted through this app —
                    // reviews done on wanikani.com or another client won't show up here.
                    text = "Counts reviews done in this app only",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReviewsCompletedCardPreview() {
    ShellfStudyTheme {
        ReviewsCompletedCard(stats = ReviewsCompletedStats(today = 12, last7Days = 84, allTime = 1502))
    }
}
