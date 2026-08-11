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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazyfluff.shellfstudy.core.data.model.PitchAccent
import com.crazyfluff.shellfstudy.core.data.model.SubjectDetail
import com.crazyfluff.shellfstudy.core.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.core.designsystem.strokeorder.StrokeOrderSection
import com.crazyfluff.shellfstudy.core.designsystem.strokeorder.StrokeOrderUiState
import com.crazyfluff.shellfstudy.core.designsystem.theme.subjectTypeLabel
import com.crazyfluff.shellfstudy.core.designsystem.writing.WritingPracticeSection
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
    onPlayReading: ((String) -> Unit)? = null,
    strokeOrder: StrokeOrderUiState = StrokeOrderUiState.Unavailable
) {
    val revealMeaning = revealMode == DetailRevealMode.FULL || (isAnswered && questionType != DetailQuestionType.MEANING)
    val revealReading = revealMode == DetailRevealMode.FULL || (isAnswered && questionType != DetailQuestionType.READING)
    val hasReadings = detail.readings.isNotEmpty()
    val isVocabulary = detail.subjectType == SubjectType.VOCABULARY || detail.subjectType == SubjectType.KANA_VOCABULARY

    // Absorbs whatever this column's own scroll can't consume once it hits its top or bottom bound
    // — both leftover drag delta (onPostScroll) and leftover fling velocity (onPostFling) — before
    // either reaches the enclosing ModalBottomSheet's nested scroll connection. Two distinct bugs
    // showed up without this: (1) a fast fling that runs out of content (e.g. flinging back up to
    // the top) handed its leftover velocity to the sheet as a swipe-to-dismiss fling, closing the
    // sheet on a scroll gesture that never touched it; (2) a plain drag that hit a scroll bound
    // handed its leftover delta to the sheet's own drag state, which — combined with
    // rememberReluctantDismissSheetState's oversized thresholds — could settle a hair off its
    // Expanded anchor rather than exactly at rest, after which the sheet kept intercepting every
    // later scroll gesture trying to finish that settle, making the content look permanently
    // unscrollable no matter how many times you retried. Consuming both right here, at the boundary
    // closest to the scroll itself, means neither kind of leftover ever leaves this column's subtree.
    val scrollBoundaryConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                available

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .nestedScroll(scrollBoundaryConnection)
            .verticalScroll(rememberScrollState())
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
            Text(
                text = "Level ${detail.level} · ${subjectTypeLabel(detail.subjectType)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isVocabulary && detail.partsOfSpeech.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail.partsOfSpeech.forEach { part ->
                        AssistChip(onClick = {}, label = { Text(part) })
                    }
                }
            }
        }
        StrokeOrderSection(strokeOrder)
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
        val hasMeaningMnemonic = !detail.meaningMnemonic.isNullOrBlank()
        val hasReadingMnemonic = !detail.readingMnemonic.isNullOrBlank()

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
                        WkMnemonicText(detail.meaningMnemonic, style = MaterialTheme.typography.bodyMedium)
                        if (!detail.meaningHint.isNullOrBlank()) {
                            WkMnemonicText(detail.meaningHint, style = MaterialTheme.typography.bodySmall)
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
                        WkMnemonicText(detail.readingMnemonic, style = MaterialTheme.typography.bodyMedium)
                        if (!detail.readingHint.isNullOrBlank()) {
                            WkMnemonicText(detail.readingHint, style = MaterialTheme.typography.bodySmall)
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
