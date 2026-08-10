package com.crazyfluff.shellfstudy.core.data

import app.cash.turbine.test
import com.crazyfluff.shellfstudy.core.network.ReviewResultData
import com.crazyfluff.shellfstudy.fakes.TestRepositories
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.fakes.jsonResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
    fun `logReviewEvent then observeReviewsCompletedStats reflects the logged review`() = runTest {
        repository.logReviewEvent(reviewResult(createdAt = Instant.now().toString()))

        repository.observeReviewsCompletedStats().test {
            val stats = awaitItem()
            assertThat(stats.today).isEqualTo(1)
            assertThat(stats.allTime).isEqualTo(1)
        }
    }

    @Test
    fun `study streak counts consecutive days ending today`() = runTest {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        repository.logReviewEvent(reviewResult(createdAt = today.atStartOfDay(zone).toInstant().toString()))
        repository.logReviewEvent(reviewResult(createdAt = today.minusDays(1).atStartOfDay(zone).toInstant().toString()))

        repository.observeStudyStreak().test {
            val streak = awaitItem()
            assertThat(streak.isActiveToday).isTrue()
            assertThat(streak.currentStreakDays).isEqualTo(2)
        }
    }

    private fun reviewResult(createdAt: String) = ReviewResultData(
        assignmentId = 1,
        subjectId = 440,
        startingSrsStage = 3,
        endingSrsStage = 4,
        incorrectMeaningAnswers = 0,
        incorrectReadingAnswers = 0,
        createdAt = createdAt
    )

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
