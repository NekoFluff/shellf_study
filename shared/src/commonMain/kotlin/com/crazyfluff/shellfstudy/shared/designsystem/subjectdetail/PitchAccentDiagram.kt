package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.data.model.forReading
import com.crazyfluff.shellfstudy.shared.designsystem.theme.LocalEinkTheme
import com.crazyfluff.shellfstudy.shared.designsystem.theme.PitchAccentColors

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
    // The pattern is already conveyed by dot height/position and the odaka drop-tick below, so
    // under the e-ink theme every pattern just draws in the same flat onSurface color.
    if (LocalEinkTheme.current) return MaterialTheme.colorScheme.onSurface
    return when {
        pitchNumber == 0 -> PitchAccentColors.Heiban
        pitchNumber == 1 -> PitchAccentColors.Atamadaka
        pitchNumber == moraCount -> PitchAccentColors.Odaka
        else -> PitchAccentColors.Nakadaka
    }
}

/**
 * Replicates Smouldering Durtles' `PitchInfoDiagramView`: one dot per mora, connected by lines,
 * high/low position derived from [pitchAccent]'s pitch number, color-coded by pattern
 * (heiban/atamadaka/nakadaka/odaka). Odaka gets a trailing drop-tick after the last mora — without
 * it, odaka and heiban are visually identical (they only differ in what happens on the next word).
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

    val diagramHeightDp = 20.dp
    val dotRadiusPx = with(density) { 3.dp.toPx() }
    val tickLengthPx = with(density) { 5.dp.toPx() }
    // Size the canvas to the diagram's actual content width (mora glyph widths plus a small
    // trailing allowance for the odaka drop-tick / dot radius) instead of fillMaxWidth() —
    // otherwise short readings draw their dots crammed into a corner of a much wider canvas,
    // leaving a large dead gap before whatever follows in the row (e.g. the play button).
    val contentWidthDp = with(density) {
        (moraWidths.sum() + dotRadiusPx + (if (pitchNumber == moraCount) tickLengthPx else 0f)).toDp()
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
        val tickLength = tickLengthPx

        var x = 0f
        val points = moraWidths.mapIndexed { index, width ->
            val point = Offset(x + width / 2f, if (isHighMora(index, pitchNumber, moraCount)) highY else lowY)
            x += width
            point
        }

        for (i in 0 until points.lastIndex) {
            drawLine(color = color, start = points[i], end = points[i + 1], strokeWidth = strokeWidth)
        }
        if (pitchNumber == moraCount) {
            val last = points.last()
            drawLine(color = color, start = last, end = Offset(last.x + tickLength, lowY), strokeWidth = strokeWidth)
        }
        points.forEach { point -> drawCircle(color = color, radius = dotRadius, center = point) }
    }
}

/** A reading with its pitch-accent diagram above it, or a plain reading if no pitch data matches. */
@Composable
fun PitchAccentReadingRow(reading: String, pitchAccents: List<PitchAccent>, modifier: Modifier = Modifier) {
    val match = remember(reading, pitchAccents) { pitchAccents.forReading(reading) }
    Column(modifier = modifier) {
        if (match != null) {
            PitchAccentDiagram(reading = reading, pitchAccent = match)
        }
        Text(reading, style = MaterialTheme.typography.bodyLarge)
    }
}
