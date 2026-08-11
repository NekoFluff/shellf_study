package com.crazyfluff.shellfstudy.core.data.model

import com.crazyfluff.shellfstudy.core.network.SubjectType

data class WaniKaniUser(
    val username: String,
    val level: Int,
    val profileUrl: String
)

data class DashboardSummary(
    val lessonCount: Int,
    val reviewCount: Int
)

/** Progress toward WaniKani's level-up requirement: 90% of a level's kanji at Guru or higher. */
data class LevelUpProgress(
    val kanjiGuruedOrHigher: Int,
    val kanjiTotal: Int
)

data class ReviewItem(
    val assignmentId: Long,
    val subjectId: Long,
    val subjectType: SubjectType,
    val characters: String?,
    val level: Int,
    val srsStage: Int,
    val meanings: List<String>,
    val readings: List<String>,
    /** WaniKani's own official alternate meanings (e.g. "1" alongside "one") — distinct from the
     *  primary [meanings], but just as acceptable a grading answer. */
    val auxiliaryMeanings: List<String> = emptyList(),
    val pronunciationAudios: List<PronunciationAudio> = emptyList()
)

data class ReviewGrade(
    val meaningCorrect: Boolean,
    val readingCorrect: Boolean
) {
    val isFullyCorrect: Boolean get() = meaningCorrect && readingCorrect
}

data class LessonItem(
    val assignmentId: Long,
    val subjectId: Long,
    val subjectType: SubjectType,
    val characters: String?,
    val level: Int,
    val meanings: List<String>,
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
    val contextSentences: List<ContextSentence> = emptyList()
)

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
