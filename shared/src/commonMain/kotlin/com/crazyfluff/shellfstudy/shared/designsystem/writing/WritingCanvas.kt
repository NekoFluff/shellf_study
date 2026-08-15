package com.crazyfluff.shellfstudy.shared.designsystem.writing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.model.StrokeOrderStroke

object WritingPracticeTestTags {
    const val SECTION = "writing_practice_section"
    const val CANVAS = "writing_practice_canvas"
    const val UNDO_BUTTON = "writing_practice_undo"
    const val CLEAR_BUTTON = "writing_practice_clear"
    const val REFERENCE_TOGGLE = "writing_practice_reference_toggle"
    const val EXPAND_TOGGLE = "writing_practice_expand_toggle"
}

// KanjiVG's native coordinate space, matching StrokeOrderDiagram's own convention.
private const val KANJIVG_UNITS = 109f
private const val REFERENCE_STROKE_WIDTH_UNITS = 3f
private const val INK_STROKE_WIDTH_DP = 4f
private const val GRID_STROKE_WIDTH_DP = 1f
private val GRID_DASH = floatArrayOf(12f, 10f)

/**
 * A blank(ish) drawing surface for tracing a kanji's glyph with a finger or stylus. Stateless:
 * every stroke lives in the hoisted [completedStrokes]/[currentStrokePoints] parameters, and
 * gesture events are reported via callbacks rather than mutating anything locally — the only
 * state this composable owns is the once-per-stroke-list path parsing cache below.
 *
 * Two coordinate spaces coexist in one draw call: [referenceStrokes] paths are KanjiVG's fixed
 * 109x109 unit square and are drawn inside a `scale()` block to fit; user ink points arrive from
 * `pointerInput` already in this canvas's own local pixel space and are drawn outside that block.
 * Mixing the two up would draw the user's ink at 109/[size]ths of its intended size.
 *
 * [shape] is applied via `clip`, not just as a visual outline — pointer drags that continue past
 * the edge of the canvas (finger/stylus overshoot is common) would otherwise paint ink outside the
 * paper area entirely, since Compose doesn't clip draw calls to a layout's bounds by default.
 */
@Composable
fun WritingCanvas(
    completedStrokes: List<WritingStroke>,
    currentStrokePoints: List<Offset>,
    referenceStrokes: List<StrokeOrderStroke>,
    showReference: Boolean,
    onStrokeStart: (Offset) -> Unit,
    onStrokeDrag: (Offset) -> Unit,
    onStrokeEnd: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 260.dp,
    shape: Shape = RoundedCornerShape(16.dp),
    inkColor: Color = Color.Black,
    referenceColor: Color = Color.Black.copy(alpha = 0.15f),
    gridColor: Color = Color.Black.copy(alpha = 0.09f),
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant
) {
    val referencePaths = remember(referenceStrokes) {
        referenceStrokes.map { PathParser().parsePathString(it.pathData).toPath() }
    }
    val referenceStyle = remember {
        Stroke(width = REFERENCE_STROKE_WIDTH_UNITS, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }

    Canvas(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Color.White)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .testTag(WritingPracticeTestTags.CANVAS)
            .pointerInput(onStrokeStart, onStrokeDrag, onStrokeEnd) {
                detectDragGestures(
                    onDragStart = { offset -> onStrokeStart(offset) },
                    onDrag = { change, _ ->
                        change.consume()
                        onStrokeDrag(change.position)
                    },
                    onDragEnd = { onStrokeEnd() },
                    onDragCancel = { onStrokeEnd() }
                )
            }
    ) {
        drawPracticeGrid(gridColor)

        if (showReference && referencePaths.isNotEmpty()) {
            val scaleFactor = this.size.width / KANJIVG_UNITS
            scale(scaleFactor, scaleFactor, pivot = Offset.Zero) {
                referencePaths.forEach { path -> drawPath(path, color = referenceColor, style = referenceStyle) }
            }
        }

        val ink = Stroke(width = INK_STROKE_WIDTH_DP.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        completedStrokes.forEach { stroke -> drawInkPath(stroke.points, inkColor, ink) }
        if (currentStrokePoints.size > 1) drawInkPath(currentStrokePoints, inkColor, ink)
    }
}

/**
 * The classic 米字格 ("rice character grid") kanji practice paper guide — a center cross plus
 * corner-to-corner diagonals — so strokes can be judged against the glyph's natural center and
 * proportions rather than freehand on a blank square.
 */
private fun DrawScope.drawPracticeGrid(color: Color) {
    val w = size.width
    val h = size.height
    val style = Stroke(width = GRID_STROKE_WIDTH_DP.dp.toPx(), pathEffect = PathEffect.dashPathEffect(GRID_DASH))

    drawLine(color, Offset(w / 2f, 0f), Offset(w / 2f, h), style.width, pathEffect = style.pathEffect)
    drawLine(color, Offset(0f, h / 2f), Offset(w, h / 2f), style.width, pathEffect = style.pathEffect)
    drawLine(color, Offset(0f, 0f), Offset(w, h), style.width, pathEffect = style.pathEffect)
    drawLine(color, Offset(w, 0f), Offset(0f, h), style.width, pathEffect = style.pathEffect)
}

private fun DrawScope.drawInkPath(points: List<Offset>, color: Color, style: Stroke) {
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    }
    drawPath(path, color = color, style = style)
}
