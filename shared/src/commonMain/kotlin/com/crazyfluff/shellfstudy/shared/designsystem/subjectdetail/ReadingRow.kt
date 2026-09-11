package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crazyfluff.shellfstudy.shared.data.PlaybackState
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import com.crazyfluff.shellfstudy.shared.designsystem.text.JapaneseText

/**
 * The player readings dispatch through, provided once at each platform's app root (see
 * [com.crazyfluff.shellfstudy.shared.ShellfStudyApp]) rather than handed down screen by screen —
 * a row knows which clip belongs to its own reading, but has no business owning playback. Nullable
 * on purpose: a preview or test that composes a screen without providing one gets no play buttons
 * at all, instead of a button that looks tappable and does nothing.
 */
val LocalPronunciationAudioPlayer = staticCompositionLocalOf<PronunciationAudioPlayer?> { null }

/**
 * A vocabulary reading, with a play button when there is audio for it — and nothing else.
 * Deliberately separate from [PitchAccentDiagram]: a reading is worth showing whether or not the
 * user wants pitch-accent markers (the "Show pitch accent" setting controls the markers, not the
 * reading), so callers render this always and the diagram only when they want it.
 *
 * Takes a plain [reading] rather than a [ReadingPitchAccent] because it shows nothing from one: no
 * pitch data is consulted, so none has to exist. [audio] is data, not an event — it is the clip the
 * caller's own settings already selected for this reading, so passing null (no clip, or every clip
 * filtered away) is the single answer to "should there be a button".
 */
@Composable
fun ReadingRow(
    reading: String,
    audio: PronunciationAudio? = null
) {
    val player = LocalPronunciationAudioPlayer.current
    // Read before the Row, not inside the button: the hollow "audio unavailable" icon is driven by
    // the player's state, which nothing here owns. Without a player there is nothing to report, so
    // IDLE is the honest stand-in — spelled out rather than folded into the access above, so the
    // collect stays an unambiguously unconditional composable call.
    val playbackState = if (player == null) {
        PlaybackState.IDLE
    } else {
        player.state.collectAsState().value
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        // The same style the diagram measures its morae with (see pitchAccentTextStyle), so the dots
        // land under the right glyphs.
        JapaneseText(reading, style = pitchAccentTextStyle())
        if (audio != null && player != null) {
            IconButton(onClick = { player.play(audio) }) {
                if (playbackState == PlaybackState.ERROR) {
                    Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = "Audio unavailable for $reading")
                } else {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Play pronunciation for $reading")
                }
            }
        }
    }
}
