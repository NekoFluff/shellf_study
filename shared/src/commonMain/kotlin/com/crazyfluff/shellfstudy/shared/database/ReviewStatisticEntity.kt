package com.crazyfluff.shellfstudy.shared.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "review_statistics", indices = [Index("subjectId", unique = true)])
data class ReviewStatisticEntity(
    @PrimaryKey val id: Long,
    val subjectId: Long,
    val subjectType: String,
    val meaningCorrect: Int,
    val meaningIncorrect: Int,
    val meaningMaxStreak: Int,
    val meaningCurrentStreak: Int,
    val readingCorrect: Int,
    val readingIncorrect: Int,
    val readingMaxStreak: Int,
    val readingCurrentStreak: Int,
    val percentageCorrect: Int,
    val hidden: Boolean,
    /** From the WK envelope's `data_updated_at` — updates every time a review is submitted for
     *  this subject, so it doubles as "last reviewed at" without a dedicated reviews sync. */
    val lastReviewedAt: String? = null
)

@Dao
interface ReviewStatisticDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(statistics: List<ReviewStatisticEntity>)

    @Query("SELECT * FROM review_statistics")
    fun observeAll(): Flow<List<ReviewStatisticEntity>>

    /** The subject detail view's accuracy/streak/last-reviewed source. */
    @Query("SELECT * FROM review_statistics WHERE subjectId = :subjectId LIMIT 1")
    fun observeBySubjectId(subjectId: Long): Flow<ReviewStatisticEntity?>
}
