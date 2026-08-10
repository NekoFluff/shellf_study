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

data class ReviewItem(
    val assignmentId: Long,
    val subjectId: Long,
    val subjectType: SubjectType,
    val characters: String?,
    val level: Int,
    val srsStage: Int,
    val meanings: List<String>,
    val readings: List<String>
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
    val readingMnemonic: String?
)

/** A subject as shown in search results — no assignment/SRS context, just its content. */
data class SubjectSummary(
    val subjectId: Long,
    val subjectType: SubjectType,
    val characters: String?,
    val level: Int,
    val meanings: List<String>,
    val readings: List<String>
)
