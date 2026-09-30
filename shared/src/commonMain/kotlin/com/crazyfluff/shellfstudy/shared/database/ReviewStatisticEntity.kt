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

/**
 * The self-stats accuracy figure, summed in SQL.
 *
 * Both are null only for an empty table: SQLite's `SUM` over no rows is null, which is how the caller
 * tells "no reviews yet" (accuracy unknown) from a genuine 0%.
 */
data class ReviewAccuracyTotals(val correct: Long?, val attempts: Long?)

/**
 * Finished reviews over the whole account, split by how many questions each one asks. A finished
 * review always has exactly one correct meaning answer, so `meaningCorrect` counts them. Kanji and
 * vocabulary ask meaning and reading; radicals and kana-only vocabulary ask meaning alone. Both are
 * null for an empty table, as with [ReviewAccuracyTotals].
 */
data class CompletedReviewCounts(val twoQuestion: Long?, val oneQuestion: Long?)

@Dao
interface ReviewStatisticDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(statistics: List<ReviewStatisticEntity>)

    @Query("DELETE FROM review_statistics")
    suspend fun clearAll()

    /**
     * Totals for the leaderboard's self accuracy, without materializing a row per subject.
     *
     * The self-stats flow used to read every review-statistic row — fourteen columns
     * each, several thousand on a mature account — to add up two integers. The sum is the only thing
     * it wanted, so it happens where the rows are.
     */
    @Query(
        "SELECT SUM(meaningCorrect + readingCorrect) AS correct, " +
            "SUM(meaningCorrect + meaningIncorrect + readingCorrect + readingIncorrect) AS attempts " +
            "FROM review_statistics"
    )
    fun observeAccuracyTotals(): Flow<ReviewAccuracyTotals>

    @Query(
        "SELECT SUM(CASE WHEN subjectType IN ('kanji', 'vocabulary') THEN meaningCorrect ELSE 0 END) AS twoQuestion, " +
            "SUM(CASE WHEN subjectType IN ('kanji', 'vocabulary') THEN 0 ELSE meaningCorrect END) AS oneQuestion " +
            "FROM review_statistics"
    )
    fun observeCompletedReviewCounts(): Flow<CompletedReviewCounts>

    /** The subject detail view's accuracy/streak/last-reviewed source. */
    @Query("SELECT * FROM review_statistics WHERE subjectId = :subjectId LIMIT 1")
    fun observeBySubjectId(subjectId: Long): Flow<ReviewStatisticEntity?>
}
