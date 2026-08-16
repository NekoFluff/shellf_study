package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.data.model.SubjectDetail
import com.crazyfluff.shellfstudy.shared.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.shared.designsystem.strokeorder.StrokeOrderSection
import com.crazyfluff.shellfstudy.shared.designsystem.strokeorder.StrokeOrderUiState
import androidx.compose.foundation.text.selection.SelectionContainer
import com.crazyfluff.shellfstudy.shared.designsystem.theme.SrsStageChip
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectTypeLabel
import com.crazyfluff.shellfstudy.shared.designsystem.writing.WritingPracticeSection
import com.crazyfluff.shellfstudy.shared.network.SubjectType

/** Whether the sheet shows everything (browse/study contexts) or hides the currently-tested field (mid-quiz). */
enum class DetailRevealMode { FULL, HIDE_UNTIL_ANSWERED }

enum class DetailQuestionType { MEANING, READING }

object SubjectDetailTestTags {
    const val SHEET_ROOT = "subject_detail_sheet_root"
    const val CONTENT_ROOT = "subject_detail_content_root"
    const val PEEK_HANDLE = "subject_detail_peek_handle"
}

/**
 * The shared "everything about this subject" content, used from Review (gated), Lesson, Search,
 * and the Dashboard's level-progress breakdown. Section order mirrors Smouldering Durtles'
 * information architecture: headline, meanings, readings, components, mnemonics, parts of speech,
 * context sentences, visually similar, used-in.
 */
