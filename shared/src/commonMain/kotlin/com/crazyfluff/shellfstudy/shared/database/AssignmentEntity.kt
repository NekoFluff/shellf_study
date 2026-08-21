package com.crazyfluff.shellfstudy.shared.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "assignments", indices = [Index("subjectId"), Index("availableAt")])
data class AssignmentEntity(
    @PrimaryKey val id: Long,
    val subjectId: Long,
    val subjectType: String,
    val srsStage: Int,
    val createdAt: String,
    val unlockedAt: String? = null,
    val startedAt: String? = null,
    val passedAt: String? = null,
    val burnedAt: String? = null,
    val availableAt: String? = null,
    val resurrectedAt: String? = null,
    val hidden: Boolean
)

/** One SRS stage's assignment count for a single subject type — the item-spread type-breakdown source. */
data class SrsStageTypeCount(val srsStage: Int, val subjectType: String, val count: Int)

/** One assignment's type, subject display text, and passed status at a given level — the level-progress source. */
data class LevelProgressItemRow(
    val subjectId: Long,
    val subjectType: String,
    val characters: String?,
    val characterImageUrl: String?,
    val slug: String,
    val passedAt: String?,
    val srsStage: Int
)

/** One kanji assignment's SRS stage at a given level — the level-up-progress source. */
data class KanjiLevelUpRow(val srsStage: Int)

@Dao
interface AssignmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(assignments: List<AssignmentEntity>)

    @Query("DELETE FROM assignments")
    suspend fun clearAll()

    @Query("SELECT * FROM assignments WHERE id = :id")
    suspend fun getById(id: Long): AssignmentEntity?

    @Query("SELECT * FROM assignments WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<AssignmentEntity>

    /** The assignment (if any — the subject may not have been lessoned yet) backing a subject,
     *  for surfacing its current SRS stage in the subject detail view. */
    @Query("SELECT * FROM assignments WHERE subjectId = :subjectId LIMIT 1")
    fun observeBySubjectId(subjectId: Long): Flow<AssignmentEntity?>

    /** Reviews available right now: unlocked, started, and past their SRS-scheduled availableAt. */
    @Query("SELECT * FROM assignments WHERE hidden = 0 AND availableAt IS NOT NULL AND availableAt <= :nowIso ORDER BY availableAt ASC")
    fun observeDueForReview(nowIso: String): Flow<List<AssignmentEntity>>

    /** Lessons available right now: unlocked but not yet started. */
    @Query("SELECT * FROM assignments WHERE hidden = 0 AND unlockedAt IS NOT NULL AND startedAt IS NULL")
    fun observeDueForLesson(): Flow<List<AssignmentEntity>>

    /** Reviews that will become available later — the review-forecast source. */
    @Query("SELECT * FROM assignments WHERE hidden = 0 AND availableAt IS NOT NULL AND availableAt > :nowIso ORDER BY availableAt ASC")
    fun observeUpcoming(nowIso: String): Flow<List<AssignmentEntity>>

    /** SRS-stage distribution by subject type across every started assignment — the item-spread source. */
    @Query("SELECT srsStage, subjectType, COUNT(*) as count FROM assignments WHERE hidden = 0 AND startedAt IS NOT NULL GROUP BY srsStage, subjectType")
    fun observeSrsStageAndTypeCounts(): Flow<List<SrsStageTypeCount>>

    /** Every subject's type, display text, and passed status at [level] — driven from [subjects]
     *  rather than [assignments] so a subject with no assignment row yet (its prerequisites
     *  haven't been reached, so WaniKani hasn't created one) still shows up as locked (srsStage 0)
     *  instead of silently missing from the level progress view. */
    @Query(
        """
        SELECT s.id as subjectId, s.subjectType as subjectType, s.characters as characters, s.characterImageUrl as characterImageUrl, s.slug as slug, a.passedAt as passedAt, COALESCE(a.srsStage, 0) as srsStage
        FROM subjects s
        LEFT JOIN assignments a ON a.subjectId = s.id AND a.hidden = 0
        WHERE s.level = :level AND s.hiddenAt IS NULL
        ORDER BY s.lessonPosition ASC, s.id ASC
        """
    )
    fun observeLevelProgressItemRows(level: Int): Flow<List<LevelProgressItemRow>>

    @Query("SELECT COUNT(*) FROM assignments WHERE hidden = 0 AND startedAt IS NOT NULL")
    fun observeItemsSeenCount(): Flow<Int>

    @Query("SELECT startedAt FROM assignments WHERE hidden = 0 AND startedAt IS NOT NULL")
    fun observeAllStartedTimestamps(): Flow<List<String>>

    @Query("SELECT burnedAt FROM assignments WHERE hidden = 0 AND burnedAt IS NOT NULL")
    fun observeAllBurnedTimestamps(): Flow<List<String>>

    /** Every kanji's SRS stage at [level] — Guru+ (stage >= 5) counts toward leveling up. Driven
     *  from [subjects], like [observeLevelProgressItemRows], so a kanji with no assignment row
     *  yet still counts toward the level's total (as locked, stage 0) instead of shrinking the
     *  90%-of-level denominator. */
    @Query(
        """
        SELECT COALESCE(a.srsStage, 0) as srsStage
        FROM subjects s
        LEFT JOIN assignments a ON a.subjectId = s.id AND a.hidden = 0
        WHERE s.level = :level AND s.subjectType = 'kanji' AND s.hiddenAt IS NULL
        """
    )
    fun observeKanjiLevelUpRows(level: Int): Flow<List<KanjiLevelUpRow>>
}
