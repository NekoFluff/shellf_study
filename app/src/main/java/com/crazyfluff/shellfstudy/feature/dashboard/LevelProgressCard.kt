package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.core.data.model.LevelItem
import com.crazyfluff.shellfstudy.core.data.model.LevelProgress
import com.crazyfluff.shellfstudy.core.data.model.SubjectTypeProgress
import com.crazyfluff.shellfstudy.core.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.core.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.core.designsystem.theme.subjectTypeLabel
import com.crazyfluff.shellfstudy.core.network.SubjectType

object LevelProgressTestTags {
    const val CARD = "level_progress_card"
    const val ROW_PREFIX = "level_progress_row_"
    const val PREV_LEVEL_BUTTON = "level_progress_prev_button"
    const val NEXT_LEVEL_BUTTON = "level_progress_next_button"
    const val EXPAND_TOGGLE_BUTTON = "level_progress_expand_toggle_button"
    const val DETAIL_PREFIX = "level_progress_detail_"
    const val ITEM_CHIP_PREFIX = "level_progress_item_"
}

@Composable
fun LevelProgressCard(
    progress: LevelProgress?,
    maxLevel: Int? = null,
    onLevelChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (progress == null) return
    var expanded by remember { mutableStateOf(false) }

    // Deliberately not a whole-card onClick: with the level-arrow IconButtons already living
    // inside it, a second nested clickable spanning the same area makes hit-testing (and
    // accessibility focus) ambiguous. A dedicated chevron button keeps "expand" unambiguous.
    Card(modifier = modifier.fillMaxWidth().testTag(LevelProgressTestTags.CARD)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = { onLevelChange(progress.level - 1) },
                    enabled = progress.level > 1,
                    modifier = Modifier.size(32.dp).testTag(LevelProgressTestTags.PREV_LEVEL_BUTTON)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous level")
                }
                Text(
                    text = "Level ${progress.level} Progress",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(
                    onClick = { onLevelChange(progress.level + 1) },
                    enabled = maxLevel == null || progress.level < maxLevel,
                    modifier = Modifier.size(32.dp).testTag(LevelProgressTestTags.NEXT_LEVEL_BUTTON)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next level")
                }
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(32.dp).testTag(LevelProgressTestTags.EXPAND_TOGGLE_BUTTON)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Show less" else "Show more"
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            progress.breakdown.forEach { entry ->
                SubjectTypeProgressRow(entry, showDetail = expanded)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SubjectTypeProgressRow(entry: SubjectTypeProgress, showDetail: Boolean) {
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
            color = subjectColor(entry.subjectType),
            drawStopIndicator = {}
        )
        AnimatedVisibility(
            visible = showDetail && entry.items.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().testTag(LevelProgressTestTags.DETAIL_PREFIX + entry.subjectType.name)
                ) {
                    entry.items.forEach { item -> LevelItemChip(item) }
                }
            }
        }
    }
}

/** One kanji/radical/vocab item, filled when passed and outlined when not — same idea as WaniKani's own item grids. */
@Composable
private fun LevelItemChip(item: LevelItem) {
    val accent = subjectColor(item.subjectType)
    val backgroundModifier = if (item.passed) {
        Modifier.background(accent, RoundedCornerShape(8.dp))
    } else {
        Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
    }
    Text(
        text = item.display,
        style = MaterialTheme.typography.bodyMedium,
        color = if (item.passed) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .defaultMinSize(minWidth = 36.dp)
            .then(backgroundModifier)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(LevelProgressTestTags.ITEM_CHIP_PREFIX + item.subjectId)
    )
}

@Preview(showBackground = true)
@Composable
private fun LevelProgressCardPreview() {
    ShellfStudyTheme {
        LevelProgressCard(
            progress = LevelProgress(
                level = 12,
                breakdown = listOf(
                    SubjectTypeProgress(
                        subjectType = SubjectType.RADICAL,
                        items = listOf("一", "二", "三", "口", "日").mapIndexed { index, characters ->
                            LevelItem(subjectId = index.toLong(), subjectType = SubjectType.RADICAL, display = characters, passed = true)
                        }
                    ),
                    SubjectTypeProgress(
                        subjectType = SubjectType.KANJI,
                        items = (1..25).map { index ->
                            LevelItem(subjectId = 100L + index, subjectType = SubjectType.KANJI, display = "漢$index", passed = index <= 18)
                        }
                    ),
                    SubjectTypeProgress(
                        subjectType = SubjectType.VOCABULARY,
                        items = (1..10).map { index ->
                            LevelItem(subjectId = 1000L + index, subjectType = SubjectType.VOCABULARY, display = "語彙$index", passed = index <= 4)
                        }
                    )
                )
            ),
            maxLevel = 12
        )
    }
}
