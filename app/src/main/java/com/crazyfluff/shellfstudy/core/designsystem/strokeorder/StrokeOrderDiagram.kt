package com.crazyfluff.shellfstudy.core.designsystem.strokeorder

import android.graphics.Paint as NativePaint
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.model.StrokeOrderStroke
import com.crazyfluff.shellfstudy.core.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import kotlinx.coroutines.delay

/** State for the "Stroke order" section of the subject detail view. */
sealed interface StrokeOrderUiState {
    data object Loading : StrokeOrderUiState
    data object Unavailable : StrokeOrderUiState
    data class Available(val strokes: List<StrokeOrderStroke>) : StrokeOrderUiState
}

object StrokeOrderTestTags {
    const val SECTION = "stroke_order_section"
    const val DIAGRAM = "stroke_order_diagram"
    const val REPLAY_BUTTON = "stroke_order_replay"
}

// KanjiVG's native coordinate space is a fixed 109x109 unit square; stroke width and number size
// below are chosen to match the proportions KanjiVG's own SVGs use in that same space, so they
// scale together with the diagram at any rendered size.
private const val KANJIVG_UNITS = 109f
private const val STROKE_WIDTH_UNITS = 3f
private const val PEN_TIP_RADIUS_UNITS = 2.5f
private const val NUMBER_FONT_SIZE_UNITS = 9f
private const val STROKE_DURATION_MS = 350
private const val INTER_STROKE_DELAY_MS = 120L

internal data class ParsedStrokes(val paths: List<Path>, val measures: List<PathMeasure>)

/**
 * Caches the pure [PathParser]/[PathMeasure] derivation of a stroke list across sheet opens within
 * the process — parsing is deterministic, and kanji get revisited often (repeat reviews,
 * related-subject drill-downs), so redoing it from scratch every open is wasted main-thread work.
 * Deliberately unbounded: what's cached is derived from WaniKani's fixed, small kanji corpus, so
 * even a session touching every kanji in the app tops out at a few thousand tiny entries — mirrors
 * [com.crazyfluff.shellfstudy.core.data.strokeorder.StrokeOrderRepository]'s own unbounded cache
 * for the same underlying reason.
 */
internal object ParsedStrokesCache {
    private val entries = mutableMapOf<List<StrokeOrderStroke>, ParsedStrokes>()

    fun obtain(strokes: List<StrokeOrderStroke>): ParsedStrokes =
        entries.getOrPut(strokes) {
            val paths = strokes.map { PathParser().parsePathString(it.pathData).toPath() }
            ParsedStrokes(paths, paths.map { PathMeasure().apply { setPath(it, false) } })
        }

    @VisibleForTesting
    internal fun clear() = entries.clear()
}

/**
 * The "Stroke order" section slotted into the subject detail view — a header, the diagram itself,
 * and the KanjiVG attribution the bundled data's CC BY-SA license requires. Renders nothing while
 * [state] is [StrokeOrderUiState.Loading] or [StrokeOrderUiState.Unavailable] (most radicals have
 * no Unicode glyph and so no stroke data at all).
 */
