package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.StudyStreak
import com.crazyfluff.shellfstudy.shared.database.LevelProgressionDao
import com.crazyfluff.shellfstudy.shared.database.LevelProgressionEntity
import com.crazyfluff.shellfstudy.shared.database.ReviewStatisticDao
import com.crazyfluff.shellfstudy.shared.database.ReviewStatisticEntity
import com.crazyfluff.shellfstudy.shared.database.SyncStateDao
import com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDao
import com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDayEntity
import com.crazyfluff.shellfstudy.shared.network.WaniKaniApi
import com.crazyfluff.shellfstudy.shared.network.collectAllPages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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

private const val RESOURCE_REVIEW_STATISTICS = "review_statistics"
private const val RESOURCE_LEVEL_PROGRESSIONS = "level_progressions"
private val STALENESS = 1.hours

/** Owns review statistics, level progressions, and the local study-activity log (which days had
 *  at least one review — drives the daily study-streak reminder). */
class StatsRepository(
    private val api: WaniKaniApi,
    private val reviewStatisticDao: ReviewStatisticDao,
    private val levelProgressionDao: LevelProgressionDao,
    private val studyActivityDao: StudyActivityDao,
    private val syncStateDao: SyncStateDao
) {
    suspend fun syncReviewStatistics(force: Boolean = false): ApiResult<Unit> =
        runSync(syncStateDao, RESOURCE_REVIEW_STATISTICS, force, STALENESS) { cursor ->
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
        }

    /** level_progressions has no documented updated_after filter — always a full (small) refetch. */
    suspend fun syncLevelProgressions(force: Boolean = false): ApiResult<Unit> =
        runSync(syncStateDao, RESOURCE_LEVEL_PROGRESSIONS, force, STALENESS, useCursor = false) {
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
        }

    /** Marks today as an active study day — local-only, never gated on network (there's nothing to
     *  sync, this data has no server counterpart), called directly from the review-grading path so
     *  the streak stays live even offline. Idempotent: a day already marked active is a no-op. */
    suspend fun markStudyActivityToday() {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        studyActivityDao.markActive(StudyActivityDayEntity(date = today.toString()))
    }

    fun observeStudyStreak(): Flow<StudyStreak> =
        studyActivityDao.observeActiveDays().map { days ->
            val activeDays = days.map(LocalDate::parse).toSet()
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val isActiveToday = today in activeDays

            var currentStreak = 0
            var cursor = if (isActiveToday) today else today.minus(1, DateTimeUnit.DAY)
            while (cursor in activeDays) {
                currentStreak++
                cursor = cursor.minus(1, DateTimeUnit.DAY)
            }

            StudyStreak(currentStreakDays = currentStreak, isActiveToday = isActiveToday)
        }.flowOn(Dispatchers.Default)

    fun observeDaysOnCurrentLevel(): Flow<Int?> =
        levelProgressionDao.observeAll().map { progressions ->
            val current = currentLevelProgression(progressions)
            val startedAtRaw = current?.startedAt ?: current?.unlockedAt
            startedAtRaw?.let { ((Clock.System.now() - Instant.parse(it)).inWholeDays + 1).toInt() }
        }

    /** The level currently being studied (highest not-yet-passed level). */
    fun observeCurrentLevel(): Flow<Int?> =
        levelProgressionDao.observeAll().map { progressions -> currentLevelProgression(progressions)?.level }

    private fun currentLevelProgression(progressions: List<LevelProgressionEntity>): LevelProgressionEntity? =
        progressions.filter { it.passedAt == null }.maxByOrNull { it.level }
}
