package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.data.model.SubjectAssignmentStats
import com.crazyfluff.shellfstudy.shared.data.model.SubjectDetail
import com.crazyfluff.shellfstudy.shared.data.model.SubjectReviewStats
import com.crazyfluff.shellfstudy.shared.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.shared.designsystem.strokeorder.StrokeOrderSection
import com.crazyfluff.shellfstudy.shared.designsystem.strokeorder.StrokeOrderUiState
import com.crazyfluff.shellfstudy.shared.designsystem.text.AkebiSelectableContainer
import com.crazyfluff.shellfstudy.shared.designsystem.theme.SrsStageChip
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectTypeLabel
import com.crazyfluff.shellfstudy.shared.designsystem.writing.WritingPracticeSection
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType
import com.crazyfluff.shellfstudy.shared.util.formatAnswerList

/** Whether the sheet shows everything (browse/study contexts) or hides the currently-tested field (mid-quiz). */
enum class DetailRevealMode { FULL, HIDE_UNTIL_ANSWERED }

enum class DetailQuestionType { MEANING, READING }

/** True when the sheet's own "Show all" override should be offered — i.e. this field is still
 *  genuinely gated by [DetailRevealMode.HIDE_UNTIL_ANSWERED] and hasn't already been forced open
 *  by drilling into a related subject or a prior tap on this button. */
fun canOfferForceReveal(revealMode: DetailRevealMode, hasBackStack: Boolean, forceRevealAll: Boolean): Boolean =
    revealMode == DetailRevealMode.HIDE_UNTIL_ANSWERED && !hasBackStack && !forceRevealAll

/** The reveal mode actually applied this render: forced to [DetailRevealMode.FULL] once the user
 *  has drilled into a related subject (nothing to hide once you're browsing, not being quizzed) or
 *  tapped "Show all" — otherwise whatever the caller asked for. */
fun resolveEffectiveRevealMode(revealMode: DetailRevealMode, hasBackStack: Boolean, forceRevealAll: Boolean): DetailRevealMode =
    if (forceRevealAll || hasBackStack) DetailRevealMode.FULL else revealMode

fun QuestionType.toDetailQuestionType(): DetailQuestionType = when (this) {
    QuestionType.MEANING -> DetailQuestionType.MEANING
    QuestionType.READING -> DetailQuestionType.READING
}

/** Height of the subject-detail sheet's always-present grab strip in its collapsed "peek" state —
 *  used by callers (Lesson/Review's quiz content, the sheet itself) to reserve room below their
 *  own content so the strip doesn't cover it. */
val SubjectDetailHandleHeight = 56.dp

object SubjectDetailTestTags {
    const val SHEET_ROOT = "subject_detail_sheet_root"
    const val CONTENT_ROOT = "subject_detail_content_root"
    const val PEEK_HANDLE = "subject_detail_peek_handle"
    const val AUXILIARY_MEANINGS_TEXT = "subject_detail_auxiliary_meanings_text"
}