@Composable
fun StrokeOrderSection(state: StrokeOrderUiState, modifier: Modifier = Modifier, autoPlay: Boolean = true) {
    if (state !is StrokeOrderUiState.Available) return
    // Owned here rather than inside StrokeOrderDiagram so the replay button can live in its own row
    // below the diagram — sharing a Box with the canvas let it visually overlap strokes that
    // legitimately reach the same corner (KanjiVG artwork draws edge-to-edge).
    var replayTrigger by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier.testTag(StrokeOrderTestTags.SECTION),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Stroke order", style = MaterialTheme.typography.titleSmall)
        StrokeOrderDiagram(strokes = state.strokes, replayTrigger = replayTrigger, autoPlay = autoPlay)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Stroke data © KanjiVG contributors, CC BY-SA",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { replayTrigger++ },
                modifier = Modifier.size(28.dp).testTag(StrokeOrderTestTags.REPLAY_BUTTON)
            ) {
                Icon(Icons.Filled.Replay, contentDescription = "Replay stroke order", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Draws a kanji's strokes one at a time — numbered, in order — then stops; tapping the replay
 * button plays it again. Strokes not yet drawn are shown faint as a preview of the full
 * character, matching KanjiVG's own convention of always showing the complete glyph.
 */
@Composable
fun StrokeOrderDiagram(
    strokes: List<StrokeOrderStroke>,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeColor: Color = subjectColor(SubjectType.KANJI),
    ghostColor: Color = MaterialTheme.colorScheme.outlineVariant,
    replayTrigger: Int = 0,
    autoPlay: Boolean = true
) {
    val parsedStrokes = remember(strokes) { ParsedStrokesCache.obtain(strokes) }
    val paths = parsedStrokes.paths
    val pathMeasures = parsedStrokes.measures
    val segmentPath = remember { Path() }
    val numberPaint = remember {
        NativePaint().apply {
            isAntiAlias = true
            textSize = NUMBER_FONT_SIZE_UNITS
            // KanjiVG's own <text> elements left-align from their transform's x,y (the default SVG
            // text-anchor), which is what those coordinates were curated against — Align.LEFT
            // reproduces that placement exactly instead of re-centering it around a different point.
            textAlign = NativePaint.Align.LEFT
        }
    }

    var playedCount by remember(strokes) { mutableIntStateOf(0) }
    val currentProgress = remember(strokes) { Animatable(0f) }

    LaunchedEffect(strokes, replayTrigger, autoPlay) {
        if (!autoPlay) return@LaunchedEffect
        playedCount = 0
        currentProgress.snapTo(0f)
        paths.indices.forEach { index ->
            currentProgress.animateTo(1f, animationSpec = tween(STROKE_DURATION_MS))
            playedCount = index + 1
            // Reset before the inter-stroke pause, not at the top of the next iteration — otherwise
            // the still-1.0 progress from the stroke that just finished gets read against the *next*
            // stroke's (longer or shorter) length for the whole delay, flashing it fully drawn before
            // snapping back to empty when its own animation starts.
            currentProgress.snapTo(0f)
            delay(INTER_STROKE_DELAY_MS)
        }
    }

    val strokeColorArgb = strokeColor.toArgb()
    val strokeStyle = Stroke(width = STROKE_WIDTH_UNITS, cap = StrokeCap.Round, join = StrokeJoin.Round)

    Canvas(modifier = modifier.size(size).testTag(StrokeOrderTestTags.DIAGRAM)) {
        val scaleFactor = this.size.width / KANJIVG_UNITS
        scale(scaleFactor, scaleFactor, pivot = Offset.Zero) {
            paths.forEach { path -> drawPath(path, color = ghostColor, style = strokeStyle) }

            for (index in 0 until playedCount) {
                drawPath(paths[index], color = strokeColor, style = strokeStyle)
                drawStrokeNumber(numberPaint, strokeColorArgb, strokes[index], index + 1)
            }

            if (playedCount < paths.size) {
                val measure = pathMeasures[playedCount]
                val distance = measure.length * currentProgress.value
                segmentPath.reset()
                measure.getSegment(0f, distance, segmentPath, startWithMoveTo = true)
                drawPath(segmentPath, color = strokeColor, style = strokeStyle)
                if (currentProgress.value > 0f) {
                    drawCircle(color = strokeColor, radius = PEN_TIP_RADIUS_UNITS, center = measure.getPosition(distance))
                }
                drawStrokeNumber(numberPaint, strokeColorArgb, strokes[playedCount], playedCount + 1)
            }
        }
    }
}

private fun DrawScope.drawStrokeNumber(
    paint: NativePaint,
    colorArgb: Int,
    stroke: StrokeOrderStroke,
    number: Int
) {
    paint.color = colorArgb
    drawContext.canvas.nativeCanvas.drawText(number.toString(), stroke.labelX, stroke.labelY, paint)
}
