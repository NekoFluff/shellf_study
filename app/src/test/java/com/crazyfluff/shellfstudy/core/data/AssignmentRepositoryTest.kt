package com.crazyfluff.shellfstudy.core.data

import app.cash.turbine.test
import com.crazyfluff.shellfstudy.core.database.SubjectEntity
import com.crazyfluff.shellfstudy.core.network.MeaningData
import com.crazyfluff.shellfstudy.core.network.ReadingData
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

class AssignmentRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repositories: TestRepositories
    private val repository get() = repositories.assignmentRepository

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
    fun `syncAssignments caches assignments, observeReviewQueue emits due items with subject content`() = runTest {
        seedSubject(id = 440, characters = "水", meaning = "Water", reading = "みず")
        server.enqueue(jsonResponse(assignmentJson(id = 999, availableAt = "2020-01-01T00:00:00.000000Z")))

        val result = repository.syncAssignments(force = true)
        assertThat(result).isInstanceOf(ApiResult.Success::class.java)

        repository.observeReviewQueue().test {
            val items = awaitItem()
            assertThat(items).hasSize(1)
            assertThat(items.first().characters).isEqualTo("水")
            assertThat(items.first().meanings).contains("Water")
        }
    }

    @Test
    fun `observeLessonQueue emits unlocked-not-started items with mnemonics`() = runTest {
        seedSubject(
            id = 440, characters = "水", meaning = "Water", reading = "みず",
            meaningMnemonic = "A stream of water.", readingMnemonic = "Sounds like mizu."
        )
        server.enqueue(jsonResponse(assignmentJson(id = 888, unlockedAt = "2026-01-01T00:00:00.000000Z")))

        repository.syncAssignments(force = true)

        repository.observeLessonQueue().test {
            val items = awaitItem()
            assertThat(items).hasSize(1)
            assertThat(items.first().meaningMnemonic).isEqualTo("A stream of water.")
            assertThat(items.first().readingMnemonic).isEqualTo("Sounds like mizu.")
        }
    }

    @Test
    fun `startAssignment posts to the start endpoint`() = runTest {
        server.enqueue(jsonResponse(startAssignmentResultJson(id = 777)))

        val result = repository.startAssignment(777)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).contains("/assignments/777/start")
    }

    @Test
    fun `observeLessonsCompletedToday counts assignments started today`() = runTest {
        val now = Instant.now().toString()
        server.enqueue(jsonResponse(assignmentJson(id = 1, startedAt = now)))

        repository.syncAssignments(force = true)

        repository.observeLessonsCompletedToday().test {
            assertThat(awaitItem()).isEqualTo(1)
        }
    }

    @Test
    fun `observeLevelUpProgress counts guru-or-higher kanji out of the total`() = runTest {
        seedSubject(id = 1, level = 12, characters = "一", meaning = "One", reading = "いち")
        seedSubject(id = 2, level = 12, characters = "二", meaning = "Two", reading = "に")
        server.enqueue(
            jsonResponse(
                collectionJson(
                    listOf(
                        assignmentData(id = 201, subjectId = 1, srsStage = 5, unlockedAt = "2020-01-01T00:00:00.000000Z"),
                        assignmentData(id = 202, subjectId = 2, srsStage = 3, unlockedAt = "2020-01-01T00:00:00.000000Z")
                    )
                )
            )
        )

        repository.syncAssignments(force = true)

        repository.observeLevelUpProgress(12).test {
            val progress = awaitItem()
            assertThat(progress.kanjiTotal).isEqualTo(2)
            assertThat(progress.kanjiGuruedOrHigher).isEqualTo(1)
        }
    }

    @Test
    fun `observeSrsItemSpread buckets started assignments by srs stage`() = runTest {
        seedSubject(id = 1, characters = "1", meaning = "a", reading = "a")
        seedSubject(id = 2, characters = "2", meaning = "b", reading = "b")
        server.enqueue(
            jsonResponse(
                collectionJson(
                    listOf(
                        assignmentData(id = 1, subjectId = 1, srsStage = 2, startedAt = "2020-01-01T00:00:00.000000Z"),
                        assignmentData(id = 2, subjectId = 2, srsStage = 9, startedAt = "2020-01-01T00:00:00.000000Z")
                    )
                )
            )
        )

        repository.syncAssignments(force = true)

        repository.observeSrsItemSpread().test {
            val spread = awaitItem()
            assertThat(spread.apprenticeCount).isEqualTo(1)
            assertThat(spread.burnedCount).isEqualTo(1)
            assertThat(spread.lockedCount).isEqualTo(0)
        }
    }

    private suspend fun seedSubject(
        id: Long,
        characters: String,
        meaning: String,
        reading: String,
        level: Int = 3,
        meaningMnemonic: String? = null,
        readingMnemonic: String? = null
    ) {
        repositories.subjectDao.upsertAll(
            listOf(
                SubjectEntity(
                    id = id,
                    subjectType = "kanji",
                    level = level,
                    slug = characters,
                    characters = characters,
                    meanings = listOf(MeaningData(meaning = meaning, primary = true)),
                    readings = listOf(ReadingData(reading = reading, primary = true)),
                    documentUrl = null,
                    meaningMnemonic = meaningMnemonic,
                    readingMnemonic = readingMnemonic,
                    searchTarget = "$characters $meaning $reading".lowercase()
                )
            )
        )
    }

    private fun assignmentData(
        id: Long,
        subjectId: Long = 440,
        srsStage: Int = 3,
        availableAt: String? = null,
        unlockedAt: String? = null,
        startedAt: String? = null
    ) = """
        {
          "id": $id,
          "object": "assignment",
          "url": "https://api.wanikani.com/v2/assignments/$id",
          "data_updated_at": "2026-01-01T00:00:00.000000Z",
          "data": {
            "created_at": "2026-01-01T00:00:00.000000Z",
            "subject_id": $subjectId,
            "subject_type": "kanji",
            "srs_stage": $srsStage
            ${availableAt?.let { ", \"available_at\": \"$it\"" } ?: ""}
            ${unlockedAt?.let { ", \"unlocked_at\": \"$it\"" } ?: ""}
            ${startedAt?.let { ", \"started_at\": \"$it\"" } ?: ""}
            , "hidden": false
          }
        }
    """.trimIndent()

    private fun assignmentJson(
        id: Long,
        subjectId: Long = 440,
        srsStage: Int = 3,
        availableAt: String? = null,
        unlockedAt: String? = null,
        startedAt: String? = null
    ) = collectionJson(listOf(assignmentData(id, subjectId, srsStage, availableAt, unlockedAt, startedAt)))

    private fun collectionJson(items: List<String>) = """
        {
          "object": "collection",
          "url": "https://api.wanikani.com/v2/assignments",
          "total_count": ${items.size},
          "data": [${items.joinToString(",")}]
        }
    """.trimIndent()

    private fun startAssignmentResultJson(id: Long) = """
        {
          "id": $id,
          "object": "assignment",
          "url": "https://api.wanikani.com/v2/assignments/$id",
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
}
