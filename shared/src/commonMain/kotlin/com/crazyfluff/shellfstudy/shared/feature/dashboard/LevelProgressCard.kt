package com.crazyfluff.shellfstudy.shared.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.model.LevelItem
import com.crazyfluff.shellfstudy.shared.data.model.LevelProgress
import com.crazyfluff.shellfstudy.shared.data.model.LevelUpProgress
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.data.model.SubjectTypeProgress
import com.crazyfluff.shellfstudy.shared.designsystem.components.SegmentedBar
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.SubjectGlyph
import com.crazyfluff.shellfstudy.shared.designsystem.theme.srsStageColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectTypeLabel
import com.crazyfluff.shellfstudy.shared.network.SubjectType

object LevelProgressTestTags {
    const val CARD = "level_progress_card"
    const val ROW_PREFIX = "level_progress_row_"
    const val PREV_LEVEL_BUTTON = "level_progress_prev_button"
    const val NEXT_LEVEL_BUTTON = "level_progress_next_button"
    const val EXPAND_TOGGLE_BUTTON = "level_progress_expand_toggle_button"
    const val DETAIL_PREFIX = "level_progress_detail_"
    const val ITEM_CHIP_PREFIX = "level_progress_item_"
    const val LEVEL_UP_INDICATOR = "level_progress_level_up_indicator"
    const val LEVEL_UP_THRESHOLD_MARK = "level_progress_level_up_threshold_mark"
}

@Composable
fun LevelProgressCard(
    progress: LevelProgress?,
    maxLevel: Int? = null,
    levelUpProgress: LevelUpProgress? = null,
    onLevelChange: (Int) -> Unit = {},
    onSubjectClick: (Long) -> Unit = {},
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
                // Only the Kanji row, and only while viewing the level the user is actually on
                // (not a past/future level paged to via the arrows above), should claim "you're
                // this close to leveling up" — level-up is a current-level-only concept.
                val relevantLevelUpProgress = if (entry.subjectType == SubjectType.KANJI && progress.level == maxLevel) {
                    levelUpProgress
                } else {
                    null
                }
                SubjectTypeProgressRow(
                    entry,
                    showDetail = expanded,
                    onSubjectClick = onSubjectClick,
                    levelUpProgress = relevantLevelUpProgress
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SubjectTypeProgressRow(
    entry: SubjectTypeProgress,
    showDetail: Boolean,
    onSubjectClick: (Long) -> Unit,
    levelUpProgress: LevelUpProgress? = null
) {
    val accent = subjectColor(entry.subjectType)
    val doneCount = entry.items.count { it.srsStage.raw >= SrsStage.GURU_1.raw }
    val inProgressCount = entry.items.count { it.srsStage != SrsStage.LOCKED && it.srsStage.raw < SrsStage.GURU_1.raw }
    val lockedCount = entry.totalCount - doneCount - inProgressCount
    // entry.totalCount and levelUpProgress.kanjiTotal are both drawn from the same unfiltered
    // (locked-items-included) per-level assignment set, so this mark lines up exactly with the
    // real 90% threshold; "Ready to level up!" below is still the authoritative signal for
    // whether it's actually been met.
    val thresholdFraction = levelUpProgress?.let {
        if (entry.totalCount > 0) (it.requiredCount.toFloat() / entry.totalCount).coerceIn(0f, 1f) else null
    }

    Column(modifier = Modifier.testTag(LevelProgressTestTags.ROW_PREFIX + entry.subjectType.name)) {
        Text(
            text = "${subjectTypeLabel(entry.subjectType)}: ${entry.passedCount} / ${entry.totalCount}",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(4.dp))
        SegmentedBar(
            segments = listOf(
                accent to doneCount,
                accent.copy(alpha = 0.4f) to inProgressCount,
                MaterialTheme.colorScheme.surfaceVariant to lockedCount
            ),
            modifier = if (thresholdFraction != null) {
                Modifier.testTag(LevelProgressTestTags.LEVEL_UP_THRESHOLD_MARK)
            } else {
                Modifier
            },
            height = 6.dp,
            thresholdFraction = thresholdFraction
        )
        if (levelUpProgress?.isLevelUpReady == true) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Ready to level up!",
                style = MaterialTheme.typography.bodySmall,
                color = srsStageColor(SrsStage.GURU_1),
                modifier = Modifier.testTag(LevelProgressTestTags.LEVEL_UP_INDICATOR)
            )
        }
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
                    entry.items.forEach { item -> LevelItemChip(item, onClick = onSubjectClick) }
                }
            }
        }
    }
}

/** One kanji/radical/vocab item. Filled (subject-color background, white content) once passed,
 *  outlined otherwise — same idea as WaniKani's own item grids. The fill/outline gate is
 *  [LevelItem.passed] (ever reached Guru), matching [SubjectTypeProgress.passedCount] above it —
 *  gating on live [LevelItem.srsStage] instead could show an item outlined here while the row
 *  above still counts it as passed, after an SRS demotion. The accent stays [subjectColor] (not
 *  [srsStageColor]) so a row full of items sharing a stage — radicals in particular, which usually
 *  all reach Guru together — doesn't collapse into one indistinguishable color; a not-yet-passed
 *  item's Apprentice sub-stage is instead called out with a small dot row under the glyph.
 *
 *  Content priority is [LevelItem.characters] first, then [LevelItem.characterImageUrl], then
 *  [LevelItem.display]'s slug fallback — WaniKani can supply a decorative character_images SVG
 *  *alongside* a real glyph, not only for glyph-less radicals, so characterImageUrl alone isn't a
 *  reliable "no real character" signal. */
@Composable
private fun LevelItemChip(item: LevelItem, onClick: (Long) -> Unit) {
    val accent = subjectColor(item.subjectType)
    val backgroundModifier = if (item.passed) {
        Modifier.background(accent, RoundedCornerShape(8.dp))
    } else {
        Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
    }
    val chipModifier = Modifier
        .defaultMinSize(minWidth = 48.dp)
        .then(backgroundModifier)
        .clickable { onClick(item.subjectId) }
        .testTag(LevelProgressTestTags.ITEM_CHIP_PREFIX + item.subjectId)

    Box(modifier = chipModifier) {
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            val characters = item.characters
            when {
                characters != null -> Text(
                    text = characters,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (item.passed) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                item.characterImageUrl != null -> SubjectGlyph(
                    characters = null,
                    characterImageUrl = item.characterImageUrl,
                    subjectType = item.subjectType,
                    size = 28.dp
                )
                else -> Text(
                    text = item.display,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (item.passed) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }

        val subStageDots = apprenticeSubStageDots(item.srsStage)
        if (!item.passed && subStageDots != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-3).dp)
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(
                                color = if (index < subStageDots) accent else accent.copy(alpha = 0.25f),
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

/** Dot count (of 4) for an unpassed item's Apprentice sub-stage — null for Locked (nothing studied
 *  yet to show progress on) and for Guru+ (always [LevelItem.passed], never reaches here). */
private fun apprenticeSubStageDots(stage: SrsStage): Int? = when (stage) {
    SrsStage.APPRENTICE_1 -> 1
    SrsStage.APPRENTICE_2 -> 2
    SrsStage.APPRENTICE_3 -> 3
    SrsStage.APPRENTICE_4 -> 4
    else -> null
}
