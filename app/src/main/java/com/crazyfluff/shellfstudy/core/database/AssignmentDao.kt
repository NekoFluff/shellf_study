package com.crazyfluff.shellfstudy.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class SrsStageCount(val srsStage: Int, val count: Int)

/** One assignment's type, subject display text, and passed status at a given level — the level-progress source. */
data class LevelProgressItemRow(
    val subjectId: Long,
    val subjectType: String,
    val characters: String?,
    val slug: String,
    val passedAt: String?
)

/** One kanji assignment's SRS stage at a given level — the level-up-progress source. */
data class KanjiLevelUpRow(val srsStage: Int)

@Dao
interface AssignmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(assignments: List<AssignmentEntity>)

    /** Reviews available right now: unlocked, started, and past their SRS-scheduled availableAt. */
    @Query("SELECT * FROM assignments WHERE hidden = 0 AND availableAt IS NOT NULL AND availableAt <= :nowIso ORDER BY availableAt ASC")
    fun observeDueForReview(nowIso: String): Flow<List<AssignmentEntity>>

    /** Lessons available right now: unlocked but not yet started. */
    @Query("SELECT * FROM assignments WHERE hidden = 0 AND unlockedAt IS NOT NULL AND startedAt IS NULL")
    fun observeDueForLesson(): Flow<List<AssignmentEntity>>

    /** Reviews that will become available later — the review-forecast source. */
    @Query("SELECT * FROM assignments WHERE hidden = 0 AND availableAt IS NOT NULL AND availableAt > :nowIso ORDER BY availableAt ASC")
    fun observeUpcoming(nowIso: String): Flow<List<AssignmentEntity>>

    /** SRS-stage distribution across every started assignment — the item-spread source. */
    @Query("SELECT srsStage, COUNT(*) as count FROM assignments WHERE hidden = 0 AND startedAt IS NOT NULL GROUP BY srsStage")
    fun observeSrsStageCounts(): Flow<List<SrsStageCount>>

    /** Every started assignment's type, subject display text, and passed status at [level]. */
    @Query(
        """
        SELECT a.subjectId as subjectId, a.subjectType as subjectType, s.characters as characters, s.slug as slug, a.passedAt as passedAt
        FROM assignments a
        JOIN subjects s ON s.id = a.subjectId
        WHERE s.level = :level AND a.hidden = 0 AND a.unlockedAt IS NOT NULL
        ORDER BY s.lessonPosition ASC, a.subjectId ASC
        """
    )
    fun observeLevelProgressItemRows(level: Int): Flow<List<LevelProgressItemRow>>

    @Query("SELECT COUNT(*) FROM assignments WHERE hidden = 0 AND startedAt IS NOT NULL")
    fun observeItemsSeenCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM assignments WHERE hidden = 0 AND startedAt IS NOT NULL AND startedAt >= :startOfDayIso")
    fun observeStartedTodayCount(startOfDayIso: String): Flow<Int>

    /** Every kanji assignment's SRS stage at [level] — Guru+ (stage >= 5) counts toward leveling up. */
    @Query(
        """
        SELECT a.srsStage as srsStage
        FROM assignments a
        JOIN subjects s ON s.id = a.subjectId
        WHERE s.level = :level AND a.subjectType = 'kanji' AND a.hidden = 0
        """
    )
    fun observeKanjiLevelUpRows(level: Int): Flow<List<KanjiLevelUpRow>>
}
