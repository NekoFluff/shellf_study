package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.audio.selectAudioFor
import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import com.crazyfluff.shellfstudy.shared.designsystem.components.ExpandableAnswerListText
import com.crazyfluff.shellfstudy.shared.designsystem.components.SectionTitle
import com.crazyfluff.shellfstudy.shared.designsystem.text.JapaneseText
import com.crazyfluff.shellfstudy.shared.network.SubjectType

/**
 * The two halves of a subject's answer — the fields a learner opens a subject for — each rendered
 * with no "Meaning"/"Reading" subtitle, because English text is the meaning and kana is the reading,
 * so the headers only restated what the content already says while pushing the answer past the fold.
 *
 * They are separate composables rather than one zone because the two surfaces place them apart: the
 * meaning sits directly under the subject's characters (the strongest place on the page), the
 * level/type and part-of-speech tags follow it as metadata, and the reading comes after those. Taking
 * plain fields rather than a [com.crazyfluff.shellfstudy.shared.data.model.SubjectDetail] or a lesson
 * item is what lets the detail sheet and the lesson study card share them — the two used to carry
 * separate copies of this layout and drifted.
 */
@Composable
fun SubjectMeaningAnswer(
    meanings: List<String>,
    auxiliaryMeanings: List<String>,
    resetKey: Any?,
    modifier: Modifier = Modifier
) {
    if (meanings.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SubjectDetailTestTags.MEANING_ANSWER),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // SemiBold is the whole hierarchy cue now that the header is gone: the answer itself is the
        // heaviest thing on the page, and the reading stays regular weight.
        Text(
            text = meanings.joinToString(", "),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        if (auxiliaryMeanings.isNotEmpty()) {
            AuxiliaryMeaningsText(auxiliaryMeanings, resetKey = resetKey)
        }
    }
}

private enum class ReadingDisplayStyle { KANJI_BREAKDOWN, VOCABULARY, PLAIN }

/** Which shape the reading takes — derived from the subject type alone, so the two call sites can't
 *  disagree about it (the old pair threaded a pre-computed `isVocabulary`/`hasReadingBreakdown`). */
private fun readingDisplayStyle(subjectType: SubjectType, hasReadingBreakdown: Boolean): ReadingDisplayStyle = when {
    subjectType == SubjectType.KANJI && hasReadingBreakdown -> ReadingDisplayStyle.KANJI_BREAKDOWN
    subjectType == SubjectType.VOCABULARY || subjectType == SubjectType.KANA_VOCABULARY -> ReadingDisplayStyle.VOCABULARY
    else -> ReadingDisplayStyle.PLAIN
}

/**
 * The subject's reading — kanji reading-type rows, a vocabulary reading list with audio and pitch
 * accent, or a plain joined string. Callers gate it (quiz mode hides the field being tested); an
 * empty reading list renders nothing.
 */
