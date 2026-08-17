package com.crazyfluff.shellfstudy.core.data

import com.crazyfluff.shellfstudy.shared.data.ApiResult

import app.cash.turbine.test
import com.crazyfluff.shellfstudy.shared.data.model.ItemSpreadBucket
import com.crazyfluff.shellfstudy.shared.data.model.ReviewGrade
import com.crazyfluff.shellfstudy.shared.data.model.ReviewItem
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.database.AssignmentEntity
import com.crazyfluff.shellfstudy.shared.database.SubjectEntity
import com.crazyfluff.shellfstudy.shared.network.MeaningData
import com.crazyfluff.shellfstudy.shared.network.PronunciationAudioData
import com.crazyfluff.shellfstudy.shared.network.PronunciationAudioMetadataData
import com.crazyfluff.shellfstudy.shared.network.ReadingData
import com.crazyfluff.shellfstudy.shared.network.ReviewResultData
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.crazyfluff.shellfstudy.fakes.TestRepositories
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.fakes.jsonResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private fun Instant.truncatedToHour(): Instant = Instant.fromEpochSeconds((epochSeconds / 3600) * 3600)

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
    fun `refreshReviewQueue skips re-syncing assignments once the cache is fresh`() = runTest {
        server.enqueue(jsonResponse(collectionJson(emptyList()))) // subjects sync
        server.enqueue(jsonResponse(assignmentJson(id = 999, availableAt = "2020-01-01T00:00:00.000000Z"))) // assignments sync

        val first = repository.refreshReviewQueue()
        assertThat(first).isInstanceOf(ApiResult.Success::class.java)
        assertThat(server.requestCount).isEqualTo(2)

        // A second call right after should be served entirely from the staleness-gated cache —
        // no further requests, since neither subjects nor assignments are stale yet. Only two
        // responses are enqueued above, so a regression that forces a re-sync here would fail.
        val second = repository.refreshReviewQueue()
        assertThat(second).isInstanceOf(ApiResult.Success::class.java)
        assertThat(server.requestCount).isEqualTo(2)
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
    fun `observeReviewQueue carries the subject's pronunciation audios onto the review item`() = runTest {
        repositories.subjectDao.upsertAll(
            listOf(
                SubjectEntity(
                    id = 440,
                    subjectType = "vocabulary",
                    level = 3,
                    slug = "水",
                    characters = "水",
                    meanings = listOf(MeaningData(meaning = "Water", primary = true)),
                    readings = listOf(ReadingData(reading = "みず", primary = true)),
                    documentUrl = null,
                    pronunciationAudios = listOf(
                        PronunciationAudioData(
                            url = "https://api.wanikani.com/audio/mizu.mp3",
                            contentType = "audio/mpeg",
                            metadata = PronunciationAudioMetadataData(gender = "female", pronunciation = "みず")
                        )
                    ),
                    searchTarget = "水 water みず"
                )
            )
        )
        server.enqueue(jsonResponse(assignmentJson(id = 999, availableAt = "2020-01-01T00:00:00.000000Z")))

        repository.syncAssignments(force = true)

        repository.observeReviewQueue().test {
            val item = awaitItem().first()
            assertThat(item.pronunciationAudios).hasSize(1)
            assertThat(item.pronunciationAudios.first().url).isEqualTo("https://api.wanikani.com/audio/mizu.mp3")
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
    fun `applyOptimisticReviewResult patches the cached stage without any network call`() = runTest {
        seedSubject(id = 1, characters = "口", meaning = "Mouth", reading = "くち")
        repositories.assignmentDao.upsertAll(listOf(seedAssignment(id = 101, subjectId = 1, srsStage = 3)))

        val rankChange = repository.applyOptimisticReviewResult(101, 0, ReviewGrade(meaningCorrect = true, readingCorrect = true))

        assertThat(server.requestCount).isEqualTo(0)
        assertThat(rankChange?.from?.raw).isEqualTo(3)
        assertThat(rankChange?.to?.raw).isEqualTo(4)
        assertThat(repositories.assignmentDao.getById(101)?.srsStage).isEqualTo(4)
    }

    @Test
    fun `applyOptimisticReviewResult demotes the stage on an incorrect answer`() = runTest {
        seedSubject(id = 1, characters = "口", meaning = "Mouth", reading = "くち")
        repositories.assignmentDao.upsertAll(listOf(seedAssignment(id = 101, subjectId = 1, srsStage = 6)))

        val rankChange = repository.applyOptimisticReviewResult(101, 0, ReviewGrade(meaningCorrect = false, readingCorrect = true))

        assertThat(rankChange?.to?.raw).isEqualTo(4)
        assertThat(repositories.assignmentDao.getById(101)?.srsStage).isEqualTo(4)
    }

    @Test
    fun `applyOptimisticReviewResult returns null when the assignment isn't cached yet`() = runTest {
        val rankChange = repository.applyOptimisticReviewResult(999, 0, ReviewGrade(meaningCorrect = true, readingCorrect = true))

        assertThat(rankChange).isNull()
    }

    @Test
    fun `applyOptimisticLessonStart patches from locked to the srs system's starting stage`() = runTest {
        seedSubject(id = 1, characters = "口", meaning = "Mouth", reading = "くち")
        repositories.assignmentDao.upsertAll(listOf(seedAssignment(id = 101, subjectId = 1, srsStage = 0)))

        val rankChange = repository.applyOptimisticLessonStart(101, 0)

        assertThat(rankChange?.from?.raw).isEqualTo(0)
        assertThat(rankChange?.to?.raw).isEqualTo(1)
        assertThat(repositories.assignmentDao.getById(101)?.srsStage).isEqualTo(1)
    }

    @Test
    fun `computeReviewRankChange predicts the same result as applyOptimisticReviewResult, with no DB write`() = runTest {
        seedSubject(id = 1, characters = "口", meaning = "Mouth", reading = "くち")
        repository.warmSrsSystemCache()
        val item = ReviewItem(
            assignmentId = 101, subjectId = 1, subjectType = SubjectType.RADICAL, characters = "口",
            level = 3, srsStage = 3, meanings = listOf("Mouth"), readings = listOf("くち"), srsSystemId = 0
        )

        val rankChange = repository.computeReviewRankChange(item, ReviewGrade(meaningCorrect = true, readingCorrect = true))

        assertThat(rankChange?.from?.raw).isEqualTo(3)
        assertThat(rankChange?.to?.raw).isEqualTo(4)
        // Purely a synchronous prediction — nothing was actually persisted.
        assertThat(repositories.assignmentDao.getById(101)).isNull()
    }

    @Test
    fun `computeReviewRankChange returns null before the cache is warmed`() = runTest {
        val item = ReviewItem(
            assignmentId = 101, subjectId = 1, subjectType = SubjectType.RADICAL, characters = "口",
            level = 3, srsStage = 3, meanings = listOf("Mouth"), readings = emptyList(), srsSystemId = 0
        )

        val rankChange = repository.computeReviewRankChange(item, ReviewGrade(meaningCorrect = true, readingCorrect = true))

        assertThat(rankChange).isNull()
    }

    @Test
    fun `reconcileAfterReviewResult overwrites the local prediction with the server-confirmed stage`() = runTest {
        seedSubject(id = 1, characters = "口", meaning = "Mouth", reading = "くち")
        repositories.assignmentDao.upsertAll(listOf(seedAssignment(id = 101, subjectId = 1, srsStage = 4)))
        // Pretend a concurrent submission elsewhere already advanced this further than our own
        // local prediction would have — the server's value must win regardless.
        repository.reconcileAfterReviewResult(
            ReviewResultData(
                assignmentId = 101, subjectId = 1, startingSrsStage = 4, endingSrsStage = 6,
                incorrectMeaningAnswers = 0, incorrectReadingAnswers = 0, createdAt = "2026-01-01T00:00:00.000000Z"
            )
        )

        assertThat(repositories.assignmentDao.getById(101)?.srsStage).isEqualTo(6)
    }

    @Test
    fun `refetchAssignment re-fetches and upserts a single assignment from the network`() = runTest {
        repositories.assignmentDao.upsertAll(listOf(seedAssignment(id = 101, subjectId = 1, srsStage = 2)))
        server.enqueue(
            jsonResponse(
                """
                {
                  "id": 101, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/101",
                  "data_updated_at": "2026-01-01T00:00:00.000000Z",
                  "data": {
                    "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": 1, "subject_type": "radical",
                    "srs_stage": 5, "hidden": false
                  }
                }
                """.trimIndent()
            )
        )

        val result = repository.refetchAssignment(101)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(repositories.assignmentDao.getById(101)?.srsStage).isEqualTo(5)
    }

    private fun seedAssignment(id: Long, subjectId: Long, srsStage: Int) = AssignmentEntity(
        id = id, subjectId = subjectId, subjectType = "radical", srsStage = srsStage,
        createdAt = "2026-01-01T00:00:00.000000Z", hidden = false
    )

    @Test
    fun `observeLessonsCompletedToday counts assignments started today`() = runTest {
        val now = Clock.System.now().toString()
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
    fun `observeLevelProgress maps each item's current srsStage`() = runTest {
        seedSubject(id = 1, level = 12, characters = "一", meaning = "One", reading = "いち")
        seedSubject(id = 2, level = 12, characters = "二", meaning = "Two", reading = "に")
        server.enqueue(
            jsonResponse(
                collectionJson(
                    listOf(
                        assignmentData(id = 201, subjectId = 1, srsStage = 5, unlockedAt = "2020-01-01T00:00:00.000000Z"),
                        assignmentData(id = 202, subjectId = 2, srsStage = 2, unlockedAt = "2020-01-01T00:00:00.000000Z")
                    )
                )
            )
        )

        repository.syncAssignments(force = true)

        repository.observeLevelProgress(12).test {
            val progress = awaitItem()
            val kanji = progress.breakdown.first { it.subjectType == SubjectType.KANJI }
            assertThat(kanji.items.first { it.subjectId == 1L }.srsStage).isEqualTo(SrsStage.GURU_1)
            assertThat(kanji.items.first { it.subjectId == 2L }.srsStage).isEqualTo(SrsStage.APPRENTICE_2)
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

    @Test
    fun `observeSrsItemSpread breaks each stage down by subject type, folding kana-vocabulary into vocabulary`() = runTest {
        seedSubject(id = 1, characters = "1", meaning = "a", reading = "a", subjectType = "radical")
        seedSubject(id = 2, characters = "2", meaning = "b", reading = "b", subjectType = "kanji")
        seedSubject(id = 3, characters = "3", meaning = "c", reading = "c", subjectType = "vocabulary")
        seedSubject(id = 4, characters = "4", meaning = "d", reading = "d", subjectType = "kana_vocabulary")
        seedSubject(id = 5, characters = "5", meaning = "e", reading = "e", subjectType = "kanji") // never started -> locked
        server.enqueue(
            jsonResponse(
                collectionJson(
                    listOf(
                        assignmentData(id = 1, subjectId = 1, subjectType = "radical", srsStage = 5, startedAt = "2020-01-01T00:00:00.000000Z"),
                        assignmentData(id = 2, subjectId = 2, subjectType = "kanji", srsStage = 5, startedAt = "2020-01-01T00:00:00.000000Z"),
                        assignmentData(id = 3, subjectId = 3, subjectType = "vocabulary", srsStage = 5, startedAt = "2020-01-01T00:00:00.000000Z"),
                        assignmentData(id = 4, subjectId = 4, subjectType = "kana_vocabulary", srsStage = 5, startedAt = "2020-01-01T00:00:00.000000Z")
                    )
                )
            )
        )

        repository.syncAssignments(force = true)

        repository.observeSrsItemSpread().test {
            val spread = awaitItem()
            assertThat(spread.guruCount).isEqualTo(4)
            val guruByType = spread.countsByType.getValue(ItemSpreadBucket.GURU)
            assertThat(guruByType[SubjectType.RADICAL]).isEqualTo(1)
            assertThat(guruByType[SubjectType.KANJI]).isEqualTo(1)
            assertThat(guruByType[SubjectType.VOCABULARY]).isEqualTo(2)

            assertThat(spread.lockedCount).isEqualTo(1)
            val lockedByType = spread.countsByType.getValue(ItemSpreadBucket.LOCKED)
            assertThat(lockedByType[SubjectType.KANJI]).isEqualTo(1)
        }
    }

    @Test
    fun `observeReviewForecast labels buckets by clock-hour boundary, not raw now-offset`() = runTest {
        // Regression test: bucket labels used to be `now + N hours` (e.g. 2:47 + 1h = 3:47),
        // reading almost an hour behind the actual on-the-hour availableAt they described. They
        // must instead land on the real clock hour the assignment becomes due.
        seedSubject(id = 1, characters = "一", meaning = "One", reading = "いち")
        val nextHour = Clock.System.now().truncatedToHour() + 1.hours
        server.enqueue(
            jsonResponse(
                collectionJson(listOf(assignmentData(id = 1, subjectId = 1, availableAt = nextHour.toString())))
            )
        )

        repository.syncAssignments(force = true)

        repository.observeReviewForecast().test {
            val forecast = awaitItem()
            val bucket = forecast.buckets.first { it.newlyAvailableCount == 1 }
            assertThat(bucket.availableAt).isEqualTo(nextHour)
        }
    }

    @Test
    fun `observeReviewForecast groups both the now-count and each bucket's count by subject type`() = runTest {
        val now = Clock.System.now()
        val nextHour = now.truncatedToHour() + 1.hours
        repositories.assignmentDao.upsertAll(
            listOf(
                AssignmentEntity(
                    id = 1, subjectId = 1, subjectType = "radical", srsStage = 1,
                    createdAt = "2026-01-01T00:00:00.000000Z", availableAt = nextHour.toString(), hidden = false
                ),
                AssignmentEntity(
                    id = 2, subjectId = 2, subjectType = "kanji", srsStage = 1,
                    createdAt = "2026-01-01T00:00:00.000000Z", availableAt = nextHour.toString(), hidden = false
                ),
                AssignmentEntity(
                    id = 3, subjectId = 3, subjectType = "vocabulary", srsStage = 1,
                    createdAt = "2026-01-01T00:00:00.000000Z", availableAt = (now - 60.seconds).toString(), hidden = false
                )
            )
        )

        repository.observeReviewForecast().test {
            val forecast = awaitItem()
            assertThat(forecast.availableNowCountsByType[SubjectType.VOCABULARY]).isEqualTo(1)
            val bucket = forecast.buckets.first { it.availableAt == nextHour }
            assertThat(bucket.countsByType[SubjectType.RADICAL]).isEqualTo(1)
            assertThat(bucket.countsByType[SubjectType.KANJI]).isEqualTo(1)
        }
    }

    private suspend fun seedSubject(
        id: Long,
        characters: String,
        meaning: String,
        reading: String,
        level: Int = 3,
        subjectType: String = "kanji",
        meaningMnemonic: String? = null,
        readingMnemonic: String? = null
    ) {
        repositories.subjectDao.upsertAll(
            listOf(
                SubjectEntity(
                    id = id,
                    subjectType = subjectType,
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
        subjectType: String = "kanji",
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
            "subject_type": "$subjectType",
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
    ) = collectionJson(listOf(assignmentData(id = id, subjectId = subjectId, srsStage = srsStage, availableAt = availableAt, unlockedAt = unlockedAt, startedAt = startedAt)))

    private fun collectionJson(items: List<String>) = """
        {
          "object": "collection",
          "url": "https://api.wanikani.com/v2/assignments",
          "total_count": ${items.size},
          "data": [${items.joinToString(",")}]
        }
    """.trimIndent()

    // --- subject-drop and passedAt/burnedAt tests ---

    @Test
    fun `observeReviewQueue silently drops an assignment whose subject is not in the DB`() = runTest {
        // Seed an assignment that is due but deliberately omit the corresponding subject row —
        // this models an incomplete sync (e.g. subjects sync was interrupted). The queue must
        // return empty rather than crash, per the mapNotNull silent-drop in the repository.
        repositories.assignmentDao.upsertAll(
            listOf(
                AssignmentEntity(
                    id = 999, subjectId = 9999, subjectType = "radical", srsStage = 1,
                    createdAt = "2026-01-01T00:00:00.000000Z",
                    availableAt = "2020-01-01T00:00:00.000000Z", hidden = false
                )
            )
        )

        repository.observeReviewQueue().test {
            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test
    fun `observeAssignmentStats returns null when the subject has not been lessoned`() = runTest {
        repository.observeAssignmentStats(12345).test {
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `observeAssignmentStats maps srsStage, next review, and every lifecycle date`() = runTest {
        repositories.assignmentDao.upsertAll(
            listOf(
                AssignmentEntity(
                    id = 500, subjectId = 440, subjectType = "kanji", srsStage = 5,
                    createdAt = "2020-01-01T00:00:00.000000Z",
                    unlockedAt = "2026-01-02T00:00:00.000000Z",
                    startedAt = "2026-01-03T00:00:00.000000Z",
                    passedAt = "2026-01-20T00:00:00.000000Z",
                    burnedAt = null,
                    availableAt = "2026-01-26T03:00:00.000000Z",
                    hidden = false
                )
            )
        )

        repository.observeAssignmentStats(440).test {
            val stats = awaitItem()!!
            assertThat(stats.srsStage).isEqualTo(SrsStage.GURU_1)
            assertThat(stats.nextReviewAt).isEqualTo(Instant.parse("2026-01-26T03:00:00.000000Z"))
            assertThat(stats.unlockedAt).isEqualTo(Instant.parse("2026-01-02T00:00:00.000000Z"))
            assertThat(stats.startedAt).isEqualTo(Instant.parse("2026-01-03T00:00:00.000000Z"))
            assertThat(stats.passedAt).isEqualTo(Instant.parse("2026-01-20T00:00:00.000000Z"))
            assertThat(stats.burnedAt).isNull()
        }
    }

    @Test
    fun `applyOptimisticReviewResult sets passedAt when advancing to the passing stage`() = runTest {
        seedSubject(id = 1, characters = "口", meaning = "Mouth", reading = "くち")
        repositories.assignmentDao.upsertAll(listOf(seedAssignment(id = 101, subjectId = 1, srsStage = 4)))

        repository.applyOptimisticReviewResult(101, 0, ReviewGrade(meaningCorrect = true, readingCorrect = true))

        val updated = repositories.assignmentDao.getById(101)!!
        assertThat(updated.srsStage).isEqualTo(5) // passed into Guru I
        assertThat(updated.passedAt).isNotNull()
        assertThat(updated.burnedAt).isNull()
    }

    @Test
    fun `applyOptimisticReviewResult sets burnedAt when advancing to the burning stage`() = runTest {
        seedSubject(id = 1, characters = "口", meaning = "Mouth", reading = "くち")
        repositories.assignmentDao.upsertAll(listOf(seedAssignment(id = 101, subjectId = 1, srsStage = 8)))

        repository.applyOptimisticReviewResult(101, 0, ReviewGrade(meaningCorrect = true, readingCorrect = true))

        val updated = repositories.assignmentDao.getById(101)!!
        assertThat(updated.srsStage).isEqualTo(9)
        assertThat(updated.burnedAt).isNotNull()
    }

    @Test
    fun `applyOptimisticReviewResult does not overwrite an already-set passedAt`() = runTest {
        seedSubject(id = 1, characters = "口", meaning = "Mouth", reading = "くち")
        val originalPassedAt = "2025-01-01T00:00:00.000000Z"
        repositories.assignmentDao.upsertAll(
            listOf(
                AssignmentEntity(
                    id = 101, subjectId = 1, subjectType = "radical", srsStage = 5,
                    createdAt = "2026-01-01T00:00:00.000000Z", hidden = false,
                    passedAt = originalPassedAt
                )
            )
        )

        repository.applyOptimisticReviewResult(101, 0, ReviewGrade(meaningCorrect = true, readingCorrect = true))

        assertThat(repositories.assignmentDao.getById(101)?.passedAt).isEqualTo(originalPassedAt)
    }

    @Test
    fun `applyOptimisticReviewResult never sets passedAt on an incorrect answer that drops the stage`() = runTest {
        seedSubject(id = 1, characters = "口", meaning = "Mouth", reading = "くち")
        repositories.assignmentDao.upsertAll(listOf(seedAssignment(id = 101, subjectId = 1, srsStage = 5)))

        repository.applyOptimisticReviewResult(101, 0, ReviewGrade(meaningCorrect = false, readingCorrect = true))

        val updated = repositories.assignmentDao.getById(101)!!
        assertThat(updated.srsStage).isLessThan(5)
        assertThat(updated.passedAt).isNull()
    }

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