@Composable
fun SubjectDetailContent(
    detail: SubjectDetail,
    relatedSubjects: Map<Long, SubjectSummary>,
    revealMode: DetailRevealMode,
    isAnswered: Boolean,
    questionType: DetailQuestionType?,
    onRelatedSubjectClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showPitchAccent: Boolean = true,
    onPlayReading: ((String) -> Unit)? = null,
    strokeOrder: StrokeOrderUiState = StrokeOrderUiState.Unavailable,
    autoPlayStrokeOrder: Boolean = true,
    srsStage: SrsStage? = null
) {
    val revealMeaning = revealMode == DetailRevealMode.FULL || (isAnswered && questionType == DetailQuestionType.MEANING)
    val revealReading = revealMode == DetailRevealMode.FULL || (isAnswered && questionType == DetailQuestionType.READING)
    val hasReadings = detail.readings.isNotEmpty()
    val isVocabulary = detail.subjectType == SubjectType.VOCABULARY || detail.subjectType == SubjectType.KANA_VOCABULARY

    // Reset (not just remembered fresh) so navigating deeper into a related subject scrolls back
    // to the top of its own content, instead of inheriting whatever offset the previous subject was
    // scrolled to — an effect-driven reset animates cleanly rather than discarding in-flight gesture
    // state on the same frame the way keying rememberScrollState() to subjectId would.
    val scrollState = rememberScrollState()
    LaunchedEffect(detail.subjectId) {
        scrollState.scrollTo(0)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .testTag(SubjectDetailTestTags.CONTENT_ROOT),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Headline: title, level/type subtitle, and part-of-speech tags read as one tight cluster —
        // they're all "what is this" at a glance, so they sit closer together than the sections below.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SubjectGlyph(
                characters = detail.characters,
                characterImageUrl = detail.characterImageUrl,
                subjectType = detail.subjectType,
                size = 80.dp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Level ${detail.level} · ${subjectTypeLabel(detail.subjectType)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (srsStage != null) {
                    SrsStageChip(srsStage)
                }
            }
            if (isVocabulary && detail.partsOfSpeech.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail.partsOfSpeech.forEach { part ->
                        AssistChip(onClick = {}, label = { Text(part) })
                    }
                }
            }
        }
        StrokeOrderSection(strokeOrder, autoPlay = autoPlayStrokeOrder)
        WritingPracticeSection(strokeOrder = strokeOrder, resetKey = detail.subjectId)

        RelatedSubjectsSection(
            title = componentsLabel(detail.subjectType),
            subjects = detail.componentSubjectIds.mapNotNull { relatedSubjects[it] },
            onSubjectClick = onRelatedSubjectClick
        )

        val hasReadingBreakdown =
            detail.onyomiReadings.isNotEmpty() || detail.kunyomiReadings.isNotEmpty() || detail.nanoriReadings.isNotEmpty()
        val showMeaningZone = revealMeaning
        val showReadingZone = revealReading && hasReadings
        val meaningMnemonic = detail.meaningMnemonic
        val readingMnemonic = detail.readingMnemonic
        val meaningHint = detail.meaningHint
        val readingHint = detail.readingHint
        val hasMeaningMnemonic = !meaningMnemonic.isNullOrBlank()
        val hasReadingMnemonic = !readingMnemonic.isNullOrBlank()

        // Meaning and Reading are deliberately structured the same way — title, then its own
        // mnemonic right underneath — so the two read as parallel, equal-weight sections rather than
        // one standing out from the other. Plain sections (no card) keep them visually lightweight
        // for a view learners scroll through constantly; a divider between them is enough separation.
        if (showMeaningZone) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Meaning",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(detail.meanings.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
                    if (detail.auxiliaryMeanings.isNotEmpty()) {
                        Text(
                            text = detail.auxiliaryMeanings.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (hasMeaningMnemonic) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionEyebrow("Meaning mnemonic")
                        WkMnemonicText(meaningMnemonic, style = MaterialTheme.typography.bodyMedium)
                        if (!meaningHint.isNullOrBlank()) {
                            WkMnemonicText(meaningHint, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (showReadingZone) {
            if (showMeaningZone) HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Reading",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (detail.subjectType == SubjectType.KANJI && hasReadingBreakdown) {
                        if (detail.onyomiReadings.isNotEmpty()) {
                            ReadingTypeRow(label = "On'yomi", readings = detail.onyomiReadings)
                        }
                        if (detail.kunyomiReadings.isNotEmpty()) {
                            ReadingTypeRow(label = "Kun'yomi", readings = detail.kunyomiReadings)
                        }
                        if (detail.nanoriReadings.isNotEmpty()) {
                            ReadingTypeRow(label = "Nanori", readings = detail.nanoriReadings)
                        }
                    } else if (isVocabulary) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            detail.readings.forEach { reading ->
                                VocabReadingRow(
                                    reading = reading,
                                    pitchAccents = detail.pitchAccents,
                                    showPitchAccent = showPitchAccent,
                                    hasAudio = detail.pronunciationAudios.isNotEmpty(),
                                    onPlayReading = onPlayReading
                                )
                            }
                        }
                    } else {
                        Text(detail.readings.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
                    }
                }

                if (hasReadingMnemonic) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionEyebrow("Reading mnemonic")
                        WkMnemonicText(readingMnemonic, style = MaterialTheme.typography.bodyMedium)
                        if (!readingHint.isNullOrBlank()) {
                            WkMnemonicText(readingHint, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (isVocabulary && detail.contextSentences.isNotEmpty()) {
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionEyebrow("Context sentences")
                // 20dp between example sentences (vs. 2dp between a sentence's own JP/EN pair) so
                // each example reads as its own distinct card of information while scanning.
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        detail.contextSentences.forEach { sentence ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(sentence.japanese, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = sentence.english,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (detail.subjectType == SubjectType.KANJI) {
            RelatedSubjectsSection(
                title = "Visually similar",
                subjects = detail.visuallySimilarSubjectIds.mapNotNull { relatedSubjects[it] },
                onSubjectClick = onRelatedSubjectClick
            )
        }

        RelatedSubjectsSection(
            title = "Used in",
            subjects = detail.amalgamationSubjectIds.mapNotNull { relatedSubjects[it] },
            onSubjectClick = onRelatedSubjectClick
        )

    }
}

fun componentsLabel(type: SubjectType): String = when (type) {
    SubjectType.KANJI -> "Radicals"
    SubjectType.VOCABULARY, SubjectType.KANA_VOCABULARY -> "Kanji"
    SubjectType.RADICAL -> "Components"
}

/** A small uppercase, letter-spaced label for secondary sections (mnemonics, context sentences) — kept visually quieter than the primary "Meaning"/"Reading" headers so the two hierarchy tiers are easy to tell apart while scanning. */
@Composable
fun SectionEyebrow(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun VocabReadingRow(
    reading: String,
    pitchAccents: List<PitchAccent>,
    showPitchAccent: Boolean,
    hasAudio: Boolean,
    onPlayReading: ((String) -> Unit)?
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showPitchAccent && pitchAccents.isNotEmpty()) {
            // weight(1f, fill = false): claim only the space left after the play button, instead
            // of the diagram's Canvas.fillMaxWidth() greedily filling the whole row and pushing
            // the button out of the visible area entirely.
            PitchAccentReadingRow(reading = reading, pitchAccents = pitchAccents, modifier = Modifier.weight(1f, fill = false))
        } else {
            Text(reading, style = MaterialTheme.typography.bodyLarge)
        }
        if (onPlayReading != null && hasAudio) {
            IconButton(onClick = { onPlayReading(reading) }) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Play pronunciation for $reading")
            }
        }
    }
}

@Composable
fun ReadingTypeRow(label: String, readings: List<String>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(readings.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
    }
}
