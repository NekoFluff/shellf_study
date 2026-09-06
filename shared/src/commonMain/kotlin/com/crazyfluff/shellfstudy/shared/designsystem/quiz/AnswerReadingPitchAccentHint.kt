package com.crazyfluff.shellfstudy.shared.designsystem.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.data.model.allForReading
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.MoraReadingText
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentDiagram

/** Matches IconButton's default minimum touch target — used as a leading spacer (see below) so a
 *  trailing play button doesn't pull the diagram off-center. */
private val IconButtonDefaultSize = 48.dp

/**
 * Every matching pitch-accent diagram, stacked above one shared reading row (see
 * [MoraReadingText]) lined up under their dots, plus a play button to the right of the whole
 * stack — shown above a quiz's subject glyph once a reading question is answered. A reading with
 * no pitch-accent match still shows the reading alone (an empty diagram stack, just the row).
 * Mirrors [com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.VocabReadingRow]'s trailing
 * play button in the subject-detail sheet.
 *
 * The diagram/reading block itself stays centered under the glyph above it — not the whole
 * row including the button — by balancing the trailing button with an equally-wide leading
 * spacer. Without it, the parent's horizontal centering would center the diagram+button pair as
 * a unit, visibly shifting the diagram (and the glyph it needs to line up with) left whenever the
 * button is showing.
 */
@Composable
fun AnswerReadingPitchAccentHint(
    reading: String,
    pitchAccents: List<PitchAccent>,
    modifier: Modifier = Modifier,
    hasAudio: Boolean = false,
    onPlayReading: (() -> Unit)? = null
) {
    val matches = remember(reading, pitchAccents) { pitchAccents.allForReading(reading) }
    // A step up from labelSmall (used elsewhere for a subject-type caption) — this is the reading
    // itself, so it earns a bit more visual weight even at furigana scale.
    val furiganaStyle = MaterialTheme.typography.bodyMedium
    // Captured as a local val (rather than checking `onPlayReading != null && hasAudio` at each
    // use site) so Kotlin can smart-cast it to a non-null callback below, instead of needing `!!`.
    val playCallback = onPlayReading.takeIf { hasAudio }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (playCallback != null) {
            Spacer(modifier = Modifier.size(IconButtonDefaultSize))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            matches.forEach { match ->
                PitchAccentDiagram(reading = reading, pitchAccent = match, textStyle = furiganaStyle)
            }
            MoraReadingText(reading = reading, textStyle = furiganaStyle)
        }
        if (playCallback != null) {
            IconButton(onClick = playCallback) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Play pronunciation for $reading")
            }
        }
    }
}
