package com.crazyfluff.shellfstudy.core.data

import app.cash.turbine.test
import com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDayEntity
import com.crazyfluff.shellfstudy.fakes.TestRepositories
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.fakes.jsonResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class StatsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repositories: TestRepositories
    private val repository get() = repositories.statsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repositories = buildTestRepositories(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `syncLevelProgressions caches rows, observeDaysOnCurrentLevel computes days since the in-progress level started`() = runTest {
        server.enqueue(jsonResponse(levelProgressionsJson(startedAt = "2026-01-01T00:00:00.000000Z")))

        repository.syncLevelProgressions(force = true)

        repository.observeDaysOnCurrentLevel().test {
            assertThat(awaitItem()).isNotNull()
        }
    }

    @Test
    fun `observeDaysOnCurrentLevel returns null when every level has been passed`() = runTest {
        server.enqueue(jsonResponse(ALL_LEVELS_PASSED_JSON))

        repository.syncLevelProgressions(force = true)

        repository.observeDaysOnCurrentLevel().test {
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `observeCurrentLevel skips an abandoned level reset even if it is the highest level number`() = runTest {
        val realLevelStartedAt = (Clock.System.now() - 19.days).toString()
        server.enqueue(jsonResponse(abandonedResetJson(realLevelStartedAt)))

        repository.syncLevelProgressions(force = true)

        repository.observeCurrentLevel().test {
            assertThat(awaitItem()).isEqualTo(3)
        }
    }

    @Test
    fun `observeDaysOnCurrentLevel computes from the real in-progress level, not an abandoned reset`() = runTest {
        val realLevelStartedAt = (Clock.System.now() - 19.days).toString()
        server.enqueue(jsonResponse(abandonedResetJson(realLevelStartedAt)))

        repository.syncLevelProgressions(force = true)

        repository.observeDaysOnCurrentLevel().test {
            // Level 3's startedAt is 19 days ago; the abandoned level 15 row's startedAt is years
            // earlier and must not be the one used, otherwise this would be in the thousands.
            assertThat(awaitItem()).isEqualTo(20)
        }
    }

    @Test
    fun `observeCurrentLevel is the highest not-yet-passed level`() = runTest {
        server.enqueue(jsonResponse(levelProgressionsJson(startedAt = "2026-01-01T00:00:00.000000Z")))

        repository.syncLevelProgressions(force = true)

        repository.observeCurrentLevel().test {
            assertThat(awaitItem()).isEqualTo(12)
        }
    }

    @Test
    fun `observeCurrentLevel is null once every level has been passed`() = runTest {
        server.enqueue(jsonResponse(ALL_LEVELS_PASSED_JSON))

        repository.syncLevelProgressions(force = true)

        repository.observeCurrentLevel().test {
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `markStudyActivityToday marks today active exactly once even if called twice`() = runTest {
        repository.markStudyActivityToday()
        repository.markStudyActivityToday()

        repository.observeStudyStreak().test {
            val streak = awaitItem()
            assertThat(streak.isActiveToday).isTrue()
            assertThat(streak.currentStreakDays).isEqualTo(1)
        }
    }

    @Test
    fun `study streak counts consecutive days ending today`() = runTest {
        val today = LocalDate.now()
        repositories.studyActivityDao.markActive(StudyActivityDayEntity(today.toString()))
        repositories.studyActivityDao.markActive(StudyActivityDayEntity(today.minusDays(1).toString()))

        repository.observeStudyStreak().test {
            val streak = awaitItem()
            assertThat(streak.isActiveToday).isTrue()
            assertThat(streak.currentStreakDays).isEqualTo(2)
        }
    }

    @Test
    fun `study streak is broken by a gap, even if today is active`() = runTest {
        val today = LocalDate.now()
        repositories.studyActivityDao.markActive(StudyActivityDayEntity(today.toString()))
        repositories.studyActivityDao.markActive(StudyActivityDayEntity(today.minusDays(2).toString()))

        repository.observeStudyStreak().test {
            val streak = awaitItem()
            assertThat(streak.isActiveToday).isTrue()
            assertThat(streak.currentStreakDays).isEqualTo(1)
        }
    }

    @Test
    fun `syncReviewStatistics persists lastReviewedAt from the envelope's data_updated_at`() = runTest {
        server.enqueue(jsonResponse(reviewStatisticsJson(subjectId = 440, dataUpdatedAt = "2026-01-15T03:00:00.000000Z")))

        repository.syncReviewStatistics(force = true)

        repository.observeReviewStatistic(440).test {
            assertThat(awaitItem()?.lastReviewedAt).isEqualTo(Instant.parse("2026-01-15T03:00:00.000000Z"))
        }
    }

    @Test
    fun `observeReviewStatistic returns null when the subject has no review_statistics row`() = runTest {
        repository.observeReviewStatistic(999).test {
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `observeReviewStatistic maps correct, incorrect, and streak fields per question type`() = runTest {
        server.enqueue(
            jsonResponse(
                reviewStatisticsJson(
                    subjectId = 440,
                    dataUpdatedAt = "2026-01-15T03:00:00.000000Z",
                    meaningCorrect = 22,
                    meaningIncorrect = 2,
                    meaningCurrentStreak = 6,
                    meaningMaxStreak = 9,
                    readingCorrect = 19,
                    readingIncorrect = 3,
                    readingCurrentStreak = 4,
                    readingMaxStreak = 14
                )
            )
        )

        repository.syncReviewStatistics(force = true)

        repository.observeReviewStatistic(440).test {
            val stats = awaitItem()
            assertThat(stats?.meaningAccuracyPercent).isEqualTo(91)
            assertThat(stats?.readingAccuracyPercent).isEqualTo(86)
            assertThat(stats?.meaningCurrentStreak).isEqualTo(6)
            assertThat(stats?.readingMaxStreak).isEqualTo(14)
            assertThat(stats?.hasBeenReviewed).isTrue()
        }
    }

    private fun reviewStatisticsJson(
        subjectId: Long,
        dataUpdatedAt: String,
        meaningCorrect: Int = 0,
        meaningIncorrect: Int = 0,
        meaningCurrentStreak: Int = 0,
        meaningMaxStreak: Int = 0,
        readingCorrect: Int = 0,
        readingIncorrect: Int = 0,
        readingCurrentStreak: Int = 0,
        readingMaxStreak: Int = 0
    ) = """
        {
          "object": "collection",
          "url": "https://api.wanikani.com/v2/review_statistics",
          "total_count": 1,
          "data": [
            {
              "id": 1,
              "object": "review_statistic",
              "url": "https://api.wanikani.com/v2/review_statistics/1",
              "data_updated_at": "$dataUpdatedAt",
              "data": {
                "created_at": "2020-01-01T00:00:00.000000Z",
                "subject_id": $subjectId,
                "subject_type": "kanji",
                "meaning_correct": $meaningCorrect,
                "meaning_incorrect": $meaningIncorrect,
                "meaning_max_streak": $meaningMaxStreak,
                "meaning_current_streak": $meaningCurrentStreak,
                "reading_correct": $readingCorrect,
                "reading_incorrect": $readingIncorrect,
                "reading_max_streak": $readingMaxStreak,
                "reading_current_streak": $readingCurrentStreak,
                "percentage_correct": 90,
                "hidden": false
              }
            }
          ]
        }
    """.trimIndent()

    private fun levelProgressionsJson(startedAt: String) = """
        {
          "object": "collection",
          "url": "https://api.wanikani.com/v2/level_progressions",
          "total_count": 2,
          "data": [
            {
              "id": 1,
              "object": "level_progression",
              "url": "https://api.wanikani.com/v2/level_progressions/1",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2020-01-01T00:00:00.000000Z",
                "level": 11,
                "unlocked_at": "2020-01-01T00:00:00.000000Z",
                "started_at": "2020-01-01T00:00:00.000000Z",
                "passed_at": "2020-02-01T00:00:00.000000Z"
              }
            },
            {
              "id": 2,
              "object": "level_progression",
              "url": "https://api.wanikani.com/v2/level_progressions/2",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "$startedAt",
                "level": 12,
                "unlocked_at": "$startedAt",
                "started_at": "$startedAt"
              }
            }
          ]
        }
    """.trimIndent()

    private fun abandonedResetJson(realLevelStartedAt: String) = """
        {
          "object": "collection",
          "url": "https://api.wanikani.com/v2/level_progressions",
          "total_count": 2,
          "data": [
            {
              "id": 1,
              "object": "level_progression",
              "url": "https://api.wanikani.com/v2/level_progressions/1",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2020-01-01T00:00:00.000000Z",
                "level": 15,
                "unlocked_at": "2020-01-01T00:00:00.000000Z",
                "started_at": "2020-01-01T00:00:00.000000Z",
                "passed_at": null,
                "abandoned_at": "2020-03-01T00:00:00.000000Z"
              }
            },
            {
              "id": 2,
              "object": "level_progression",
              "url": "https://api.wanikani.com/v2/level_progressions/2",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "$realLevelStartedAt",
                "level": 3,
                "unlocked_at": "$realLevelStartedAt",
                "started_at": "$realLevelStartedAt"
              }
            }
          ]
        }
    """.trimIndent()

    private companion object {
        val ALL_LEVELS_PASSED_JSON = """
            {
              "object": "collection",
              "url": "https://api.wanikani.com/v2/level_progressions",
              "total_count": 1,
              "data": [
                {
                  "id": 1,
                  "object": "level_progression",
                  "url": "https://api.wanikani.com/v2/level_progressions/1",
                  "data_updated_at": "2026-01-01T00:00:00.000000Z",
                  "data": {
                    "created_at": "2020-01-01T00:00:00.000000Z",
                    "level": 11,
                    "unlocked_at": "2020-01-01T00:00:00.000000Z",
                    "started_at": "2020-01-01T00:00:00.000000Z",
                    "passed_at": "2020-02-01T00:00:00.000000Z"
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
