package com.crazyfluff.shellfstudy.core.designsystem.writing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.designsystem.strokeorder.StrokeOrderUiState

/**
 * Ungraded, ephemeral stylus practice for a subject's glyph, slotted right next to the reference
 * [com.crazyfluff.shellfstudy.core.designsystem.strokeorder.StrokeOrderSection] it's meant to
 * pair with. Self-assessed only: never submitted to WaniKani (its API has no writing-grade
 * concept) and never persisted (no self-study/backup layer exists yet). Renders nothing unless
 * [strokeOrder] is [StrokeOrderUiState.Available] — the same gate as the reference diagram, so
 * subjects with no KanjiVG data (most radicals, all multi-character vocabulary) get neither
 * section.
 */
@Composable
fun WritingPracticeSection(
    strokeOrder: StrokeOrderUiState,
    resetKey: Any?,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    if (strokeOrder !is StrokeOrderUiState.Available) return

    var expanded by rememberSaveable(resetKey) { mutableStateOf(initiallyExpanded) }
    val chevronRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "writing_practice_chevron")

    Column(
        modifier = modifier.testTag(WritingPracticeTestTags.SECTION),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = if (expanded) "Hide writing practice" else "Practice writing") {
                    expanded = !expanded
                }
                .testTag(WritingPracticeTestTags.EXPAND_TOGGLE)
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = "Practice writing",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(chevronRotation)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val practiceState = rememberWritingPracticeState(resetKey)
            var showReference by rememberSaveable(resetKey) { mutableStateOf(true) }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    WritingCanvas(
                        completedStrokes = practiceState.completedStrokes,
                        currentStrokePoints = practiceState.currentStrokePoints,
                        referenceStrokes = strokeOrder.strokes,
                        showReference = showReference,
                        onStrokeStart = practiceState::onDragStart,
                        onStrokeDrag = practiceState::onDrag,
                        onStrokeEnd = practiceState::onDragEnd
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, alignment = Alignment.CenterHorizontally)
                ) {
                    FilledTonalIconButton(
                        onClick = { showReference = !showReference },
                        modifier = Modifier.testTag(WritingPracticeTestTags.REFERENCE_TOGGLE)
                    ) {
                        Icon(
                            imageVector = if (showReference) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showReference) "Hide reference glyph" else "Show reference glyph"
                        )
                    }
                    FilledTonalIconButton(
                        onClick = practiceState::undoLast,
                        enabled = practiceState.completedStrokes.isNotEmpty(),
                        modifier = Modifier.testTag(WritingPracticeTestTags.UNDO_BUTTON)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo last stroke")
                    }
                    FilledTonalIconButton(
                        onClick = practiceState::clear,
                        enabled = practiceState.completedStrokes.isNotEmpty() || practiceState.currentStrokePoints.isNotEmpty(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.testTag(WritingPracticeTestTags.CLEAR_BUTTON)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear practice")
                    }
                }
            }
        }
    }
}
