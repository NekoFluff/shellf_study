package com.crazyfluff.shellfstudy.shared.network

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
    @SerialName("hidden_at") val hiddenAt: String? = null,
    val level: Int,
    val slug: String,
    val characters: String? = null,
    @SerialName("lesson_position") val lessonPosition: Int = 0,
    @SerialName("spaced_repetition_system_id") val srsSystemId: Long = 0,
    val meanings: List<MeaningData> = emptyList(),
    @SerialName("auxiliary_meanings") val auxiliaryMeanings: List<AuxiliaryMeaningData> = emptyList(),
    val readings: List<ReadingData> = emptyList(),
    @SerialName("document_url") val documentUrl: String? = null,
    @SerialName("meaning_mnemonic") val meaningMnemonic: String? = null,
    @SerialName("meaning_hint") val meaningHint: String? = null,
    @SerialName("reading_mnemonic") val readingMnemonic: String? = null,
    @SerialName("reading_hint") val readingHint: String? = null,
    @SerialName("component_subject_ids") val componentSubjectIds: List<Long> = emptyList(),
    @SerialName("amalgamation_subject_ids") val amalgamationSubjectIds: List<Long> = emptyList(),
    @SerialName("visually_similar_subject_ids") val visuallySimilarSubjectIds: List<Long> = emptyList(),
    @SerialName("parts_of_speech") val partsOfSpeech: List<String> = emptyList(),
    @SerialName("context_sentences") val contextSentences: List<ContextSentenceData> = emptyList(),
    @SerialName("character_images") val characterImages: List<CharacterImageData> = emptyList(),
    @SerialName("pronunciation_audios") val pronunciationAudios: List<PronunciationAudioData> = emptyList()
)

@Serializable
data class MeaningData(
    val meaning: String,
    val primary: Boolean = false,
    @SerialName("accepted_answer") val acceptedMeaning: Boolean = true
)

@Serializable
data class AuxiliaryMeaningData(
    val meaning: String,
    val type: String
)

@Serializable
data class ContextSentenceData(
    val en: String,
    val ja: String
)

@Serializable
data class CharacterImageData(
    val url: String,
    @SerialName("content_type") val contentType: String,
    val metadata: CharacterImageMetadataData? = null
)

@Serializable
data class CharacterImageMetadataData(
    val style: String? = null
)

@Serializable
data class PronunciationAudioData(
    val url: String,
    @SerialName("content_type") val contentType: String,
    val metadata: PronunciationAudioMetadataData? = null
)

@Serializable
data class PronunciationAudioMetadataData(
    val gender: String? = null,
    @SerialName("source_id") val sourceId: Long? = null,
    val pronunciation: String? = null,
    @SerialName("voice_actor_id") val voiceActorId: Long? = null,
    @SerialName("voice_actor_name") val voiceActorName: String? = null,
    @SerialName("voice_description") val voiceDescription: String? = null
)

@Serializable
data class ReadingData(
    val reading: String,
    val primary: Boolean = false,
    @SerialName("accepted_answer") val acceptedReading: Boolean = true,
    /** Kanji-only: "onyomi" | "kunyomi" | "nanori". Absent on vocabulary/radical readings. */
    val type: String? = null
)

@Serializable
data class SpacedRepetitionSystemData(
    val name: String,
    val description: String,
    @SerialName("unlocking_stage_position") val unlockingStagePosition: Int,
    @SerialName("starting_stage_position") val startingStagePosition: Int,
    @SerialName("passing_stage_position") val passingStagePosition: Int,
    @SerialName("burning_stage_position") val burningStagePosition: Int,
    val stages: List<SrsStageData> = emptyList()
)

@Serializable
data class SrsStageData(
    val position: Int,
    val interval: Long? = null,
    @SerialName("interval_unit") val intervalUnit: String? = null
)

@Serializable
data class ReviewStatisticData(
    @SerialName("created_at") val createdAt: String,
    @SerialName("subject_id") val subjectId: Long,
    @SerialName("subject_type") val subjectType: String,
    @SerialName("meaning_correct") val meaningCorrect: Int = 0,
    @SerialName("meaning_incorrect") val meaningIncorrect: Int = 0,
    @SerialName("meaning_max_streak") val meaningMaxStreak: Int = 0,
    @SerialName("meaning_current_streak") val meaningCurrentStreak: Int = 0,
    @SerialName("reading_correct") val readingCorrect: Int = 0,
    @SerialName("reading_incorrect") val readingIncorrect: Int = 0,
    @SerialName("reading_max_streak") val readingMaxStreak: Int = 0,
    @SerialName("reading_current_streak") val readingCurrentStreak: Int = 0,
    @SerialName("percentage_correct") val percentageCorrect: Int = 0,
    val hidden: Boolean = false
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

@Serializable
data class LevelProgressionData(
    val level: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("unlocked_at") val unlockedAt: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("passed_at") val passedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("abandoned_at") val abandonedAt: String? = null
)

/** Subject "object" type as returned in AssignmentData.subjectType / WkResourceItem.objectType. */
enum class SubjectType {
    RADICAL, KANJI, VOCABULARY, KANA_VOCABULARY;

    companion object {
        fun fromWkString(value: String): SubjectType = when (value) {
            "radical" -> RADICAL
            "kanji" -> KANJI
            "vocabulary" -> VOCABULARY
            "kana_vocabulary" -> KANA_VOCABULARY
            else -> VOCABULARY
        }
    }
}
