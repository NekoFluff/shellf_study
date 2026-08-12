package com.crazyfluff.shellfstudy.core.data

import com.crazyfluff.shellfstudy.core.data.model.StudyStreak
import com.crazyfluff.shellfstudy.core.database.LevelProgressionDao
import com.crazyfluff.shellfstudy.core.database.LevelProgressionEntity
import com.crazyfluff.shellfstudy.core.database.ReviewStatisticDao
import com.crazyfluff.shellfstudy.core.database.ReviewStatisticEntity
import com.crazyfluff.shellfstudy.core.database.SyncStateDao
import com.crazyfluff.shellfstudy.core.database.studyactivity.StudyActivityDao
import com.crazyfluff.shellfstudy.core.database.studyactivity.StudyActivityDayEntity
import com.crazyfluff.shellfstudy.core.network.WaniKaniApi
import com.crazyfluff.shellfstudy.core.network.collectAllPages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val RESOURCE_REVIEW_STATISTICS = "review_statistics"
private const val RESOURCE_LEVEL_PROGRESSIONS = "level_progressions"
private val STALENESS = Duration.ofHours(1)

/** Owns review statistics, level progressions, and the local study-activity log (which days had
 *  at least one review — drives the daily study-streak reminder). */
@Singleton
class StatsRepository @Inject constructor(
    private val api: WaniKaniApi,
    private val reviewStatisticDao: ReviewStatisticDao,
    private val levelProgressionDao: LevelProgressionDao,
    private val studyActivityDao: StudyActivityDao,
    private val syncStateDao: SyncStateDao
) {
    suspend fun syncReviewStatistics(force: Boolean = false): ApiResult<Unit> {
        if (!shouldSync(syncStateDao, RESOURCE_REVIEW_STATISTICS, force, STALENESS)) return ApiResult.Success(Unit)
        return safeApiCall {
            val cursor = syncCursor(syncStateDao, RESOURCE_REVIEW_STATISTICS)
            val startedAt = Instant.now().toString()
            val items = collectAllPages(
                firstPage = { api.getReviewStatistics(updatedAfter = cursor) },
                nextPage = { url -> api.getReviewStatisticsPage(url) }
            )
            reviewStatisticDao.upsertAll(
                items.map { item ->
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
                        hidden = item.data.hidden
                    )
                }
            )
            recordSyncSuccess(syncStateDao, RESOURCE_REVIEW_STATISTICS, cursor = startedAt)
        }
    }

    /** level_progressions has no documented updated_after filter — always a full (small) refetch. */
    suspend fun syncLevelProgressions(force: Boolean = false): ApiResult<Unit> {
        if (!shouldSync(syncStateDao, RESOURCE_LEVEL_PROGRESSIONS, force, STALENESS)) return ApiResult.Success(Unit)
        return safeApiCall {
            val response = api.getLevelProgressions()
            levelProgressionDao.upsertAll(
                response.data.map { item ->
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
            )
            recordSyncSuccess(syncStateDao, RESOURCE_LEVEL_PROGRESSIONS)
        }
    }

    /** Marks today as an active study day — local-only, never gated on network (there's nothing to
     *  sync, this data has no server counterpart), called directly from the review-grading path so
     *  the streak stays live even offline. Idempotent: a day already marked active is a no-op. */
    suspend fun markStudyActivityToday() {
        studyActivityDao.markActive(StudyActivityDayEntity(date = LocalDate.now().toString()))
    }

    fun observeStudyStreak(): Flow<StudyStreak> =
        studyActivityDao.observeActiveDays().map { days ->
            val activeDays = days.map(LocalDate::parse).toSet()
            val today = LocalDate.now()
            val isActiveToday = today in activeDays

            var currentStreak = 0
            var cursor = if (isActiveToday) today else today.minusDays(1)
            while (cursor in activeDays) {
                currentStreak++
                cursor = cursor.minusDays(1)
            }

            StudyStreak(currentStreakDays = currentStreak, isActiveToday = isActiveToday)
        }

    fun observeDaysOnCurrentLevel(): Flow<Int?> =
        levelProgressionDao.observeAll().map { progressions ->
            val current = currentLevelProgression(progressions)
            val startedAtRaw = current?.startedAt ?: current?.unlockedAt
            startedAtRaw?.let { (ChronoUnit.DAYS.between(Instant.parse(it), Instant.now()) + 1).toInt() }
        }

    /** The level currently being studied (highest not-yet-passed level). */
    fun observeCurrentLevel(): Flow<Int?> =
        levelProgressionDao.observeAll().map { progressions -> currentLevelProgression(progressions)?.level }

    private fun currentLevelProgression(progressions: List<LevelProgressionEntity>): LevelProgressionEntity? =
        progressions.filter { it.passedAt == null }.maxByOrNull { it.level }
}
