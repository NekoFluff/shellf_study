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
    val hidden: Boolean
)

@Dao
interface ReviewStatisticDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(statistics: List<ReviewStatisticEntity>)

    @Query("SELECT * FROM review_statistics WHERE subjectId = :subjectId")
    suspend fun getBySubjectId(subjectId: Long): ReviewStatisticEntity?

    @Query("SELECT * FROM review_statistics")
    fun observeAll(): Flow<List<ReviewStatisticEntity>>

    @Query("SELECT COALESCE(SUM(meaningCorrect + meaningIncorrect + readingCorrect + readingIncorrect), 0) FROM review_statistics")
    fun observeTotalReviewsCount(): Flow<Int>
}
