package com.crazyfluff.shellfstudy.shared.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Indexes are chosen from the query plans of the dashboard's observable queries, not added
 * speculatively — every index also costs write time on each sync's bulk upsert, so one that no plan
 * uses is pure loss.
 *
 * - `(hidden, startedAt)`: `observeItemsSeenCount`, `observeAllStartedTimestamps` and
 *   `observeStartedSinceCount` all filter on exactly this pair; the first two previously
 *   `SCAN assignments`.
 * - `(hidden, availableAt)`: `observeDueForReviewCount` / `observeAvailableNow` / `observeUpcoming`.
 *   Every query that filters on `availableAt` also filters on `hidden = 0`, so a composite starting
 *   with `hidden` supersedes the bare `availableAt` index — which this entity declared anyway until
 *   the 10->11 migration dropped it. It was pure write cost: one more index for every row of every
 *   bulk sync write, serving no plan.
 * - `(hidden, burnedAt)`: `observeAllBurnedTimestamps`, the self-stats "burned over time" source.
 *   Without it that query scanned the whole table on every assignments write.
 * - `(hidden, srsStage, subjectType)`: `observeSrsStageAndTypeCounts`'s GROUP BY, which previously
 *   ran as `SCAN assignments` plus `USE TEMP B-TREE FOR GROUP BY`.
 *
 * Deliberately *not* added: `(hidden, unlockedAt, startedAt)` for the lesson queries. Those match at
 * most a few dozen rows (`observeDueForLesson` returns 17 on a level-30 account), so the composite
 * would make the SELECT scan fewer rows while never being worth its write cost.
 */
@Entity(
    tableName = "assignments",
    indices = [
        Index("subjectId"),
        Index("hidden", "startedAt"),
        Index("hidden", "availableAt"),
        Index("hidden", "burnedAt"),
        Index("hidden", "srsStage", "subjectType")
    ]
)
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

/** One radical or kanji at a given level, with whatever its assignment says about where it stands —
 *  the fastest-level-up-path source (see `LevelUpPathCalculator`). The assignment columns are null
 *  (and [srsStage] 0) for a subject WaniKani hasn't created an assignment for yet. */
data class LevelUpPathRow(
    val subjectId: Long,
    val subjectType: String,
    val srsSystemId: Long,
    val componentSubjectIds: List<Long>,
    val srsStage: Int,
    val unlockedAt: String?,
    val availableAt: String?
)

/** The three columns the review forecast buckets — see [AssignmentDao.observeUpcoming]. Deliberately
 *  not an [AssignmentEntity]: that would decode eleven more columns per upcoming assignment, none of
 *  which the forecast reads. On a maxed-out account the upcoming set runs to a few thousand rows, so
 *  the difference is a 1.2 KB/row projection against a 158-byte one. */
data class UpcomingAssignmentRow(
    val availableAt: String?,
    val subjectType: String,
    val srsStage: Int
)

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

    /** Same filter as [observeDueForReview], as a count — for the dashboard badge/enablement.
     *  Requires the subject to be present in the subjects table so the count matches the
     *  queue builders (which silently drop assignments with uncached subjects), avoiding a
     *  mismatch between the badge and the actual session content the user lands on. */
    @Query("SELECT COUNT(*) FROM assignments WHERE hidden = 0 AND availableAt IS NOT NULL AND availableAt <= :nowIso AND EXISTS (SELECT 1 FROM subjects WHERE subjects.id = assignments.subjectId)")
    fun observeDueForReviewCount(nowIso: String): Flow<Int>

    /** Reviews available right now, projected like [observeUpcoming], for the forecast's "now" column.
     *
     *  Not [observeDueForReview], which is also the review-queue source and so has to load whole rows
     *  — the forecast reads only a count and two groupings, so it must not pay the queue's projection.
     *
     *  Keeps [observeDueForReviewCount]'s subject-existence check, though, because the forecast's
     *  "N due now" and the dashboard's review badge describe the same quantity and must not disagree.
     *  That check is what makes them agree with what a session can actually contain: queue builders
     *  drop assignments whose subject isn't cached. */
    @Query(
        """
        SELECT availableAt, subjectType, srsStage FROM assignments
        WHERE hidden = 0 AND availableAt IS NOT NULL AND availableAt <= :nowIso
          AND EXISTS (SELECT 1 FROM subjects WHERE subjects.id = assignments.subjectId)
        """
    )
    fun observeAvailableNow(nowIso: String): Flow<List<UpcomingAssignmentRow>>

    /** Lessons available right now: unlocked but not yet started. */
    @Query("SELECT * FROM assignments WHERE hidden = 0 AND unlockedAt IS NOT NULL AND startedAt IS NULL")
    fun observeDueForLesson(): Flow<List<AssignmentEntity>>

    /** Same filter as [observeDueForLesson], as a count — see [observeDueForReviewCount]. */
    @Query("SELECT COUNT(*) FROM assignments WHERE hidden = 0 AND unlockedAt IS NOT NULL AND startedAt IS NULL AND EXISTS (SELECT 1 FROM subjects WHERE subjects.id = assignments.subjectId)")
    fun observeDueForLessonCount(): Flow<Int>

    /** Reviews that will become available later — the review-forecast source.
     *
     *  Projects only the three columns the forecast buckets. This previously selected whole
     *  [AssignmentEntity] rows, which meant decoding nine unused columns — including two nullable
     *  timestamp strings parsed per row — for a set that runs to a few thousand rows on a
     *  maxed-out account. */
    @Query("SELECT availableAt, subjectType, srsStage FROM assignments WHERE hidden = 0 AND availableAt IS NOT NULL AND availableAt > :nowIso ORDER BY availableAt ASC")
    fun observeUpcoming(nowIso: String): Flow<List<UpcomingAssignmentRow>>

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

    /** How many assignments were started at or after [sinceIso] — the "lessons done today" source.
     *
     *  Counted in SQL rather than by materializing every started timestamp and parsing each one in
     *  Kotlin ([observeAllStartedTimestamps]'s approach), which meant one ISO-8601 string allocation
     *  plus one [kotlin.time.Instant.parse] per started assignment on every recompute — a few
     *  thousand of each on a maxed-out account, just to count the handful started today.
     *
     *  [sinceIso] must be in the same canonical form WaniKani writes (`...T00:00:00.000000Z`), so the
     *  lexicographic comparison the index provides stays correct. See `Instant.toWkIsoString`. */
    @Query("SELECT COUNT(*) FROM assignments WHERE hidden = 0 AND startedAt IS NOT NULL AND startedAt >= :sinceIso")
    fun observeStartedSinceCount(sinceIso: String): Flow<Int>

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

    /** Every radical and kanji at [level] with its assignment's stage and timing — the input to the
     *  fastest-level-up path. Driven from [subjects] like [observeKanjiLevelUpRows], so a kanji with
     *  no assignment row yet still shows up (as locked) and counts toward the level's total. */
    @Query(
        """
        SELECT s.id as subjectId, s.subjectType as subjectType, s.srsSystemId as srsSystemId,
               s.componentSubjectIds as componentSubjectIds, COALESCE(a.srsStage, 0) as srsStage,
               a.unlockedAt as unlockedAt, a.availableAt as availableAt
        FROM subjects s
        LEFT JOIN assignments a ON a.subjectId = s.id AND a.hidden = 0
        WHERE s.level = :level AND s.subjectType IN ('radical', 'kanji') AND s.hiddenAt IS NULL
        """
    )
    fun observeLevelUpPathRows(level: Int): Flow<List<LevelUpPathRow>>
}
