package com.crazyfluff.shellfstudy.shared.designsystem.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.data.model.allForReading
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentDiagram
import com.crazyfluff.shellfstudy.shared.designsystem.text.JapaneseText

/**
 * The matching pitch-accent diagram(s), with the reading's characters lined up directly below
 * their own dots (see [PitchAccentDiagram]'s `showReadingBelow`) — shown above a quiz's subject
 * glyph once a reading question is answered. Falls back to plain reading text when no pitch
 * pattern matches, since there are no dots to align it with in that case.
 */
@Composable
fun AnswerReadingPitchAccentHint(reading: String, pitchAccents: List<PitchAccent>, modifier: Modifier = Modifier) {
    val matches = remember(reading, pitchAccents) { pitchAccents.allForReading(reading) }
    // A step up from labelSmall (used elsewhere for a subject-type caption) — this is the reading
    // itself, so it earns a bit more visual weight even at furigana scale.
    val furiganaStyle = MaterialTheme.typography.bodyMedium

    if (matches.isEmpty()) {
        JapaneseText(reading, style = furiganaStyle, modifier = modifier)
        return
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        matches.forEachIndexed { index, match ->
            PitchAccentDiagram(
                reading = reading,
                pitchAccent = match,
                textStyle = furiganaStyle,
                // Only the first pattern needs the reading spelled out below it — a second accepted
                // pattern (e.g. a different part of speech) would otherwise repeat the same reading
                // a second time right underneath.
                showReadingBelow = index == 0
            )
        }
    }
}
