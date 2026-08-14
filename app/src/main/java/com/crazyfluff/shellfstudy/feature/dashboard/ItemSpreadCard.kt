package com.crazyfluff.shellfstudy.feature.dashboard

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.model.ItemSpread
import com.crazyfluff.shellfstudy.shared.data.model.ItemSpreadBucket
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.core.designsystem.components.SegmentedBar
import com.crazyfluff.shellfstudy.shared.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.shared.designsystem.theme.srsStageColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.shared.network.SubjectType

object ItemSpreadTestTags {
    const val CARD = "item_spread_card"
    const val BAR = "item_spread_bar"
    const val EMPTY_STATE = "item_spread_empty_state"
    fun typeBar(bucket: ItemSpreadBucket) = "item_spread_type_bar_${bucket.name.lowercase()}"
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
                    StatRow(segment, total = total, countsByType = spread?.countsByType?.get(segment.bucket).orEmpty())
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

/** A stage's composition by subject type (Radical/Kanji/Vocabulary), same visual language as
 *  [ItemSpreadBar] but thinner, sitting under that stage's legend row. */
@Composable
private fun TypeMiniBar(bucket: ItemSpreadBucket, countsByType: Map<SubjectType, Int>, modifier: Modifier = Modifier) {
    val typeSegments = listOf(SubjectType.RADICAL, SubjectType.KANJI, SubjectType.VOCABULARY)
        .map { type -> subjectColor(type) to (countsByType[type] ?: 0) }
    SegmentedBar(
        segments = typeSegments,
        modifier = modifier.testTag(ItemSpreadTestTags.typeBar(bucket)),
        height = 6.dp
    )
}

@Composable
private fun StatRow(segment: SpreadSegment, total: Int, countsByType: Map<SubjectType, Int>) {
    val percent = if (total > 0) segment.count * 100 / total else 0
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(segment.color))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "${segment.label}: ${segment.count} ($percent%)", style = MaterialTheme.typography.bodySmall)
        }
        if (segment.count > 0) {
            Spacer(modifier = Modifier.height(2.dp))
            TypeMiniBar(
                bucket = segment.bucket,
                countsByType = countsByType,
                modifier = Modifier.padding(start = 18.dp)
            )
        }
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
                burnedCount = 200,
                countsByType = mapOf(
                    ItemSpreadBucket.LOCKED to mapOf(SubjectType.RADICAL to 50, SubjectType.KANJI to 200, SubjectType.VOCABULARY to 250),
                    ItemSpreadBucket.APPRENTICE to mapOf(SubjectType.RADICAL to 10, SubjectType.KANJI to 30, SubjectType.VOCABULARY to 40),
                    ItemSpreadBucket.GURU to mapOf(SubjectType.RADICAL to 20, SubjectType.KANJI to 40, SubjectType.VOCABULARY to 60),
                    ItemSpreadBucket.MASTER to mapOf(SubjectType.RADICAL to 5, SubjectType.KANJI to 15, SubjectType.VOCABULARY to 20),
                    ItemSpreadBucket.ENLIGHTENED to mapOf(SubjectType.RADICAL to 5, SubjectType.KANJI to 10, SubjectType.VOCABULARY to 15),
                    ItemSpreadBucket.BURNED to mapOf(SubjectType.RADICAL to 40, SubjectType.KANJI to 70, SubjectType.VOCABULARY to 90)
                )
            )
        )
    }
}
