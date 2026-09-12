package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.model.SubjectAssignmentStats
import com.crazyfluff.shellfstudy.shared.data.model.SubjectDetail
import com.crazyfluff.shellfstudy.shared.data.model.SubjectReviewStats
import com.crazyfluff.shellfstudy.shared.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.shared.designsystem.components.SectionTitle
import com.crazyfluff.shellfstudy.shared.designsystem.strokeorder.StrokeOrderSection
import com.crazyfluff.shellfstudy.shared.designsystem.strokeorder.StrokeOrderUiState
import com.crazyfluff.shellfstudy.shared.designsystem.text.AkebiSelectableContainer
import com.crazyfluff.shellfstudy.shared.designsystem.text.ContextSentenceRow
import com.crazyfluff.shellfstudy.shared.designsystem.text.rememberShareText
import com.crazyfluff.shellfstudy.shared.designsystem.theme.SrsStageChip
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectTypeLabel
import com.crazyfluff.shellfstudy.shared.designsystem.writing.WritingPracticeSection
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType

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

    /** The headerless meaning and reading, tagged separately so a test can assert each is reachable
     *  without scrolling and where it sits (see [SubjectMeaningAnswer], [SubjectReadingAnswer]). */
    const val MEANING_ANSWER = "subject_detail_meaning_answer"
    const val READING_ANSWER = "subject_detail_reading_answer"
}

private fun List<Long>.resolve(relatedSubjects: Map<Long, SubjectSummary>): List<SubjectSummary> =
    mapNotNull { relatedSubjects[it] }

