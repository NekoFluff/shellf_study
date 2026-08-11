package com.crazyfluff.shellfstudy.core.designsystem.subjectdetail

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import com.crazyfluff.shellfstudy.core.data.model.PitchAccent
import com.crazyfluff.shellfstudy.core.data.model.SubjectDetail
import com.crazyfluff.shellfstudy.core.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.core.designsystem.theme.subjectTypeLabel
import com.crazyfluff.shellfstudy.core.network.SubjectType

/** Whether the sheet shows everything (browse/study contexts) or hides the currently-tested field (mid-quiz). */
enum class DetailRevealMode { FULL, HIDE_UNTIL_ANSWERED }

enum class DetailQuestionType { MEANING, READING }

object SubjectDetailTestTags {
    const val SHEET_ROOT = "subject_detail_sheet_root"
    const val CONTENT_ROOT = "subject_detail_content_root"
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
    onPlayReading: ((String) -> Unit)? = null
) {
    val revealMeaning = revealMode == DetailRevealMode.FULL || (isAnswered && questionType != DetailQuestionType.MEANING)
    val revealReading = revealMode == DetailRevealMode.FULL || (isAnswered && questionType != DetailQuestionType.READING)
    val hasReadings = detail.readings.isNotEmpty()
    val isVocabulary = detail.subjectType == SubjectType.VOCABULARY || detail.subjectType == SubjectType.KANA_VOCABULARY

    // Swallow leftover fling velocity once this column's own scroll hits a bound (e.g. flinging
    // back up to the top). Without this, the enclosing ModalBottomSheet's nested scroll connection
    // treats that residual velocity as a swipe-to-dismiss fling, closing the sheet on a fast
    // scroll-up that never touched the sheet itself.
    val absorbResidualFlingConnection = remember {
        object : NestedScrollConnection {
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .nestedScroll(absorbResidualFlingConnection)
            .verticalScroll(rememberScrollState())
            .testTag(SubjectDetailTestTags.CONTENT_ROOT),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Headline
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SubjectGlyph(
                characters = detail.characters,
                characterImageUrl = detail.characterImageUrl,
                subjectType = detail.subjectType,
                size = 80.dp
            )
            Text(
                text = "Level ${detail.level} · ${subjectTypeLabel(detail.subjectType)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // TODO(stroke-order): slot in a StrokeOrderSection(detail.characters) composable here once
        // built — kanji/radical only. Deliberately not stubbed out; see the feature plan.

        if (isVocabulary && detail.partsOfSpeech.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                detail.partsOfSpeech.forEach { part ->
                    AssistChip(onClick = {}, label = { Text(part) })
                }
            }
        }

        if (revealMeaning) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Meaning", style = MaterialTheme.typography.titleSmall)
                Text(detail.meanings.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
                if (detail.auxiliaryMeanings.isNotEmpty()) {
                    Text(
                        text = detail.auxiliaryMeanings.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val hasReadingBreakdown =
            detail.onyomiReadings.isNotEmpty() || detail.kunyomiReadings.isNotEmpty() || detail.nanoriReadings.isNotEmpty()
        if (revealReading && hasReadings) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Reading", style = MaterialTheme.typography.titleSmall)
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
        }

        RelatedSubjectsSection(
            title = componentsLabel(detail.subjectType),
            subjects = detail.componentSubjectIds.mapNotNull { relatedSubjects[it] },
            onSubjectClick = onRelatedSubjectClick
        )

        if (revealMeaning && !detail.meaningMnemonic.isNullOrBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Meaning mnemonic", style = MaterialTheme.typography.titleSmall)
                WkMnemonicText(detail.meaningMnemonic, style = MaterialTheme.typography.bodyMedium)
                if (!detail.meaningHint.isNullOrBlank()) {
                    WkMnemonicText(detail.meaningHint, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (revealReading && hasReadings && !detail.readingMnemonic.isNullOrBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Reading mnemonic", style = MaterialTheme.typography.titleSmall)
                WkMnemonicText(detail.readingMnemonic, style = MaterialTheme.typography.bodyMedium)
                if (!detail.readingHint.isNullOrBlank()) {
                    WkMnemonicText(detail.readingHint, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (isVocabulary && detail.contextSentences.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Context sentences", style = MaterialTheme.typography.titleSmall)
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

private fun componentsLabel(type: SubjectType): String = when (type) {
    SubjectType.KANJI -> "Radicals"
    SubjectType.VOCABULARY, SubjectType.KANA_VOCABULARY -> "Kanji"
    SubjectType.RADICAL -> "Components"
}

@Composable
private fun VocabReadingRow(
    reading: String,
    pitchAccents: List<PitchAccent>,
    showPitchAccent: Boolean,
    hasAudio: Boolean,
    onPlayReading: ((String) -> Unit)?
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showPitchAccent && pitchAccents.isNotEmpty()) {
            PitchAccentReadingRow(reading = reading, pitchAccents = pitchAccents)
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
private fun ReadingTypeRow(label: String, readings: List<String>) {
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
