package com.crazyfluff.shellfstudy.core.database.reviewhistory

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One completed review submission — the only local source of streak/reviews-completed history. */
@Entity(tableName = "review_log", indices = [Index("reviewedAt"), Index("subjectId")])
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assignmentId: Long,
    val subjectId: Long,
    val startingSrsStage: Int,
    val endingSrsStage: Int,
    val incorrectMeaningAnswers: Int,
    val incorrectReadingAnswers: Int,
    val reviewedAt: String
)

data class DailyReviewCount(val day: String, val count: Int)

@Dao
interface ReviewLogDao {
    @Insert
    suspend fun insert(entry: ReviewLogEntity)

    /** Review counts grouped by calendar day (WK's ISO timestamps always start "YYYY-MM-DD"). */
    @Query(
        "SELECT substr(reviewedAt, 1, 10) as day, COUNT(*) as count FROM review_log " +
            "WHERE reviewedAt >= :sinceIso GROUP BY day"
    )
    fun observeDailyCounts(sinceIso: String): Flow<List<DailyReviewCount>>

    @Query("SELECT COUNT(*) FROM review_log WHERE reviewedAt >= :sinceIso")
    fun observeCountSince(sinceIso: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM review_log")
    fun observeTotalCount(): Flow<Int>
}