/**
 * The shared "everything about this subject" content, used from Review (gated), Lesson, Search,
 * and the Dashboard's level-progress breakdown. Section order puts the answer first: the level/type
 * line with its SRS chip (pinned to the trailing edge, above the characters), the subject's
 * characters, then the headerless meaning underneath (see [SubjectMeaningAnswer]), the headerless
 * reading (see [SubjectReadingAnswer]), the part-of-speech tags, the writing zone and components, the
 * mnemonics (see [SubjectMnemonicZone]), then context sentences, visually similar, used-in, and stats.
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
    restrictAudioToMp3: Boolean = false,
    strokeOrder: StrokeOrderUiState = StrokeOrderUiState.Unavailable,
    autoPlayStrokeOrder: Boolean = true,
    showStrokeOrder: Boolean = true,
    hideContextSentenceTranslations: Boolean = true,
    assignmentStats: SubjectAssignmentStats? = null,
    reviewStats: SubjectReviewStats? = null,
    initialScrollOffset: Int = 0,
    onScrollPositionChanged: (Int) -> Unit = {}
) {
    val revealMeaning = revealMode == DetailRevealMode.FULL || (isAnswered && questionType == DetailQuestionType.MEANING)
    val revealReading = revealMode == DetailRevealMode.FULL || (isAnswered && questionType == DetailQuestionType.READING)
    val isVocabulary = detail.subjectType == SubjectType.VOCABULARY || detail.subjectType == SubjectType.KANA_VOCABULARY

    // One scroll state per (subject, arrival offset), seeded with that offset. This is what makes
    // a subject switch atomic on the render side: when the ViewModel publishes the new subject
    // (see SubjectDetailViewModel's coherence gate), the content remounts with a fresh ScrollState
    // that already starts at the right position — 0 for a drill-down/open, the recorded offset when
    // navigating back — so content and scroll position change in the very first frame together,
    // with no frame at the previous subject's offset and no post-frame jump effect. ScrollState
    // handles an offset that exceeds the content height on first layout (maxValue starts at
    // Int.MAX_VALUE and clamps value down once the content is measured). Keying on the offset too
    // means re-emissions of the same subject at the same arrival offset leave the user's live
    // scroll position untouched.
    val scrollState = remember(detail.subjectId, initialScrollOffset) { ScrollState(initialScrollOffset) }
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
        // The reading is part of the word's identity cluster — it stays right under the meaning
        // (see SubjectHeadline's tighter meaning/reading spacing) and above the writing zone, so a
        // learner checking an answer never scrolls past a stroke-order diagram for it.
        SubjectHeadline(detail, assignmentStats, isVocabulary, showMeaning = revealMeaning, showReading = revealReading) {
            SubjectReadingAnswer(
                subjectType = detail.subjectType,
                readings = detail.readings,
                onyomiReadings = detail.onyomiReadings,
                kunyomiReadings = detail.kunyomiReadings,
                nanoriReadings = detail.nanoriReadings,
                pronunciationAudios = detail.pronunciationAudios,
                pitchAccents = detail.pitchAccents,
                showPitchAccent = showPitchAccent,
                restrictAudioToMp3 = restrictAudioToMp3
            )
        }
        SubjectWritingZone(strokeOrder, autoPlayStrokeOrder, showStrokeOrder, detail.subjectId)
        SubjectComponentsSection(detail, relatedSubjects, onRelatedSubjectClick)
        SubjectMnemonicZone(
            meaningMnemonic = detail.meaningMnemonic,
            meaningHint = detail.meaningHint,
            readingMnemonic = detail.readingMnemonic,
            readingHint = detail.readingHint,
            showMeaning = revealMeaning,
            showReading = revealReading
        )
        SubjectContextSentencesSection(detail, isVocabulary, hideContextSentenceTranslations)
        SubjectVisuallySimilarSection(detail, relatedSubjects, onRelatedSubjectClick)
        SubjectUsedInSection(detail, relatedSubjects, onRelatedSubjectClick)
        SubjectStatsZone(assignmentStats, reviewStats)
    }
}

// Headline: the level/type line with its SRS chip sits above the characters, pinned to the trailing
// edge (metadata about the character, off to the side rather than competing with it for the
// strongest spot on the page), then the glyph, then the meaning directly underneath it — the
// strongest place on the page, and the first thing a learner checking an answer looks for — then
// [reading], then the part-of-speech tags. One cluster, so its parts sit at 8dp from each other
// instead of at the page's section spacing — except the meaning and reading themselves, which sit at
// a tighter 2dp: they read as one answer pair, not two separate facts about the word.
@Composable
private fun SubjectHeadline(
    detail: SubjectDetail,
    assignmentStats: SubjectAssignmentStats?,
    isVocabulary: Boolean,
    showMeaning: Boolean,
    showReading: Boolean,
    reading: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Level ${detail.level} · ${subjectTypeLabel(detail.subjectType)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (assignmentStats != null) {
                SrsStageChip(assignmentStats.srsStage)
            }
        }
        SubjectGlyph(
            characters = detail.characters,
            characterImageUrl = detail.characterImageUrl,
            subjectType = detail.subjectType,
            size = 80.dp,
            // Trimmed box: the ink only fills ~55% of a square 80dp box, and the empty band under it
            // is what pushed the meaning away. Trimming the box closes that gap without shrinking the
            // character.
            boxHeight = headlineGlyphBoxHeight(80.dp)
        )
        if (showMeaning || showReading) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (showMeaning) {
                    SubjectMeaningAnswer(
                        meanings = detail.meanings,
                        auxiliaryMeanings = detail.auxiliaryMeanings,
                        resetKey = detail.subjectId
                    )
                }
                if (showReading) {
                    reading()
                }
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

// Meaning/reading rendering lives in SubjectAnswerSections.kt, next to the mnemonic zone that follows
// it in the page — shared with the lesson study card, which used to carry its own copy of both.

@Composable
private fun SubjectContextSentencesSection(
    detail: SubjectDetail,
    isVocabulary: Boolean,
    hideTranslations: Boolean
) {
    if (!isVocabulary || detail.contextSentences.isEmpty()) return
    val shareText = rememberShareText()
    HorizontalDivider()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Context sentences")
        // 20dp between example sentences (vs. 2dp between a sentence's own JP/EN pair) so
        // each example reads as its own distinct card of information while scanning.
        AkebiSelectableContainer {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                detail.contextSentences.forEach { sentence ->
                    ContextSentenceRow(sentence, onShare = shareText, hideTranslation = hideTranslations)
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
