package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.PlaybackState
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.designsystem.text.JapaneseText
import com.crazyfluff.shellfstudy.shared.designsystem.theme.LocalEinkTheme
import com.crazyfluff.shellfstudy.shared.designsystem.theme.LocalJapaneseFontFamily
import com.crazyfluff.shellfstudy.shared.designsystem.theme.PitchAccentColors
import kotlin.math.hypot

object PitchAccentTestTags {
    const val DIAGRAM = "pitch_accent_diagram"

    /** The caption every pitch-accent renderer shows in place of a diagram: "not checked yet",
     *  "not available", or — when a fetch the reader asked for failed — "Couldn't check pitch accent". */
    const val MESSAGE = "pitch_accent_message"

    /** The inline "Check now"/"Try again" link offered under the "not checked yet" / failed caption
     *  — see [PitchAccentDiagram]. */
    const val CHECK = "pitch_accent_check"

    /** The spinner shown in place of that link while a check is in flight. */
    const val CHECKING = "pitch_accent_checking"

    /** The whole row — reading, play button, and whatever pitch section applies. Tags itself, so
     *  callers don't have to pass one in just to make the block findable from a test. */
    const val ROOT = "pitch_accent_root"
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
 * The style the reading is drawn in and the diagrams measure their morae with. Those two have to
 * agree — the dots' x-positions come from glyph advance widths measured in this style — so both read
 * it from here rather than looking it up separately and silently drifting apart. bodyLarge because
 * this is body copy in a detail column or a study card, not furigana scale.
 *
 * Internal rather than private because the two halves now live in separate composables: [ReadingRow]
 * draws the reading, this file's diagram measures it.
 */
@Composable
internal fun pitchAccentTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge

/** What the surrounding content can say about fetching a word's pitch accent on demand. */
data class PitchAccentCheck(
    val inProgress: Boolean,
    val failed: Boolean,
    val onClick: () -> Unit
)

/**
 * The check a "not checked yet" reading offers its reader, provided around the content that shows
 * readings (the subject detail sheet and both quiz screens) rather than handed down through
 * intermediate composables — a row knows which reading it draws, but has no business owning the
 * fetch. Nullable on purpose: a surface whose state a fetch cannot update (the lesson study card,
 * whose readings are reference material rather than a just-graded answer) simply leaves it
 * unprovided, so it shows no "Check now" link at all instead of one that looks tappable and does
 * nothing. Offering the value *is* the affordance.
 */
val LocalPitchAccentCheck = staticCompositionLocalOf<PitchAccentCheck?> { null }

/**
 * The pitch-accent patterns for one reading — the diagrams, or a caption saying why there are none.
 * Renders nothing but that: the reading itself is [ReadingRow]'s job, so a caller that doesn't want
 * diagrams (see the "show pitch accent" preference) simply doesn't call this.
 *
 * Takes the reading and its pitch data as one value ([ReadingPitchAccent]) rather than as two
 * arguments: they are only meaningful together, and pairing them is
 * [com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.forReading]'s job, not a renderer's.
 * [ReadingPitchAccent.Patterns] stacks one diagram per pattern, labeled by part of speech when there
 * is more than one; [ReadingPitchAccent.NoPatterns] render their message. Both are answers to the same
 * question, so neither is left blank: "not checked yet" and "not available" mean different things, and
 * silence would leave the user unable to tell them apart.
 *
 * Knows nothing about *why* it is being shown or hidden — preferences stay with the caller — and takes
 * no `modifier`: it sizes itself to its content and tags its own root
 * ([PitchAccentTestTags.ROOT]).
 *
 * Reads [LocalPitchAccentCheck] rather than taking it as an argument, since this is where it is
 * consumed. Offering it is what shows the check affordance, and only under
 * [ReadingPitchAccent.Pending] — the one state a fetch can still resolve. [ReadingPitchAccent.NoEntry]
 * renders its caption alone, since a confirmed absence is an answer rather than something to retry.
 * [PitchAccentCheck.inProgress] swaps the link for a spinner and "Checking pitch accent…";
 * [PitchAccentCheck.failed] keeps the link (as "Try again") but replaces the caption, because a
 * failed fetch leaves the word in that same pending state — the caption would otherwise still claim
 * the word was never checked and say nothing about the failure.
 */
@Composable
fun PitchAccentDiagram(readingPitchAccent: ReadingPitchAccent) {
    val pitchAccentCheck = LocalPitchAccentCheck.current

    Column(modifier = Modifier.testTag(PitchAccentTestTags.ROOT)) {
        when (readingPitchAccent) {
            is ReadingPitchAccent.Patterns -> readingPitchAccent.patterns.forEach { match ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PitchAccentPattern(reading = readingPitchAccent.reading, pitchAccent = match)
                    if (readingPitchAccent.patterns.size > 1 && match.partOfSpeech != null) {
                        Text(match.partOfSpeech, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            is ReadingPitchAccent.NoPatterns -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pending is the only "no patterns" state a fetch can still turn into an answer;
                // NoEntry has already been looked up, so offering a retry there would be noise.
                if (readingPitchAccent is ReadingPitchAccent.Pending && pitchAccentCheck?.inProgress == true) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(12.dp)
                            .testTag(PitchAccentTestTags.CHECKING),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Checking pitch accent…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = if (readingPitchAccent is ReadingPitchAccent.Pending && pitchAccentCheck?.failed == true) {
                            "Couldn't check pitch accent"
                        } else {
                            readingPitchAccent.message
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(PitchAccentTestTags.MESSAGE)
                    )
                    // Set in the caption's own size beside it — an inline link, not a button with its
                    // own weight.
                    if (readingPitchAccent is ReadingPitchAccent.Pending && pitchAccentCheck != null) {
                        Text(
                            text = if (pitchAccentCheck.failed) "Try again" else "Check now",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable(role = Role.Button, onClickLabel = "Check pitch accent now", onClick = pitchAccentCheck.onClick)
                                .testTag(PitchAccentTestTags.CHECK)
                        )
                    }
                }
            }
        }
    }
}


/**
 * Replicates the NHK pitch-accent dictionary style: one filled dot per mora, connected by lines,
 * high/low position derived from [pitchAccent]'s pitch number, color-coded by pattern
 * (heiban/atamadaka/nakadaka/odaka), plus a trailing open dot (hollow circle) after the last mora
 * representing the following particle — shown for every pattern, at whatever height that pattern's
 * particle is actually pronounced (high for heiban, low otherwise).
 *
 * Private drawing primitive for one pattern: [PitchAccentPatterns] is the only caller, and it decides
 * which patterns exist and at what scale, so this never has to know what a state is. Kept out of
 * [PitchAccentDiagram] rather than inlined because it is pure canvas geometry — mora measurement, dot
 * positions, the particle ring — invoked once per matching pattern, and inlining it would put those
 * `remember`s inside a data-driven loop.
 */
@Composable
private fun PitchAccentPattern(
    reading: String,
    pitchAccent: PitchAccent
) {
    val morae = remember(reading) { splitIntoMorae(reading) }
    if (morae.isEmpty()) return

    val moraCount = morae.size
    val pitchNumber = pitchAccent.pitchNumber
    val color = pitchPatternColor(pitchNumber, moraCount)
    // Same style the reading is drawn in (see pitchAccentTextStyle) — otherwise the per-mora widths
    // measured here (and thus the dots' horizontal spacing) drift from the real glyph widths above
    // them in the reading line.
    val measuredTextStyle = pitchAccentTextStyle().copy(fontFamily = LocalJapaneseFontFamily.current)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val moraWidths = remember(morae, measuredTextStyle) {
        morae.map { textMeasurer.measure(it, measuredTextStyle).size.width.toFloat() }
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
        modifier = Modifier
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