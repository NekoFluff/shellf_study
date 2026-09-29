package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.StudyStreak
import com.crazyfluff.shellfstudy.shared.data.model.SubjectReviewStats
import com.crazyfluff.shellfstudy.shared.database.LevelProgressionDao
import com.crazyfluff.shellfstudy.shared.database.LevelProgressionEntity
import com.crazyfluff.shellfstudy.shared.database.ReviewStatisticDao
import com.crazyfluff.shellfstudy.shared.database.ReviewStatisticEntity
import com.crazyfluff.shellfstudy.shared.database.SyncStateDao
import com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDao
import com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDayEntity
import com.crazyfluff.shellfstudy.shared.network.WaniKaniApi
import com.crazyfluff.shellfstudy.shared.network.collectAllPages
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private val STALENESS = 1.hours

/** Owns review statistics, level progressions, and the local study-activity log (which days had
 *  at least one review — drives the daily study-streak reminder). */
class StatsRepository(
    private val api: WaniKaniApi,
    private val reviewStatisticDao: ReviewStatisticDao,
    private val levelProgressionDao: LevelProgressionDao,
    private val studyActivityDao: StudyActivityDao,
    private val syncStateDao: SyncStateDao,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend fun syncReviewStatistics(force: Boolean = false): ApiResult<Unit> = safeApiCall {
        fetchReviewStatistics(force)?.let { completeResourceSync(syncStateDao, SyncResources.REVIEW_STATISTICS, it) }
    }

    /** Fetches review statistics without writing them — see [AssignmentRepository.fetchAssignments]
     *  for why fetch and write are split. Null when they are fresh enough to skip. */
    internal suspend fun fetchReviewStatistics(force: Boolean = false): ResourceSync<List<ReviewStatisticEntity>>? =
        fetchResourceSync(
            syncStateDao = syncStateDao,
            resource = SyncResources.REVIEW_STATISTICS,
            force = force,
            staleness = STALENESS,
            countRows = { it.size },
            fetch = { cursor ->
                collectAllPages(
                    firstPage = { api.getReviewStatistics(updatedAfter = cursor) },
                    nextPage = { url -> api.getReviewStatisticsPage(url) }
                ).map { item ->
                    ReviewStatisticEntity(
                        id = item.id,
                        subjectId = item.data.subjectId,
                        subjectType = item.data.subjectType,
                        meaningCorrect = item.data.meaningCorrect,
                        meaningIncorrect = item.data.meaningIncorrect,
                        meaningMaxStreak = item.data.meaningMaxStreak,
                        meaningCurrentStreak = item.data.meaningCurrentStreak,
                        readingCorrect = item.data.readingCorrect,
                        readingIncorrect = item.data.readingIncorrect,
                        readingMaxStreak = item.data.readingMaxStreak,
                        readingCurrentStreak = item.data.readingCurrentStreak,
                        percentageCorrect = item.data.percentageCorrect,
                        hidden = item.data.hidden,
                        lastReviewedAt = item.dataUpdatedAt
                    )
                }
            },
            write = { reviewStatisticDao.upsertAll(it) }
        )

    /** The subject detail view's accuracy/streak/last-reviewed source — null if the subject has
     *  no review_statistics row yet (not lessoned, or not synced yet). */
    fun observeReviewStatistic(subjectId: Long): Flow<SubjectReviewStats?> =
        reviewStatisticDao.observeBySubjectId(subjectId).map { entity ->
            entity?.let {
                SubjectReviewStats(
                    meaningCorrect = it.meaningCorrect,
                    meaningIncorrect = it.meaningIncorrect,
                    meaningCurrentStreak = it.meaningCurrentStreak,
                    meaningMaxStreak = it.meaningMaxStreak,
                    readingCorrect = it.readingCorrect,
                    readingIncorrect = it.readingIncorrect,
                    readingCurrentStreak = it.readingCurrentStreak,
                    readingMaxStreak = it.readingMaxStreak,
                    lastReviewedAt = it.lastReviewedAt?.let(Instant::parse)
                )
            }
        }

    /** level_progressions has no documented updated_after filter — always a full (small) refetch. */
    suspend fun syncLevelProgressions(force: Boolean = false): ApiResult<Unit> = safeApiCall {
        fetchLevelProgressions(force)?.let { completeResourceSync(syncStateDao, SyncResources.LEVEL_PROGRESSIONS, it) }
    }

    /** Fetches level progressions without writing them — see [AssignmentRepository.fetchAssignments].
     *  No cursor: the API documents no `updated_after` filter for this resource, so every pass is a
     *  full refetch. Null when they are fresh enough to skip. */
    internal suspend fun fetchLevelProgressions(force: Boolean = false): ResourceSync<List<LevelProgressionEntity>>? =
        fetchResourceSync(
            syncStateDao = syncStateDao,
            resource = SyncResources.LEVEL_PROGRESSIONS,
            force = force,
            staleness = STALENESS,
            useCursor = false,
            countRows = { it.size },
            fetch = {
                api.getLevelProgressions().data.map { item ->
                    LevelProgressionEntity(
                        id = item.id,
                        level = item.data.level,
                        createdAt = item.data.createdAt,
                        unlockedAt = item.data.unlockedAt,
                        startedAt = item.data.startedAt,
                        passedAt = item.data.passedAt,
                        completedAt = item.data.completedAt,
                        abandonedAt = item.data.abandonedAt
                    )
                }
            },
            write = { levelProgressionDao.upsertAll(it) }
        )

    /** Marks today as an active study day — local-only, never gated on network (there's nothing to
     *  sync, this data has no server counterpart), called directly from the review-grading path so
     *  the streak stays live even offline. Idempotent: a day already marked active is a no-op. */
    suspend fun markStudyActivityToday() {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        studyActivityDao.markActive(StudyActivityDayEntity(date = today.toString()))
    }

    /**
     * [studyActivityDao.observeActiveDays] only re-emits on a DB write (i.e. [markStudyActivityToday]),
     * so combining with [dailyRolloverTicks] is what actually rolls "is today active" and the streak
     * over at local midnight — otherwise a day with no study session would leave yesterday's streak
     * state stuck showing "active today" until the next write or an app restart.
     */
    fun observeStudyStreak(): Flow<StudyStreak> =
        combine(
            studyActivityDao.observeActiveDays(),
            dailyRolloverTicks()
        ) { days, today ->
            val activeDays = days.map(LocalDate::parse).toSet()
            val isActiveToday = today in activeDays

            var currentStreak = 0
            var cursor = if (isActiveToday) today else today.minus(1, DateTimeUnit.DAY)
            while (cursor in activeDays) {
                currentStreak++
                cursor = cursor.minus(1, DateTimeUnit.DAY)
            }

            StudyStreak(currentStreakDays = currentStreak, isActiveToday = isActiveToday)
        }.flowOn(defaultDispatcher)

    fun observeDaysOnCurrentLevel(): Flow<Int?> =
        levelProgressionDao.observeAll().map { progressions ->
            val current = progressions.currentLevelProgression()
            val startedAtRaw = current?.startedAt ?: current?.unlockedAt
            startedAtRaw?.let { ((Clock.System.now() - Instant.parse(it)).inWholeDays + 1).toInt() }
        }

    /** The level currently being studied (highest not-yet-passed level). */
    fun observeCurrentLevel(): Flow<Int?> =
        levelProgressionDao.observeAll().map { progressions -> progressions.currentLevelProgression()?.level }
}

/** The level currently being studied: the highest one neither passed nor abandoned by a reset. */
internal fun List<LevelProgressionEntity>.currentLevelProgression(): LevelProgressionEntity? =
    filter { it.passedAt == null && it.abandonedAt == null }.maxByOrNull { it.level }
