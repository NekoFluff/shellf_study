package com.crazyfluff.shellfstudy.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserData(
    val id: String,
    val username: String,
    val level: Int,
    @SerialName("profile_url") val profileUrl: String,
    @SerialName("started_at") val startedAt: String
)

@Serializable
data class SummaryData(
    val lessons: List<SummaryEntry> = emptyList(),
    val reviews: List<SummaryEntry> = emptyList(),
    @SerialName("next_reviews_at") val nextReviewsAt: String? = null
) {
    /** Subjects immediately available right now (first entry's available_at is always "now"). */
    val availableLessonSubjectIds: List<Long> get() = lessons.firstOrNull()?.subjectIds ?: emptyList()
    val availableReviewSubjectIds: List<Long> get() = reviews.firstOrNull()?.subjectIds ?: emptyList()
}

@Serializable
data class SummaryEntry(
    @SerialName("available_at") val availableAt: String,
    @SerialName("subject_ids") val subjectIds: List<Long> = emptyList()
)

@Serializable
data class AssignmentData(
    @SerialName("created_at") val createdAt: String,
    @SerialName("subject_id") val subjectId: Long,
    @SerialName("subject_type") val subjectType: String,
    @SerialName("srs_stage") val srsStage: Int,
    @SerialName("unlocked_at") val unlockedAt: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("passed_at") val passedAt: String? = null,
    @SerialName("burned_at") val burnedAt: String? = null,
    @SerialName("available_at") val availableAt: String? = null,
    @SerialName("resurrected_at") val resurrectedAt: String? = null,
    val hidden: Boolean = false
)

@Serializable
data class SubjectData(
    @SerialName("created_at") val createdAt: String,
    val level: Int,
    val slug: String,
    val characters: String? = null,
    val meanings: List<MeaningData> = emptyList(),
    val readings: List<ReadingData> = emptyList(),
    @SerialName("document_url") val documentUrl: String? = null,
    @SerialName("meaning_mnemonic") val meaningMnemonic: String? = null,
    @SerialName("reading_mnemonic") val readingMnemonic: String? = null
)

@Serializable
data class MeaningData(
    val meaning: String,
    val primary: Boolean = false,
    @SerialName("accepted_meaning") val acceptedMeaning: Boolean = true
)

@Serializable
data class ReadingData(
    val reading: String,
    val primary: Boolean = false,
    @SerialName("accepted_reading") val acceptedReading: Boolean = true
)

@Serializable
data class ReviewSubmissionRequest(val review: ReviewSubmissionBody)

@Serializable
data class ReviewSubmissionBody(
    @SerialName("assignment_id") val assignmentId: Long,
    @SerialName("incorrect_meaning_answers") val incorrectMeaningAnswers: Int,
    @SerialName("incorrect_reading_answers") val incorrectReadingAnswers: Int
)

@Serializable
data class ReviewResultData(
    @SerialName("assignment_id") val assignmentId: Long,
    @SerialName("subject_id") val subjectId: Long,
    @SerialName("starting_srs_stage") val startingSrsStage: Int,
    @SerialName("ending_srs_stage") val endingSrsStage: Int,
    @SerialName("incorrect_meaning_answers") val incorrectMeaningAnswers: Int,
    @SerialName("incorrect_reading_answers") val incorrectReadingAnswers: Int,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class StartAssignmentRequest(val assignment: StartAssignmentBody = StartAssignmentBody())

@Serializable
data class StartAssignmentBody(@SerialName("started_at") val startedAt: String? = null)

/** Subject "object" type as returned in AssignmentData.subjectType / WkResourceItem.objectType. */
enum class SubjectType {
    RADICAL, KANJI, VOCABULARY;

    companion object {
        fun fromWkString(value: String): SubjectType = when (value) {
            "radical" -> RADICAL
            "kanji" -> KANJI
            "vocabulary" -> VOCABULARY
            else -> VOCABULARY
        }
    }
}
