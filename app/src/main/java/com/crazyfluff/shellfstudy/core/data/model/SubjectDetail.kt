package com.crazyfluff.shellfstudy.core.data.model

import com.crazyfluff.shellfstudy.core.network.SubjectType

/**
 * The full "everything we know about this subject" model backing the shared subject detail view.
 * [srsStage] deliberately isn't here — it lives on assignments, not subjects, so callers that have
 * it (Review/Lesson items) pass it into the detail view directly instead of this model joining
 * against assignments to fetch it.
 */
data class SubjectDetail(
    val subjectId: Long,
    val subjectType: SubjectType,
    val characters: String?,
    val characterImageUrl: String?,
    val level: Int,
    val meanings: List<String>,
    val auxiliaryMeanings: List<String>,
    val readings: List<String>,
    /** Kanji-only reading breakdown — empty for vocabulary/radicals, which don't have this distinction. */
    val onyomiReadings: List<String> = emptyList(),
    val kunyomiReadings: List<String> = emptyList(),
    val nanoriReadings: List<String> = emptyList(),
    val documentUrl: String?,
    val meaningMnemonic: String?,
    val meaningHint: String?,
    val readingMnemonic: String?,
    val readingHint: String?,
    val partsOfSpeech: List<String>,
    val contextSentences: List<ContextSentence>,
    val componentSubjectIds: List<Long>,
    val amalgamationSubjectIds: List<Long>,
    val visuallySimilarSubjectIds: List<Long>
)

data class ContextSentence(val japanese: String, val english: String)
