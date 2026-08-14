package com.crazyfluff.shellfstudy.shared.database.outbox

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class OutboxStatus { PENDING, FAILED_TERMINAL }

@Entity(tableName = "pending_review_submissions", indices = [Index("assignmentId")])
data class PendingReviewSubmissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assignmentId: Long,
    val subjectId: Long,
    val incorrectMeaningAnswers: Int,
    val incorrectReadingAnswers: Int,
    val gradedAt: String,
    val status: String = OutboxStatus.PENDING.name,
    val lastErrorMessage: String? = null
)

@Entity(tableName = "pending_lesson_starts", indices = [Index("assignmentId")])
data class PendingLessonStartEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assignmentId: Long,
    val subjectId: Long,
    val startedAt: String,
    val status: String = OutboxStatus.PENDING.name,
    val lastErrorMessage: String? = null
)
