package com.crazyfluff.shellfstudy.shared.designsystem.strokeorder

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
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazyfluff.shellfstudy.shared.data.model.StrokeOrderStroke
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import kotlinx.coroutines.delay

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

data class ParsedStrokes(val paths: List<Path>, val measures: List<PathMeasure>)

/**
 * Caches the pure [PathParser]/[PathMeasure] derivation of a stroke list across sheet opens within
 * the process — parsing is deterministic, and kanji get revisited often (repeat reviews,
 * related-subject drill-downs), so redoing it from scratch every open is wasted main-thread work.
 * Deliberately unbounded: what's cached is derived from WaniKani's fixed, small kanji corpus, so
 * even a session touching every kanji in the app tops out at a few thousand tiny entries — mirrors
 * [com.crazyfluff.shellfstudy.core.data.strokeorder.StrokeOrderRepository]'s own unbounded cache
 * for the same underlying reason.
 */
object ParsedStrokesCache {
    private val entries = mutableMapOf<List<StrokeOrderStroke>, ParsedStrokes>()

    fun obtain(strokes: List<StrokeOrderStroke>): ParsedStrokes =
        entries.getOrPut(strokes) {
            val paths = strokes.map { PathParser().parsePathString(it.pathData).toPath() }
            ParsedStrokes(paths, paths.map { PathMeasure().apply { setPath(it, false) } })
        }

    fun clear() = entries.clear()
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
    var replayTrigger by remember { mutableStateOf(0) }

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
    val textMeasurer = rememberTextMeasurer()

    var playedCount by remember(strokes) { mutableStateOf(0) }
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

    val strokeStyle = Stroke(width = STROKE_WIDTH_UNITS, cap = StrokeCap.Round, join = StrokeJoin.Round)

    Canvas(modifier = modifier.size(size).testTag(StrokeOrderTestTags.DIAGRAM)) {
        val scaleFactor = this.size.width / KANJIVG_UNITS

        // Phase 1: all path drawing inside the coordinate-scaled block
        scale(scaleFactor, scaleFactor, pivot = Offset.Zero) {
            paths.forEach { path -> drawPath(path, color = ghostColor, style = strokeStyle) }

            for (index in 0 until playedCount) {
                drawPath(paths[index], color = strokeColor, style = strokeStyle)
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
            }
        }

        // Phase 2: stroke numbers drawn outside the scale block with manually scaled coordinates.
        // Font size is converted from the desired pixel height (NUMBER_FONT_SIZE_UNITS * scaleFactor)
        // back to sp so TextMeasurer renders it at the same physical size as NativePaint did —
        // independent of font-scale preference because these are diagram labels, not body text.
        val numberFontSizeSp = NUMBER_FONT_SIZE_UNITS * scaleFactor / (density * fontScale)
        val numberStyle = TextStyle(color = strokeColor, fontSize = numberFontSizeSp.sp)

        val indicesToLabel = buildList {
            for (index in 0 until playedCount) add(index)
            if (playedCount < paths.size) add(playedCount)
        }
        indicesToLabel.forEach { index ->
            val layout = textMeasurer.measure((index + 1).toString(), numberStyle)
            // KanjiVG's labelY is the text baseline (matching SVG text-anchor=start convention).
            // drawText's topLeft is the top-left corner, so subtract firstBaseline to align correctly.
            drawText(
                layout,
                topLeft = Offset(
                    strokes[index].labelX * scaleFactor,
                    strokes[index].labelY * scaleFactor - layout.firstBaseline
                )
            )
        }
    }
}