private fun List<Long>.resolve(relatedSubjects: Map<Long, SubjectSummary>): List<SubjectSummary> =
    mapNotNull { relatedSubjects[it] }

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
    showStrokeOrder: Boolean = true,
    assignmentStats: SubjectAssignmentStats? = null,
    reviewStats: SubjectReviewStats? = null,
    initialScrollOffset: Int = 0,
    onScrollPositionChanged: (Int) -> Unit = {}
) {
    val revealMeaning = revealMode == DetailRevealMode.FULL || (isAnswered && questionType == DetailQuestionType.MEANING)
    val revealReading = revealMode == DetailRevealMode.FULL || (isAnswered && questionType == DetailQuestionType.READING)
    val hasReadings = detail.readings.isNotEmpty()
    val isVocabulary = detail.subjectType == SubjectType.VOCABULARY || detail.subjectType == SubjectType.KANA_VOCABULARY

    // Jump to initialScrollOffset (not just remembered fresh) on every subject change — an
    // effect-driven jump animates cleanly rather than discarding in-flight gesture state on the
    // same frame the way keying rememberScrollState() to subjectId would. Callers reset this to 0
    // when drilling into a related subject, and restore a recorded offset when going back.
    val scrollState = rememberScrollState()
    LaunchedEffect(detail.subjectId) {
        scrollState.scrollTo(initialScrollOffset)
    }
    LaunchedEffect(detail.subjectId) {
        snapshotFlow { scrollState.value }.collect { onScrollPositionChanged(it) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .testTag(SubjectDetailTestTags.CONTENT_ROOT),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SubjectHeadline(detail, assignmentStats, isVocabulary)
        SubjectWritingZone(strokeOrder, autoPlayStrokeOrder, showStrokeOrder, detail.subjectId)
        SubjectComponentsSection(detail, relatedSubjects, onRelatedSubjectClick)
        SubjectMeaningZone(detail, revealMeaning)
        SubjectReadingZone(
            detail = detail,
            revealReading = revealReading,
            hasReadings = hasReadings,
            isVocabulary = isVocabulary,
            showPitchAccent = showPitchAccent,
            onPlayReading = onPlayReading,
            showDividerAbove = revealMeaning
        )
        SubjectContextSentencesSection(detail, isVocabulary)
        SubjectVisuallySimilarSection(detail, relatedSubjects, onRelatedSubjectClick)
        SubjectUsedInSection(detail, relatedSubjects, onRelatedSubjectClick)
        SubjectStatsZone(assignmentStats, reviewStats)
    }
}

// Headline: title, level/type subtitle, and part-of-speech tags read as one tight cluster —
// they're all "what is this" at a glance, so they sit closer together than the sections below.
@Composable
private fun SubjectHeadline(detail: SubjectDetail, assignmentStats: SubjectAssignmentStats?, isVocabulary: Boolean) {
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
            if (assignmentStats != null) {
                SrsStageChip(assignmentStats.srsStage)
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
}

@Composable
private fun SubjectWritingZone(
    strokeOrder: StrokeOrderUiState,
    autoPlayStrokeOrder: Boolean,
    showStrokeOrder: Boolean,
    resetKey: Long
) {
    if (!showStrokeOrder) return
    StrokeOrderSection(strokeOrder, autoPlay = autoPlayStrokeOrder)
    WritingPracticeSection(strokeOrder = strokeOrder, resetKey = resetKey)
}

@Composable
private fun SubjectComponentsSection(
    detail: SubjectDetail,
    relatedSubjects: Map<Long, SubjectSummary>,
    onRelatedSubjectClick: (Long) -> Unit
) {
    RelatedSubjectsSection(
        title = componentsLabel(detail.subjectType),
        subjects = detail.componentSubjectIds.resolve(relatedSubjects),
        onSubjectClick = onRelatedSubjectClick
    )
}

// Meaning and Reading are deliberately structured the same way — title, then its own mnemonic
// right underneath — so the two read as parallel, equal-weight sections rather than one standing
// out from the other. Plain sections (no card) keep them visually lightweight for a view learners
// scroll through constantly; a divider between them is enough separation.
@Composable
private fun SubjectMeaningZone(detail: SubjectDetail, revealMeaning: Boolean) {
    if (!revealMeaning) return
    val meaningMnemonic = detail.meaningMnemonic
    val meaningHint = detail.meaningHint
    val hasMeaningMnemonic = !meaningMnemonic.isNullOrBlank()

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Meaning",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(detail.meanings.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
            if (detail.auxiliaryMeanings.isNotEmpty()) {
                AuxiliaryMeaningsText(detail.auxiliaryMeanings, resetKey = detail.subjectId)
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

private enum class ReadingDisplayStyle { KANJI_BREAKDOWN, VOCABULARY, PLAIN }

private fun readingDisplayStyle(
    subjectType: SubjectType,
    hasReadingBreakdown: Boolean,
    isVocabulary: Boolean
): ReadingDisplayStyle = when {
    subjectType == SubjectType.KANJI && hasReadingBreakdown -> ReadingDisplayStyle.KANJI_BREAKDOWN
    isVocabulary -> ReadingDisplayStyle.VOCABULARY
    else -> ReadingDisplayStyle.PLAIN
}

@Composable
private fun KanjiReadingBreakdown(detail: SubjectDetail) {
    if (detail.onyomiReadings.isNotEmpty()) {
        ReadingTypeRow(label = "On'yomi", readings = detail.onyomiReadings)
    }
    if (detail.kunyomiReadings.isNotEmpty()) {
        ReadingTypeRow(label = "Kun'yomi", readings = detail.kunyomiReadings)
    }
    if (detail.nanoriReadings.isNotEmpty()) {
        ReadingTypeRow(label = "Nanori", readings = detail.nanoriReadings)
    }
}

@Composable
private fun VocabularyReadingList(detail: SubjectDetail, showPitchAccent: Boolean, onPlayReading: ((String) -> Unit)?) {
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
}

@Composable
private fun SubjectReadingZone(
    detail: SubjectDetail,
    revealReading: Boolean,
    hasReadings: Boolean,
    isVocabulary: Boolean,
    showPitchAccent: Boolean,
    onPlayReading: ((String) -> Unit)?,
    showDividerAbove: Boolean
) {
    if (!(revealReading && hasReadings)) return
    val hasReadingBreakdown =
        detail.onyomiReadings.isNotEmpty() || detail.kunyomiReadings.isNotEmpty() || detail.nanoriReadings.isNotEmpty()
    val readingMnemonic = detail.readingMnemonic
    val readingHint = detail.readingHint
    val hasReadingMnemonic = !readingMnemonic.isNullOrBlank()

    if (showDividerAbove) HorizontalDivider()
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Reading",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            when (readingDisplayStyle(detail.subjectType, hasReadingBreakdown, isVocabulary)) {
                ReadingDisplayStyle.KANJI_BREAKDOWN -> KanjiReadingBreakdown(detail)
                ReadingDisplayStyle.VOCABULARY -> VocabularyReadingList(detail, showPitchAccent, onPlayReading)
                ReadingDisplayStyle.PLAIN -> Text(detail.readings.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
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

@Composable
private fun SubjectContextSentencesSection(detail: SubjectDetail, isVocabulary: Boolean) {
    if (!isVocabulary || detail.contextSentences.isEmpty()) return
    HorizontalDivider()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionEyebrow("Context sentences")
        // 20dp between example sentences (vs. 2dp between a sentence's own JP/EN pair) so
        // each example reads as its own distinct card of information while scanning.
        AkebiSelectableContainer {
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

@Composable
private fun SubjectVisuallySimilarSection(
    detail: SubjectDetail,
    relatedSubjects: Map<Long, SubjectSummary>,
    onRelatedSubjectClick: (Long) -> Unit
) {
    if (detail.subjectType != SubjectType.KANJI) return
    RelatedSubjectsSection(
        title = "Visually similar",
        subjects = detail.visuallySimilarSubjectIds.resolve(relatedSubjects),
        onSubjectClick = onRelatedSubjectClick
    )
}

@Composable
private fun SubjectUsedInSection(
    detail: SubjectDetail,
    relatedSubjects: Map<Long, SubjectSummary>,
    onRelatedSubjectClick: (Long) -> Unit
) {
    RelatedSubjectsSection(
        title = "Used in",
        subjects = detail.amalgamationSubjectIds.resolve(relatedSubjects),
        onSubjectClick = onRelatedSubjectClick
    )
}

@Composable
private fun SubjectStatsZone(assignmentStats: SubjectAssignmentStats?, reviewStats: SubjectReviewStats?) {
    if (assignmentStats == null) return
    HorizontalDivider()
    SubjectStatsSection(assignmentStats = assignmentStats, reviewStats = reviewStats)
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
            // weight(1f, fill = false): the diagram sizes itself to its content, this just caps
            // its max width for unusually long readings so the play button can't be pushed off-screen.
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

/** Auxiliary meanings truncate to a "+N more" summary the same way the review screen's answer
 *  feedback does (see [formatAnswerList]) — tapping toggles the full list back open and closed,
 *  rather than always spelling out every whitelisted alternate meaning up front. Shared by every
 *  screen that shows a subject's/item's auxiliary meanings (subject detail, lesson). */
@Composable
fun AuxiliaryMeaningsText(auxiliaryMeanings: List<String>, resetKey: Any?) {
    var isExpanded by remember(resetKey) { mutableStateOf(false) }
    val display = formatAnswerList(auxiliaryMeanings.joinToString(", "), expanded = isExpanded)
    Text(
        text = display.text,
        style = MaterialTheme.typography.bodySmall,
        color = if (display.hasMore) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (isExpanded) Int.MAX_VALUE else 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .testTag(SubjectDetailTestTags.AUXILIARY_MEANINGS_TEXT)
            .then(if (display.hasMore) Modifier.clickable { isExpanded = !isExpanded } else Modifier)
    )
}
