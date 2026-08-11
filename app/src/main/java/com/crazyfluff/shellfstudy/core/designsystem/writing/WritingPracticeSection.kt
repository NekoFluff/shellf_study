package com.crazyfluff.shellfstudy.core.designsystem.writing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.core.designsystem.strokeorder.StrokeOrderUiState

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

    Column(
        modifier = modifier.testTag(WritingPracticeTestTags.SECTION),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Practice writing", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.testTag(WritingPracticeTestTags.EXPAND_TOGGLE)
            ) {
                Text(if (expanded) "Hide" else "Practice")
            }
        }

        if (expanded) {
            val practiceState = rememberWritingPracticeState(resetKey)
            var showReference by rememberSaveable(resetKey) { mutableStateOf(true) }

            WritingCanvas(
                completedStrokes = practiceState.completedStrokes,
                currentStrokePoints = practiceState.currentStrokePoints,
                referenceStrokes = strokeOrder.strokes,
                showReference = showReference,
                onStrokeStart = practiceState::onDragStart,
                onStrokeDrag = practiceState::onDrag,
                onStrokeEnd = practiceState::onDragEnd
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { showReference = !showReference },
                    modifier = Modifier.testTag(WritingPracticeTestTags.REFERENCE_TOGGLE)
                ) {
                    Icon(
                        imageVector = if (showReference) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showReference) "Hide reference glyph" else "Show reference glyph"
                    )
                }
                IconButton(
                    onClick = practiceState::undoLast,
                    enabled = practiceState.completedStrokes.isNotEmpty(),
                    modifier = Modifier.testTag(WritingPracticeTestTags.UNDO_BUTTON)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo last stroke")
                }
                TextButton(
                    onClick = practiceState::clear,
                    modifier = Modifier.testTag(WritingPracticeTestTags.CLEAR_BUTTON)
                ) {
                    Text("Clear")
                }
            }
        }
    }
}
