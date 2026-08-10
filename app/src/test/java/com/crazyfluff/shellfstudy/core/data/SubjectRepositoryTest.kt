package com.crazyfluff.shellfstudy.core.data

import app.cash.turbine.test
import com.crazyfluff.shellfstudy.fakes.TestRepositories
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.fakes.jsonResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class SubjectRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repositories: TestRepositories
    private val repository get() = repositories.subjectRepository

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
    fun `syncSubjects bulk-fetches the full library without an ids filter, unlike a single review session`() = runTest {
        server.enqueue(jsonResponse(SUBJECTS_JSON))

        val result = repository.syncSubjects(force = true)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.path).doesNotContain("ids=")

        repository.observeTotalSubjectCount().test {
            assertThat(awaitItem()).isEqualTo(1)
        }
    }

    @Test
    fun `observeSearch finds a subject never encountered in a review session, by meaning`() = runTest {
        server.enqueue(jsonResponse(SUBJECTS_JSON))
        repository.syncSubjects(force = true)

        repository.observeSearch("wat").test {
            val results = awaitItem()
            assertThat(results).hasSize(1)
            assertThat(results.first().characters).isEqualTo("水")
        }
    }

    @Test
    fun `observeSearch finds a subject by character`() = runTest {
        server.enqueue(jsonResponse(SUBJECTS_JSON))
        repository.syncSubjects(force = true)

        repository.observeSearch("水").test {
            assertThat(awaitItem()).hasSize(1)
        }
    }

    private companion object {
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
    }
}
