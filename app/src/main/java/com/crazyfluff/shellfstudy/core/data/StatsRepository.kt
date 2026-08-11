package com.crazyfluff.shellfstudy.core.data

import com.crazyfluff.shellfstudy.core.data.model.ReviewsCompletedStats
import com.crazyfluff.shellfstudy.core.data.model.StudyStreak
import com.crazyfluff.shellfstudy.core.database.LevelProgressionDao
import com.crazyfluff.shellfstudy.core.database.LevelProgressionEntity
import com.crazyfluff.shellfstudy.core.database.ReviewStatisticDao
import com.crazyfluff.shellfstudy.core.database.ReviewStatisticEntity
import com.crazyfluff.shellfstudy.core.database.SyncStateDao
import com.crazyfluff.shellfstudy.core.database.reviewhistory.ReviewLogDao
import com.crazyfluff.shellfstudy.core.database.reviewhistory.ReviewLogEntity
import com.crazyfluff.shellfstudy.core.network.ReviewResultData
import com.crazyfluff.shellfstudy.core.network.WaniKaniApi
import com.crazyfluff.shellfstudy.core.network.collectAllPages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val RESOURCE_REVIEW_STATISTICS = "review_statistics"
private const val RESOURCE_LEVEL_PROGRESSIONS = "level_progressions"
private val STALENESS = Duration.ofHours(1)
private val EPOCH_ISO: String = Instant.EPOCH.toString()

/** Owns review statistics, level progressions, and this app's own local review-event log. */
@Singleton
class StatsRepository @Inject constructor(
    private val api: WaniKaniApi,
    private val reviewStatisticDao: ReviewStatisticDao,
    private val levelProgressionDao: LevelProgressionDao,
    private val reviewLogDao: ReviewLogDao,
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

    /** Appends one completed review to the local history log — the only source of streak/count data. */
    suspend fun logReviewEvent(result: ReviewResultData) {
        reviewLogDao.insert(
            ReviewLogEntity(
                assignmentId = result.assignmentId,
                subjectId = result.subjectId,
                startingSrsStage = result.startingSrsStage,
                endingSrsStage = result.endingSrsStage,
                incorrectMeaningAnswers = result.incorrectMeaningAnswers,
                incorrectReadingAnswers = result.incorrectReadingAnswers,
                reviewedAt = result.createdAt
            )
        )
    }

    fun observeStudyStreak(): Flow<StudyStreak> =
        reviewLogDao.observeDailyCounts(EPOCH_ISO).map { counts ->
            val countsByDay = counts.associate { LocalDate.parse(it.day) to it.count }
            val today = LocalDate.now()
            val isActiveToday = (countsByDay[today] ?: 0) > 0

            var currentStreak = 0
            var cursor = if (isActiveToday) today else today.minusDays(1)
            while ((countsByDay[cursor] ?: 0) > 0) {
                currentStreak++
                cursor = cursor.minusDays(1)
            }

            var longestStreak = 0
            var running = 0
            var previousDay: LocalDate? = null
            countsByDay.keys.sorted().forEach { day ->
                running = if (previousDay != null && previousDay.plusDays(1) == day) running + 1 else 1
                longestStreak = maxOf(longestStreak, running)
                previousDay = day
            }

            val activeDaysLast7 = (6 downTo 0).map { offset -> (countsByDay[today.minusDays(offset.toLong())] ?: 0) > 0 }

            StudyStreak(
                currentStreakDays = currentStreak,
                longestStreakDays = maxOf(longestStreak, currentStreak),
                isActiveToday = isActiveToday,
                activeDaysLast7 = activeDaysLast7
            )
        }

    fun observeReviewsCompletedStats(): Flow<ReviewsCompletedStats> =
        combine(
            reviewLogDao.observeCountSince(startOfDayIso(0)),
            reviewLogDao.observeCountSince(startOfDayIso(6)),
            reviewLogDao.observeTotalCount()
        ) { today, last7Days, allTime -> ReviewsCompletedStats(today = today, last7Days = last7Days, allTime = allTime) }

    fun observeDaysOnCurrentLevel(): Flow<Int?> =
        levelProgressionDao.observeAll().map { progressions ->
            val current = currentLevelProgression(progressions)
            val startedAtRaw = current?.startedAt ?: current?.unlockedAt
            startedAtRaw?.let { (ChronoUnit.DAYS.between(Instant.parse(it), Instant.now()) + 1).toInt() }
        }

    /** The level currently being studied (highest not-yet-passed level), for milestone notifications. */
    fun observeCurrentLevel(): Flow<Int?> =
        levelProgressionDao.observeAll().map { progressions -> currentLevelProgression(progressions)?.level }

    private fun currentLevelProgression(progressions: List<LevelProgressionEntity>): LevelProgressionEntity? =
        progressions.filter { it.passedAt == null }.maxByOrNull { it.level }

    private fun startOfDayIso(daysAgo: Int): String =
        LocalDate.now().minusDays(daysAgo.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
}