@Composable
fun SubjectReadingAnswer(
    subjectType: SubjectType,
    readings: List<String>,
    modifier: Modifier = Modifier,
    onyomiReadings: List<String> = emptyList(),
    kunyomiReadings: List<String> = emptyList(),
    nanoriReadings: List<String> = emptyList(),
    pronunciationAudios: List<PronunciationAudio> = emptyList(),
    pitchAccents: PitchAccentUiState = PitchAccentUiState.Unavailable,
    showPitchAccent: Boolean = false,
    restrictAudioToMp3: Boolean = false
) {
    if (readings.isEmpty()) return

    val hasReadingBreakdown = onyomiReadings.isNotEmpty() || kunyomiReadings.isNotEmpty() || nanoriReadings.isNotEmpty()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SubjectDetailTestTags.READING_ANSWER),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (readingDisplayStyle(subjectType, hasReadingBreakdown)) {
            ReadingDisplayStyle.KANJI_BREAKDOWN -> {
                if (onyomiReadings.isNotEmpty()) ReadingTypeRow(label = "On'yomi", readings = onyomiReadings)
                if (kunyomiReadings.isNotEmpty()) ReadingTypeRow(label = "Kun'yomi", readings = kunyomiReadings)
                if (nanoriReadings.isNotEmpty()) ReadingTypeRow(label = "Nanori", readings = nanoriReadings)
            }
            ReadingDisplayStyle.VOCABULARY -> VocabularyReadingList(
                readings = readings,
                pronunciationAudios = pronunciationAudios,
                pitchAccents = pitchAccents,
                showPitchAccent = showPitchAccent,
                restrictAudioToMp3 = restrictAudioToMp3
            )
            ReadingDisplayStyle.PLAIN -> JapaneseText(readings.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun VocabularyReadingList(
    readings: List<String>,
    pronunciationAudios: List<PronunciationAudio>,
    pitchAccents: PitchAccentUiState,
    showPitchAccent: Boolean,
    restrictAudioToMp3: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        readings.forEach { reading ->
            Column {
                // Re-selected fresh on every tap (see ReadingRow's audio param) through the
                // caller's own settings, so a reading whose every clip the mp3-only filter drops
                // gets no button at all rather than one that plays nothing.
                ReadingRow(
                    reading = reading,
                    audio = { selectAudioFor(pronunciationAudios, reading, mp3Only = restrictAudioToMp3) }
                )
                // The setting controls the markers, not the reading: switched off, the row above is
                // all there is.
                if (showPitchAccent) {
                    PitchAccentDiagram(pitchAccents.forReading(reading))
                }
            }
        }
    }
}

/**
 * The prose that explains the answer — meaning and reading mnemonics, each with its own mnemonic-hint
 * line when WaniKani supplies one. Sits below the writing zone rather than next to the answer it
 * explains, keeping the top of the page to the answer itself.
 *
 * Each block is titled with the plain [SectionTitle] (the same heading "Stats" and "Context sentences"
 * use), and a divider between them marks the hand-off from one to the other. No accent colour: the
 * prose is the content, and a coloured marker in front of it only competed with the words.
 *
 * Renders its own divider only when it has something to show, so a subject with no mnemonics (or a
 * quiz-revealed field whose mnemonic is still gated) leaves neither an empty heading nor a dangling
 * rule.
 */
@Composable
fun SubjectMnemonicZone(
    meaningMnemonic: String?,
    meaningHint: String?,
    readingMnemonic: String?,
    readingHint: String?,
    showMeaning: Boolean,
    showReading: Boolean,
    modifier: Modifier = Modifier
) {
    val meaningText = meaningMnemonic?.takeIf { showMeaning && it.isNotBlank() }
    val readingText = readingMnemonic?.takeIf { showReading && it.isNotBlank() }
    if (meaningText == null && readingText == null) return

    HorizontalDivider()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (meaningText != null) {
            MnemonicBlock(title = "Meaning mnemonic", mnemonic = meaningText, hint = meaningHint)
        }
        if (meaningText != null && readingText != null) HorizontalDivider()
        if (readingText != null) {
            MnemonicBlock(title = "Reading mnemonic", mnemonic = readingText, hint = readingHint)
        }
    }
}

@Composable
private fun MnemonicBlock(title: String, mnemonic: String, hint: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(title)
        WkMnemonicText(mnemonic, style = MaterialTheme.typography.bodyMedium)
        // The hint is an aside about the mnemonic, not more mnemonic: muted so the prose above it stays
        // the thing being read.
        if (!hint.isNullOrBlank()) {
            WkMnemonicText(
                hint,
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }
    }
}

/** The label column of a kanji's reading rows. Wider than the longest label ("Kun'yomi") by a small
 *  gap, so the readings of all three rows start at the same x — a wrap-content label would leave them
 *  ragged for ~6dp of savings. */
private val ReadingTypeLabelWidth = 64.dp

@Composable
private fun ReadingTypeRow(label: String, readings: List<String>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = ReadingTypeLabelWidth)
        )
        JapaneseText(readings.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Auxiliary meanings truncate to a "+N more" summary the same way the review screen's answer
 *  feedback does (see [ExpandableAnswerListText]) — tapping toggles the full list back open and
 *  closed, rather than always spelling out every whitelisted alternate meaning up front. Shared by
 *  every screen that shows a subject's/item's auxiliary meanings (subject detail, lesson). */
@Composable
fun AuxiliaryMeaningsText(auxiliaryMeanings: List<String>, resetKey: Any?) {
    ExpandableAnswerListText(
        joined = auxiliaryMeanings.joinToString(", "),
        resetKey = resetKey,
        modifier = Modifier.testTag(SubjectDetailTestTags.AUXILIARY_MEANINGS_TEXT)
    )
}
