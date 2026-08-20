package com.crazyfluff.shellfstudy.shared.database.outbox

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Insert
    suspend fun insertReviewSubmission(entity: PendingReviewSubmissionEntity): Long

    @Insert
    suspend fun insertLessonStart(entity: PendingLessonStartEntity): Long

    @Query("SELECT * FROM pending_review_submissions WHERE status = 'PENDING' ORDER BY id ASC")
    suspend fun getPendingReviewSubmissions(): List<PendingReviewSubmissionEntity>

    @Query("SELECT * FROM pending_lesson_starts WHERE status = 'PENDING' ORDER BY id ASC")
    suspend fun getPendingLessonStarts(): List<PendingLessonStartEntity>

    @Delete
    suspend fun deleteReviewSubmission(entity: PendingReviewSubmissionEntity)

    @Delete
    suspend fun deleteLessonStart(entity: PendingLessonStartEntity)

    @Query("UPDATE pending_review_submissions SET status = 'FAILED_TERMINAL', lastErrorMessage = :message WHERE id = :id")
    suspend fun markReviewSubmissionTerminal(id: Long, message: String?)

    @Query("UPDATE pending_lesson_starts SET status = 'FAILED_TERMINAL', lastErrorMessage = :message WHERE id = :id")
    suspend fun markLessonStartTerminal(id: Long, message: String?)

    @Query("SELECT COUNT(*) FROM pending_review_submissions WHERE status = 'PENDING'")
    fun observePendingReviewSubmissionCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_lesson_starts WHERE status = 'PENDING'")
    fun observePendingLessonStartCount(): Flow<Int>

    @Query("DELETE FROM pending_review_submissions")
    suspend fun clearReviewSubmissions()

    @Query("DELETE FROM pending_lesson_starts")
    suspend fun clearLessonStarts()
}
