package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.data.model.allForReading
import com.crazyfluff.shellfstudy.shared.designsystem.theme.LocalEinkTheme
import com.crazyfluff.shellfstudy.shared.designsystem.theme.PitchAccentColors
import kotlin.math.hypot

object PitchAccentTestTags {
    const val DIAGRAM = "pitch_accent_diagram"
}

private val COMBINING_SMALL_KANA =
    setOf('ゃ', 'ゅ', 'ょ', 'ぁ', 'ぃ', 'ぅ', 'ぇ', 'ぉ', 'ゎ', 'ャ', 'ュ', 'ョ', 'ァ', 'ィ', 'ゥ', 'ェ', 'ォ', 'ヮ')

/** Splits a kana reading into morae — a combining small kana (きゃ, しゅ, ちょ, ...) merges with the mora before it. */
fun splitIntoMorae(reading: String): List<String> {
    val morae = mutableListOf<String>()
    for (c in reading) {
        if (c in COMBINING_SMALL_KANA && morae.isNotEmpty()) {
            morae[morae.lastIndex] = morae.last() + c
        } else {
            morae.add(c.toString())
        }
    }
    return morae
}

/** True if [moraIndex] (0-based) is pronounced high, per standard Japanese pitch-accent rules. */
fun isHighMora(moraIndex: Int, pitchNumber: Int, moraCount: Int): Boolean = when {
    pitchNumber == 0 -> moraIndex != 0
    pitchNumber == 1 -> moraIndex == 0
    else -> moraIndex in 1 until pitchNumber
}

@Composable
internal fun pitchPatternColor(pitchNumber: Int, moraCount: Int): Color {
    // The pattern is already conveyed by dot height/position and the trailing particle dot below,
    // so under the e-ink theme every pattern just draws in the same flat onSurface color.
    if (LocalEinkTheme.current) return MaterialTheme.colorScheme.onSurface
    return when {
        pitchNumber == 0 -> PitchAccentColors.Heiban
        pitchNumber == 1 -> PitchAccentColors.Atamadaka
        pitchNumber == moraCount -> PitchAccentColors.Odaka
        else -> PitchAccentColors.Nakadaka
    }
}

/**
 * Replicates the NHK pitch-accent dictionary style: one filled dot per mora, connected by lines,
 * high/low position derived from [pitchAccent]'s pitch number, color-coded by pattern
 * (heiban/atamadaka/nakadaka/odaka), plus a trailing open dot (hollow circle) after the last mora
 * representing the following particle — shown for every pattern, at whatever height that pattern's
 * particle is actually pronounced (high for heiban, low otherwise).
 */
@Composable
fun PitchAccentDiagram(reading: String, pitchAccent: PitchAccent, modifier: Modifier = Modifier) {
    val morae = remember(reading) { splitIntoMorae(reading) }
    if (morae.isEmpty()) return

    val moraCount = morae.size
    val pitchNumber = pitchAccent.pitchNumber
    val color = pitchPatternColor(pitchNumber, moraCount)
    val textStyle = MaterialTheme.typography.bodyLarge
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val moraWidths = remember(morae, textStyle) {
        morae.map { textMeasurer.measure(it, textStyle).size.width.toFloat() }
    }

    // The trailing particle dot gets the same slot width as a real mora (its average width) so it
    // reads as just another beat in the sequence rather than a separate tacked-on mark.
    val particleWidth = remember(moraWidths) { moraWidths.average().toFloat() }

    val diagramHeightDp = 20.dp
    val dotRadiusPx = with(density) { 3.dp.toPx() }
    // Size the canvas to the diagram's actual content width (mora glyph widths, the particle dot's
    // slot, plus a small trailing allowance for its radius) instead of fillMaxWidth() — otherwise
    // short readings draw their dots crammed into a corner of a much wider canvas, leaving a large
    // dead gap before whatever follows in the row (e.g. the play button).
    val contentWidthDp = with(density) {
        (moraWidths.sum() + particleWidth + dotRadiusPx).toDp()
    }
    Canvas(
        modifier = modifier
            .width(contentWidthDp)
            .height(diagramHeightDp)
            .testTag(PitchAccentTestTags.DIAGRAM)
    ) {
        val highY = size.height * 0.2f
        val lowY = size.height * 0.8f
        val dotRadius = dotRadiusPx
        val strokeWidth = with(density) { 1.5.dp.toPx() }

        var x = 0f
        val points = moraWidths.mapIndexed { index, width ->
            val point = Offset(x + width / 2f, if (isHighMora(index, pitchNumber, moraCount)) highY else lowY)
            x += width
            point
        }
        // isHighMora naturally extends to moraIndex == moraCount — the mora "slot" just past the
        // last one — giving the correct pitch (high for heiban, low for every other pattern) for
        // the particle that follows the word.
        val particleY = if (isHighMora(moraCount, pitchNumber, moraCount)) highY else lowY
        val particleCenter = Offset(x + particleWidth / 2f, particleY)

        for (i in 0 until points.lastIndex) {
            drawLine(color = color, start = points[i], end = points[i + 1], strokeWidth = strokeWidth)
        }
        // Stop the line at the particle dot's edge rather than its center — the dot is hollow, so a
        // line running all the way to the center would poke visibly through the middle of the ring.
        val toParticle = particleCenter - points.last()
        val toParticleDistance = hypot(toParticle.x, toParticle.y)
        val particleEdge = particleCenter - toParticle * (dotRadius / toParticleDistance)
        drawLine(color = color, start = points.last(), end = particleEdge, strokeWidth = strokeWidth)
        points.forEach { point -> drawCircle(color = color, radius = dotRadius, center = point) }
        drawCircle(color = color, radius = dotRadius, center = particleCenter, style = Stroke(width = strokeWidth))
    }
}

/**
 * A reading, with a slot for trailing content (e.g. a play button) that always sits next to the
 * reading text no matter how many pitch patterns render below — and every matching pitch pattern
 * stacked underneath it, one per line. A reading can have more than one accepted pitch pattern
 * (e.g. one per part of speech); stacking rather than placing them side by side means any number
 * of patterns stay fully visible without needing horizontal scrolling. Each pattern is labeled by
 * its part of speech when more than one pattern is shown and that label is available.
 */
@Composable
fun PitchAccentReadingRow(
    reading: String,
    pitchAccents: List<PitchAccent>,
    modifier: Modifier = Modifier,
    trailingContent: @Composable () -> Unit = {}
) {
    val matches = remember(reading, pitchAccents) { pitchAccents.allForReading(reading) }
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(reading, style = MaterialTheme.typography.bodyLarge)
            trailingContent()
        }
        matches.forEach { match ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PitchAccentDiagram(reading = reading, pitchAccent = match)
                if (matches.size > 1 && match.partOfSpeech != null) {
                    Text(match.partOfSpeech, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
