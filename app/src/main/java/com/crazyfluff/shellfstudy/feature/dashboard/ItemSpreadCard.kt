package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.core.data.model.ItemSpread
import com.crazyfluff.shellfstudy.core.designsystem.theme.EinkStageColors
import com.crazyfluff.shellfstudy.core.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.core.designsystem.theme.SrsStageColors
import com.crazyfluff.shellfstudy.core.designsystem.theme.themeAwareColor

object ItemSpreadTestTags {
    const val CARD = "item_spread_card"
    const val BAR = "item_spread_bar"
    const val EMPTY_STATE = "item_spread_empty_state"
}

private data class SpreadSegment(val label: String, val count: Int, val color: Color)

@Composable
fun ItemSpreadCard(spread: ItemSpread?, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().testTag(ItemSpreadTestTags.CARD)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Item Spread", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            val segments = listOf(
                SpreadSegment("Locked", spread?.lockedCount ?: 0, themeAwareColor(SrsStageColors.Locked, EinkStageColors.Locked)),
                SpreadSegment("Apprentice", spread?.apprenticeCount ?: 0, themeAwareColor(SrsStageColors.Apprentice, EinkStageColors.Apprentice)),
                SpreadSegment("Guru", spread?.guruCount ?: 0, themeAwareColor(SrsStageColors.Guru, EinkStageColors.Guru)),
                SpreadSegment("Master", spread?.masterCount ?: 0, themeAwareColor(SrsStageColors.Master, EinkStageColors.Master)),
                SpreadSegment("Enlightened", spread?.enlightenedCount ?: 0, themeAwareColor(SrsStageColors.Enlightened, EinkStageColors.Enlightened)),
                SpreadSegment("Burned", spread?.burnedCount ?: 0, themeAwareColor(SrsStageColors.Burned, EinkStageColors.Burned))
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
                segments.filter { it.count > 0 }.forEach { segment ->
                    StatRow(segment)
                }
            }
        }
    }
}

@Composable
private fun ItemSpreadBar(segments: List<SpreadSegment>) {
    val total = segments.sumOf { it.count }.coerceAtLeast(1)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .testTag(ItemSpreadTestTags.BAR)
    ) {
        var xOffset = 0f
        segments.forEach { segment ->
            val segmentWidth = size.width * (segment.count.toFloat() / total)
            drawRect(color = segment.color, topLeft = Offset(xOffset, 0f), size = Size(segmentWidth, size.height))
            xOffset += segmentWidth
        }
    }
}

@Composable
private fun StatRow(segment: SpreadSegment) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(segment.color))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "${segment.label}: ${segment.count}", style = MaterialTheme.typography.bodySmall)
    }
}

@Preview(showBackground = true)
@Composable
private fun ItemSpreadCardPreview() {
    ShellfStudyTheme {
        ItemSpreadCard(
            spread = ItemSpread(
                lockedCount = 500,
                apprenticeCount = 80,
                guruCount = 120,
                masterCount = 40,
                enlightenedCount = 30,
                burnedCount = 200
            )
        )
    }
}
