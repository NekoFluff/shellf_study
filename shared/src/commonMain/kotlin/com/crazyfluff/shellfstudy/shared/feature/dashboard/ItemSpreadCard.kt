package com.crazyfluff.shellfstudy.shared.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.model.ItemSpread
import com.crazyfluff.shellfstudy.shared.data.model.ItemSpreadBucket
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.designsystem.components.SegmentedBar
import com.crazyfluff.shellfstudy.shared.designsystem.theme.srsStageColor

object ItemSpreadTestTags {
    const val CARD = "item_spread_card"
    const val BAR = "item_spread_bar"
    const val EMPTY_STATE = "item_spread_empty_state"
}

private data class SpreadSegment(val bucket: ItemSpreadBucket, val label: String, val count: Int, val color: Color)

@Composable
fun ItemSpreadCard(spread: ItemSpread?, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().testTag(ItemSpreadTestTags.CARD)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Item Spread", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            val segments = listOf(
                SpreadSegment(ItemSpreadBucket.LOCKED, "Locked", spread?.lockedCount ?: 0, srsStageColor(SrsStage.LOCKED)),
                SpreadSegment(ItemSpreadBucket.APPRENTICE, "Apprentice", spread?.apprenticeCount ?: 0, srsStageColor(SrsStage.APPRENTICE_1)),
                SpreadSegment(ItemSpreadBucket.GURU, "Guru", spread?.guruCount ?: 0, srsStageColor(SrsStage.GURU_1)),
                SpreadSegment(ItemSpreadBucket.MASTER, "Master", spread?.masterCount ?: 0, srsStageColor(SrsStage.MASTER)),
                SpreadSegment(ItemSpreadBucket.ENLIGHTENED, "Enlightened", spread?.enlightenedCount ?: 0, srsStageColor(SrsStage.ENLIGHTENED)),
                SpreadSegment(ItemSpreadBucket.BURNED, "Burned", spread?.burnedCount ?: 0, srsStageColor(SrsStage.BURNED))
            )

            ItemSpreadBar(segments)
            Spacer(modifier = Modifier.height(12.dp))

            if (spread != null && spread.totalCount == 0) {
                Text(
                    text = "Start your first lessons to populate this!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(ItemSpreadTestTags.EMPTY_STATE)
                )
            } else {
                val total = spread?.totalCount ?: 0
                segments.filter { it.count > 0 }.forEach { segment ->
                    StatRow(segment, total = total)
                }
            }
        }
    }
}

@Composable
private fun ItemSpreadBar(segments: List<SpreadSegment>) {
    SegmentedBar(
        segments = segments.map { it.color to it.count },
        modifier = Modifier.testTag(ItemSpreadTestTags.BAR),
        height = 24.dp
    )
}

@Composable
private fun StatRow(segment: SpreadSegment, total: Int) {
    val percent = if (total > 0) segment.count * 100 / total else 0
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(segment.color))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "${segment.label}: ${segment.count} ($percent%)", style = MaterialTheme.typography.bodySmall)
    }
}
