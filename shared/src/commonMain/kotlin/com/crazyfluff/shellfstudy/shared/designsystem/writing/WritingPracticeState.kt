package com.crazyfluff.shellfstudy.shared.designsystem.writing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset

/**
 * Holds the in-progress and completed strokes for one writing-practice session. Entirely
 * in-memory — there is no self-study/backup layer yet for this drawing to persist into, so it's
 * expected to vanish the moment the composable holding it leaves composition.
 */
@Stable
class WritingPracticeState {
    val completedStrokes: SnapshotStateList<WritingStroke> = mutableStateListOf()
    val currentStrokePoints: SnapshotStateList<Offset> = mutableStateListOf()

    fun onDragStart(point: Offset) {
        currentStrokePoints.clear()
        currentStrokePoints.add(point)
    }

    fun onDrag(point: Offset) {
        currentStrokePoints.add(point)
    }

    /** A drag that never left its start point — a stray tap, common with e-ink pen jitter on
     *  press — is discarded rather than recorded as a zero-length stroke. */
    fun onDragEnd() {
        if (currentStrokePoints.size > 1) {
            completedStrokes.add(WritingStroke(points = currentStrokePoints.toList()))
        }
        currentStrokePoints.clear()
    }

    fun undoLast() {
        if (completedStrokes.isNotEmpty()) completedStrokes.removeAt(completedStrokes.lastIndex)
    }

    fun clear() {
        completedStrokes.clear()
        currentStrokePoints.clear()
    }
}

/**
 * [resetKey] should be the subject's own identity (e.g. `SubjectDetail.subjectId`). The detail
 * sheet reuses one composable subtree across the whole drill-down back-stack when navigating into
 * a related subject — without a reset key tied to subject identity, ink drawn on one kanji would
 * silently carry over onto the next one navigated into.
 */
@Composable
fun rememberWritingPracticeState(resetKey: Any?): WritingPracticeState =
    remember(resetKey) { WritingPracticeState() }
