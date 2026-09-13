package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.shared.designsystem.components.SectionAccentHeader
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.shared.network.SubjectType

/** A section's related-subject ids resolved against the cache — distinguishes "this item genuinely
 *  has none" from "ids exist but their summaries haven't loaded yet". A bare `List<SubjectSummary>`
 *  (the previous shape) couldn't tell the two apart: both rendered as nothing. */
sealed interface RelatedSubjectsUiState {
    /** No related subject ids for this section at all — nothing to render, ever. */
    data object None : RelatedSubjectsUiState

    /** Ids exist, but none of their summaries are cached yet. */
    data object NotCached : RelatedSubjectsUiState

    data class Available(val subjects: List<SubjectSummary>) : RelatedSubjectsUiState
}

/** Everything [RelatedSubjectsSection] needs to render one related-subjects group — title, the
 *  accent color, and the resolved ids — fused into one value instead of three parameters a caller
 *  would otherwise have to keep in sync, the same way [ReadingPitchAccent] fuses a reading with its
 *  own pitch-accent answer. Built by [componentsGroup]/[visuallySimilarGroup]/[usedInGroup] rather
 *  than assembled inline at each call site, so the title/accent-type pairing for a given kind of
 *  group is defined once. */
data class RelatedSubjectsGroup(
    val title: String,
    val accentType: SubjectType,
    val subjects: RelatedSubjectsUiState
)

/** The "components" group (radicals for a kanji, kanji for a vocabulary word) — one level down the
 *  radical → kanji → vocabulary hierarchy from [subjectType], matching [componentsLabel]'s wording. */
fun componentsGroup(subjectType: SubjectType, componentSubjectIds: List<Long>, cache: Map<Long, SubjectSummary>): RelatedSubjectsGroup {
    val accentType = when (subjectType) {
        SubjectType.KANJI -> SubjectType.RADICAL
        SubjectType.VOCABULARY, SubjectType.KANA_VOCABULARY -> SubjectType.KANJI
        SubjectType.RADICAL -> SubjectType.RADICAL
    }
    return RelatedSubjectsGroup(
        title = componentsLabel(subjectType),
        accentType = accentType,
        subjects = componentSubjectIds.toRelatedSubjectsUiState(cache)
    )
}

/** The "visually similar" group — kanji only; `null` for any other subject type, so callers can
 *  skip rendering the section entirely rather than passing an always-empty group. */
fun visuallySimilarGroup(subjectType: SubjectType, visuallySimilarSubjectIds: List<Long>, cache: Map<Long, SubjectSummary>): RelatedSubjectsGroup? {
    if (subjectType != SubjectType.KANJI) return null
    return RelatedSubjectsGroup(
        title = "Visually similar",
        accentType = SubjectType.KANJI,
        subjects = visuallySimilarSubjectIds.toRelatedSubjectsUiState(cache)
    )
}

/** The "phonetically similar" group — vocabulary/kana-vocabulary only (the inverse restriction of
 *  [visuallySimilarGroup]); `null` for kanji/radicals, which have no reading to match on. */
fun phoneticallySimilarGroup(
    subjectType: SubjectType,
    phoneticallySimilarSubjectIds: List<Long>,
    cache: Map<Long, SubjectSummary>
): RelatedSubjectsGroup? {
    if (subjectType != SubjectType.VOCABULARY && subjectType != SubjectType.KANA_VOCABULARY) return null
    return RelatedSubjectsGroup(
        title = "Phonetically similar",
        accentType = subjectType,
        subjects = phoneticallySimilarSubjectIds.toRelatedSubjectsUiState(cache)
    )
}

/** The "used in" group — amalgamation subjects are always vocabulary words. */
fun usedInGroup(amalgamationSubjectIds: List<Long>, cache: Map<Long, SubjectSummary>): RelatedSubjectsGroup =
    RelatedSubjectsGroup(
        title = "Used in",
        accentType = SubjectType.VOCABULARY,
        subjects = amalgamationSubjectIds.toRelatedSubjectsUiState(cache)
    )

/** Resolves related subject ids against the cache — see [RelatedSubjectsUiState] for why this
 *  isn't a plain `mapNotNull` into a `List<SubjectSummary>` any more. */
fun List<Long>.toRelatedSubjectsUiState(cache: Map<Long, SubjectSummary>): RelatedSubjectsUiState {
    if (isEmpty()) return RelatedSubjectsUiState.None
    val resolved = mapNotNull { cache[it] }
    return if (resolved.isEmpty()) RelatedSubjectsUiState.NotCached else RelatedSubjectsUiState.Available(resolved)
}

/**
 * A titled, wrapping grid of [SubjectTile]s — owns the "ids, or a caption saying why not" decision
 * for a [RelatedSubjectsGroup] so every caller (subject detail sheet, lesson study card) renders the
 * same thing: nothing for [RelatedSubjectsUiState.None], a caption for
 * [RelatedSubjectsUiState.NotCached], the tiles otherwise.
 */
@Composable
fun RelatedSubjectsSection(
    group: RelatedSubjectsGroup,
    onSubjectClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (group.subjects is RelatedSubjectsUiState.None) return

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionAccentHeader(title = group.title, accent = subjectColor(group.accentType))
        when (val subjects = group.subjects) {
            RelatedSubjectsUiState.None -> Unit
            RelatedSubjectsUiState.NotCached -> {
                Text(
                    text = "Not loaded yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is RelatedSubjectsUiState.Available -> {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subjects.subjects.forEach { subject ->
                        SubjectTile(subject = subject, onClick = onSubjectClick)
                    }
                }
            }
        }
    }
}
