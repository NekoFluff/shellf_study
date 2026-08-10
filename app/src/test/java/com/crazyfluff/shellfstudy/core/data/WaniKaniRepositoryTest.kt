package com.crazyfluff.shellfstudy.core.data

import app.cash.turbine.test
import com.crazyfluff.shellfstudy.fakes.FakeAssignmentDao
import com.crazyfluff.shellfstudy.fakes.FakeSubjectDao
import com.crazyfluff.shellfstudy.fakes.buildTestApi
import com.google.common.truth.Truth.assertThat
import com.crazyfluff.shellfstudy.fakes.jsonResponse
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class WaniKaniRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: WaniKaniRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = WaniKaniRepository(
            api = buildTestApi(server.url("/").toString()),
            subjectDao = FakeSubjectDao(),
            assignmentDao = FakeAssignmentDao()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchUser returns success with parsed user`() = runTest {
        server.enqueue(jsonResponse(USER_JSON))

        val result = repository.fetchUser()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val user = (result as ApiResult.Success).data
        assertThat(user.username).isEqualTo("durtle_fan")
        assertThat(user.level).isEqualTo(12)
    }

    @Test
    fun `fetchUser maps 401 to invalid token error`() = runTest {
        server.enqueue(jsonResponse("""{"error":"Unauthorized"}""", 401))

        val result = repository.fetchUser()

        assertThat(result).isInstanceOf(ApiResult.Error::class.java)
        assertThat((result as ApiResult.Error).message).contains("Invalid API token")
    }

    @Test
    fun `fetchDashboardSummary sums available subject ids`() = runTest {
        server.enqueue(jsonResponse(SUMMARY_JSON))

        val result = repository.fetchDashboardSummary()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val summary = (result as ApiResult.Success).data
        assertThat(summary.lessonCount).isEqualTo(2)
        assertThat(summary.reviewCount).isEqualTo(3)
    }

    @Test
    fun `refreshReviewQueue caches assignments and subjects, observeReviewQueue emits them`() = runTest {
        server.enqueue(jsonResponse(ASSIGNMENTS_JSON))
        server.enqueue(jsonResponse(SUBJECTS_JSON))

        val refreshResult = repository.refreshReviewQueue()
        assertThat(refreshResult).isInstanceOf(ApiResult.Success::class.java)

        repository.observeReviewQueue().test {
            val items = awaitItem()
            assertThat(items).hasSize(1)
            assertThat(items.first().characters).isEqualTo("水")
            assertThat(items.first().meanings).contains("Water")
        }
    }

    @Test
    fun `refreshLessonQueue caches assignments and subjects, observeLessonQueue emits them with mnemonics`() = runTest {
        server.enqueue(jsonResponse(LESSON_ASSIGNMENTS_JSON))
        server.enqueue(jsonResponse(LESSON_SUBJECTS_JSON))

        val refreshResult = repository.refreshLessonQueue()
        assertThat(refreshResult).isInstanceOf(ApiResult.Success::class.java)

        repository.observeLessonQueue().test {
            val items = awaitItem()
            assertThat(items).hasSize(1)
            assertThat(items.first().characters).isEqualTo("水")
            assertThat(items.first().meaningMnemonic).isEqualTo("A stream of water.")
            assertThat(items.first().readingMnemonic).isEqualTo("Sounds like mizu.")
        }
    }

    @Test
    fun `startAssignment posts to the start endpoint`() = runTest {
        server.enqueue(jsonResponse(START_ASSIGNMENT_RESULT_JSON))

        val result = repository.startAssignment(777)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).contains("/assignments/777/start")
    }

    @Test
    fun `fetchLessonsCompletedToday returns the total count from the assignments page`() = runTest {
        server.enqueue(jsonResponse(ASSIGNMENTS_PAGE_JSON))

        val result = repository.fetchLessonsCompletedToday()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data).isEqualTo(4)
        val request = server.takeRequest()
        assertThat(request.path).contains("started_after")
    }

    @Test
    fun `fetchLevelUpProgress counts guru-or-higher kanji out of the total`() = runTest {
        server.enqueue(jsonResponse(LEVEL_UP_ASSIGNMENTS_JSON))

        val result = repository.fetchLevelUpProgress(level = 12)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val progress = (result as ApiResult.Success).data
        assertThat(progress.kanjiTotal).isEqualTo(2)
        assertThat(progress.kanjiGuruedOrHigher).isEqualTo(1)
        val request = server.takeRequest()
        assertThat(request.path).contains("levels")
        assertThat(request.path).contains("subject_types")
    }

    @Test
    fun `fetchDaysOnCurrentLevel computes days since the in-progress level was unlocked`() = runTest {
        server.enqueue(jsonResponse(levelProgressionsJson(startedAt = "2026-01-01T00:00:00.000000Z")))

        val result = repository.fetchDaysOnCurrentLevel()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data).isNotNull()
    }

    @Test
    fun `fetchDaysOnCurrentLevel returns null when every level has been passed`() = runTest {
        server.enqueue(jsonResponse(ALL_LEVELS_PASSED_JSON))

        val result = repository.fetchDaysOnCurrentLevel()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data).isNull()
    }

    @Test
    fun `submitReview posts incorrect counts derived from grade`() = runTest {
        server.enqueue(jsonResponse(REVIEW_RESULT_JSON))

        val result = repository.submitReview(
            assignmentId = 555,
            grade = com.crazyfluff.shellfstudy.core.data.model.ReviewGrade(
                meaningCorrect = true,
                readingCorrect = false
            )
        )

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.body.readUtf8()).contains("\"incorrect_reading_answers\":1")
    }

    private companion object {
        val USER_JSON = """
            {
              "object": "user",
              "url": "https://api.wanikani.com/v2/user",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "id": "abc-123",
                "username": "durtle_fan",
                "level": 12,
                "profile_url": "https://www.wanikani.com/users/durtle_fan",
                "started_at": "2020-01-01T00:00:00.000000Z"
              }
            }
        """.trimIndent()

        val SUMMARY_JSON = """
            {
              "object": "report",
              "url": "https://api.wanikani.com/v2/summary",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "lessons": [{"available_at": "2026-01-01T00:00:00.000000Z", "subject_ids": [1, 2]}],
                "reviews": [{"available_at": "2026-01-01T00:00:00.000000Z", "subject_ids": [3, 4, 5]}]
              }
            }
        """.trimIndent()

        val ASSIGNMENTS_JSON = """
            {
              "object": "collection",
              "url": "https://api.wanikani.com/v2/assignments",
              "total_count": 1,
              "data": [
                {
                  "id": 999,
                  "object": "assignment",
                  "url": "https://api.wanikani.com/v2/assignments/999",
                  "data_updated_at": "2026-01-01T00:00:00.000000Z",
                  "data": {
                    "created_at": "2026-01-01T00:00:00.000000Z",
                    "subject_id": 440,
                    "subject_type": "kanji",
                    "srs_stage": 3,
                    "available_at": "2026-01-01T00:00:00.000000Z",
                    "hidden": false
                  }
                }
              ]
            }
        """.trimIndent()

        val SUBJECTS_JSON = """
            {
              "object": "collection",
              "url": "https://api.wanikani.com/v2/subjects",
              "total_count": 1,
              "data": [
                {
                  "id": 440,
                  "object": "kanji",
                  "url": "https://api.wanikani.com/v2/subjects/440",
                  "data_updated_at": "2026-01-01T00:00:00.000000Z",
                  "data": {
                    "created_at": "2020-01-01T00:00:00.000000Z",
                    "level": 3,
                    "slug": "水",
                    "characters": "水",
                    "meanings": [{"meaning": "Water", "primary": true, "accepted_meaning": true}],
                    "readings": [{"reading": "みず", "primary": true, "accepted_reading": true}]
                  }
                }
              ]
            }
        """.trimIndent()

        val LESSON_ASSIGNMENTS_JSON = """
            {
              "object": "collection",
              "url": "https://api.wanikani.com/v2/assignments",
              "total_count": 1,
              "data": [
                {
                  "id": 888,
                  "object": "assignment",
                  "url": "https://api.wanikani.com/v2/assignments/888",
                  "data_updated_at": "2026-01-01T00:00:00.000000Z",
                  "data": {
                    "created_at": "2026-01-01T00:00:00.000000Z",
                    "subject_id": 440,
                    "subject_type": "kanji",
                    "srs_stage": 0,
                    "unlocked_at": "2026-01-01T00:00:00.000000Z",
                    "hidden": false
                  }
                }
              ]
            }
        """.trimIndent()

        val LESSON_SUBJECTS_JSON = """
            {
              "object": "collection",
              "url": "https://api.wanikani.com/v2/subjects",
              "total_count": 1,
              "data": [
                {
                  "id": 440,
                  "object": "kanji",
                  "url": "https://api.wanikani.com/v2/subjects/440",
                  "data_updated_at": "2026-01-01T00:00:00.000000Z",
                  "data": {
                    "created_at": "2020-01-01T00:00:00.000000Z",
                    "level": 3,
                    "slug": "水",
                    "characters": "水",
                    "meanings": [{"meaning": "Water", "primary": true, "accepted_meaning": true}],
                    "readings": [{"reading": "みず", "primary": true, "accepted_reading": true}],
                    "meaning_mnemonic": "A stream of water.",
                    "reading_mnemonic": "Sounds like mizu."
                  }
                }
              ]
            }
        """.trimIndent()

        val START_ASSIGNMENT_RESULT_JSON = """
            {
              "id": 777,
              "object": "assignment",
              "url": "https://api.wanikani.com/v2/assignments/777",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2026-01-01T00:00:00.000000Z",
                "subject_id": 440,
                "subject_type": "kanji",
                "srs_stage": 1,
                "started_at": "2026-01-01T00:00:00.000000Z",
                "hidden": false
              }
            }
        """.trimIndent()

        val ASSIGNMENTS_PAGE_JSON = """
            {
              "object": "collection",
              "url": "https://api.wanikani.com/v2/assignments",
              "total_count": 4,
              "data": []
            }
        """.trimIndent()

        val LEVEL_UP_ASSIGNMENTS_JSON = """
            {
              "object": "collection",
              "url": "https://api.wanikani.com/v2/assignments",
              "total_count": 2,
              "data": [
                {
                  "id": 201,
                  "object": "assignment",
                  "url": "https://api.wanikani.com/v2/assignments/201",
                  "data_updated_at": "2026-01-01T00:00:00.000000Z",
                  "data": {
                    "created_at": "2026-01-01T00:00:00.000000Z",
                    "subject_id": 440,
                    "subject_type": "kanji",
                    "srs_stage": 5,
                    "hidden": false
                  }
                },
                {
                  "id": 202,
                  "object": "assignment",
                  "url": "https://api.wanikani.com/v2/assignments/202",
                  "data_updated_at": "2026-01-01T00:00:00.000000Z",
                  "data": {
                    "created_at": "2026-01-01T00:00:00.000000Z",
                    "subject_id": 441,
                    "subject_type": "kanji",
                    "srs_stage": 3,
                    "hidden": false
                  }
                }
              ]
            }
        """.trimIndent()

        fun levelProgressionsJson(startedAt: String) = """
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

        val REVIEW_RESULT_JSON = """
            {
              "id": 1,
              "object": "review",
              "url": "https://api.wanikani.com/v2/reviews/1",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "assignment_id": 555,
                "subject_id": 440,
                "starting_srs_stage": 3,
                "ending_srs_stage": 2,
                "incorrect_meaning_answers": 0,
                "incorrect_reading_answers": 1,
                "created_at": "2026-01-01T00:00:00.000000Z"
              }
            }
        """.trimIndent()
    }
}
