package com.crazyfluff.shellfstudy.shared.designsystem.writing

import androidx.compose.ui.geometry.Offset

/**
 * One user-drawn stroke from the writing-practice canvas, recorded in that canvas's own local
 * pixel space. Purely ephemeral UI state — never serialized, persisted, or submitted anywhere —
 * which is why it lives here rather than alongside [com.crazyfluff.shellfstudy.shared.data.model.StrokeOrderStroke].
 */
data class WritingStroke(val points: List<Offset>)

/**
 * Converts a completed stroke's canvas-pixel points into KanjiVG's fixed 109x109 unit square,
 * given the pixel width the canvas was drawn at (it's always a square, so one scalar suffices).
 * This is the exact inverse of the `scaleFactor = size.width / 109f` that
 * [com.crazyfluff.shellfstudy.core.designsystem.strokeorder.StrokeOrderDiagram] uses to go the
 * other direction. Not called anywhere yet — kept as a documented, correct starting point for a
 * future stroke-shape comparison/grading feature, so that work doesn't have to re-derive it.
 */
fun WritingStroke.toKanjiVgUnits(canvasSizePx: Float): List<Offset> =
    points.map { it * (109f / canvasSizePx) }
