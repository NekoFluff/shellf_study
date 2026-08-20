package com.crazyfluff.shellfstudy.shared.data.model

import com.crazyfluff.shellfstudy.shared.network.SubjectType
import kotlin.math.ceil
import kotlinx.serialization.Serializable

data class WaniKaniUser(
    val username: String,
    val level: Int
)

data class DashboardSummary(
    val lessonCount: Int,
    val reviewCount: Int
)

/** Progress toward WaniKani's level-up requirement: 90% of a level's kanji at Guru or higher. */
data class LevelUpProgress(
    val kanjiGuruedOrHigher: Int,
    val kanjiTotal: Int
) {
    /** WaniKani's actual level-up threshold: 90% of the level's kanji, rounded up. */
    val requiredCount: Int get() = ceil(kanjiTotal * 0.9).toInt()

    /** [kanjiTotal] > 0 guard avoids the vacuous "0 >= requiredCount(0) == 0" reading as ready
     *  during the no-data-yet default. */
    val isLevelUpReady: Boolean get() = kanjiTotal > 0 && kanjiGuruedOrHigher >= requiredCount
}

/** Common shape shared by [LessonItem] and [ReviewItem] — everything a quiz-session-summary row or
 *  the shared quiz-question screen needs to display a subject, without depending on either
 *  feature's full item type. */
interface QuizDisplayItem {
    val assignmentId: Long
    val characters: String?
    val characterImageUrl: String?
    val meanings: List<String>
    val subjectId: Long
    val subjectType: SubjectType
}

data class ReviewItem(
    override val assignmentId: Long,
    override val subjectId: Long,
    override val subjectType: SubjectType,
    override val characters: String?,
    override val characterImageUrl: String? = null,
    val level: Int,
    val srsStage: Int,
    override val meanings: List<String>,
    val readings: List<String>,
    /** WaniKani's own official alternate meanings (e.g. "1" alongside "one") — distinct from the
     *  primary [meanings], but just as acceptable a grading answer. */
    val auxiliaryMeanings: List<String> = emptyList(),
    val pronunciationAudios: List<PronunciationAudio> = emptyList(),
    /** Carried along so a review's rank change can be computed synchronously against
     *  AssignmentRepository's in-memory SRS-system cache, with no DB access needed on the
     *  per-answer critical path — see AssignmentRepository.computeReviewRankChange. */
    val srsSystemId: Long = 0
) : QuizDisplayItem

data class ReviewGrade(
    val meaningCorrect: Boolean,
    val readingCorrect: Boolean
) {
    val isFullyCorrect: Boolean get() = meaningCorrect && readingCorrect
}

data class LessonItem(
    override val assignmentId: Long,
    override val subjectId: Long,
    override val subjectType: SubjectType,
    override val characters: String?,
    override val characterImageUrl: String? = null,
    val level: Int,
    /** The subject's position within its level's lesson order, per WaniKani's own intended
     *  sequencing — used as the tie-break when [com.crazyfluff.shellfstudy.shared.feature.lesson.LessonPrioritizer]
     *  reorders a level's items. */
    val lessonPosition: Int = 0,
    override val meanings: List<String>,
    val readings: List<String>,
    val meaningMnemonic: String?,
    val readingMnemonic: String?,
    val auxiliaryMeanings: List<String> = emptyList(),
    val meaningHint: String? = null,
    val readingHint: String? = null,
    val onyomiReadings: List<String> = emptyList(),
    val kunyomiReadings: List<String> = emptyList(),
    val nanoriReadings: List<String> = emptyList(),
    val partsOfSpeech: List<String> = emptyList(),
    val pronunciationAudios: List<PronunciationAudio> = emptyList(),
    val contextSentences: List<ContextSentence> = emptyList(),
    val componentSubjectIds: List<Long> = emptyList(),
    val amalgamationSubjectIds: List<Long> = emptyList(),
    val visuallySimilarSubjectIds: List<Long> = emptyList(),
    /** Carried along so AssignmentRepository.applyOptimisticLessonStart can resolve the SRS
     *  system's starting stage without an extra DB round trip. */
    val srsSystemId: Long = 0
) : QuizDisplayItem

/** A subject as shown in search results. [srsStage] is null if no assignment exists yet. */
data class SubjectSummary(
    val subjectId: Long,
    val subjectType: SubjectType,
    val characters: String?,
    val level: Int,
    val meanings: List<String>,
    val readings: List<String>,
    val srsStage: Int? = null,
    val characterImageUrl: String? = null
)

/** One row of a lesson/review session-complete screen's "slowest answers" card — a single graded
 *  answer, already reduced to display-ready fields. [Serializable] so the same row can be persisted
 *  as part of a [com.crazyfluff.shellfstudy.shared.data.LastSessionSummary] for later revisiting. */
@Serializable
data class SessionAnswerRow(
    val label: String,
    val typeLabel: String,
    val elapsedMs: Long,
    val isCorrect: Boolean,
    val subjectId: Long,
    val subjectType: SubjectType
)

/** One chip of a lesson/review session-complete screen's "missed items" card. */
@Serializable
data class SessionMissedItemRow(val label: String, val subjectId: Long, val subjectType: SubjectType)
