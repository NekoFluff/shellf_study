package com.crazyfluff.shellfstudy.core.data

import app.cash.turbine.test
import com.crazyfluff.shellfstudy.core.database.studyactivity.StudyActivityDayEntity
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
