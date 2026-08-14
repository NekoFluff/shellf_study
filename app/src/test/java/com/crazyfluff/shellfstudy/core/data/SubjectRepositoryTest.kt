package com.crazyfluff.shellfstudy.core.data

import com.crazyfluff.shellfstudy.shared.data.ApiResult

import app.cash.turbine.test
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.database.SubjectEntity
import com.crazyfluff.shellfstudy.shared.network.MeaningData
import com.crazyfluff.shellfstudy.shared.network.ReadingData
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
    fun `observeSubjectDetail merges bundled pitch accent data into a vocabulary subject`() = runTest {
        val withPitchAccent = buildTestRepositories(
            server.url("/").toString(),
            pitchAccentEntries = mapOf("水" to listOf(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0)))
        )
        withPitchAccent.subjectDao.upsertAll(
            listOf(
                SubjectEntity(
                    id = 900,
                    subjectType = "vocabulary",
                    level = 1,
                    slug = "水",
                    characters = "水",
                    meanings = listOf(MeaningData(meaning = "Water", primary = true)),
                    readings = listOf(ReadingData(reading = "みず", primary = true)),
                    documentUrl = null,
                    searchTarget = "水 water"
                )
            )
        )

        withPitchAccent.subjectRepository.observeSubjectDetail(900).test {
            val detail = awaitItem()
            assertThat(detail?.pitchAccents).containsExactly(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0))
        }
    }

    @Test
    fun `observeSubjectDetail leaves pitch accents empty for a kanji subject even if the dictionary has an entry`() = runTest {
        val withPitchAccent = buildTestRepositories(
            server.url("/").toString(),
            pitchAccentEntries = mapOf("水" to listOf(PitchAccent(reading = "スイ", partOfSpeech = null, pitchNumber = 1)))
        )
        server.enqueue(jsonResponse(SUBJECTS_JSON))
        withPitchAccent.subjectRepository.syncSubjects(force = true)

        withPitchAccent.subjectRepository.observeSubjectDetail(440).test {
            val detail = awaitItem()
            assertThat(detail?.pitchAccents).isEmpty()
        }
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

    @Test
    fun `syncSubjects preserves raw WK markup instead of stripping it, for colored mnemonic rendering`() = runTest {
        server.enqueue(jsonResponse(SUBJECTS_JSON))
        repository.syncSubjects(force = true)

        repository.observeSubjectDetail(440).test {
            val detail = awaitItem()
            assertThat(detail?.meaningMnemonic).isEqualTo("Looks like <radical>water</radical> flowing.")
        }
    }

    @Test
    fun `syncSubjects maps context sentences and visually similar ids into the cached subject`() = runTest {
        server.enqueue(jsonResponse(SUBJECTS_JSON))
        repository.syncSubjects(force = true)

        repository.observeSubjectDetail(440).test {
            val detail = awaitItem()
            assertThat(detail?.contextSentences).hasSize(1)
            assertThat(detail?.contextSentences?.first()?.japanese).isEqualTo("水を飲みます。")
            assertThat(detail?.contextSentences?.first()?.english).isEqualTo("I drink water.")
            assertThat(detail?.visuallySimilarSubjectIds).containsExactly(441L)
        }
    }

    @Test
    fun `syncSubjects maps pronunciation audios into the cached subject`() = runTest {
        server.enqueue(jsonResponse(SUBJECTS_JSON))
        repository.syncSubjects(force = true)

        repository.observeSubjectDetail(440).test {
            val detail = awaitItem()
            assertThat(detail?.pronunciationAudios).hasSize(1)
            val audio = detail?.pronunciationAudios?.first()
            assertThat(audio?.url).isEqualTo("https://api.wanikani.com/audio/mizu.mp3")
            assertThat(audio?.contentType).isEqualTo("audio/mpeg")
            assertThat(audio?.pronunciation).isEqualTo("みず")
            assertThat(audio?.gender).isEqualTo("female")
        }
    }

    @Test
    fun `syncSubjects groups kanji readings into onyomi and kunyomi`() = runTest {
        server.enqueue(jsonResponse(SUBJECTS_JSON))
        repository.syncSubjects(force = true)

        repository.observeSubjectDetail(440).test {
            val detail = awaitItem()
            assertThat(detail?.onyomiReadings).containsExactly("スイ")
            assertThat(detail?.kunyomiReadings).containsExactly("みず")
            assertThat(detail?.nanoriReadings).isEmpty()
            assertThat(detail?.readings).containsExactly("スイ", "みず")
        }
    }

    @Test
    fun `syncSubjects captures every kunyomi reading for a kanji with many, not just the primary one`() = runTest {
        // Mirrors real data for 生, a kanji WaniKani lists with several onyomi and many kunyomi.
        server.enqueue(jsonResponse(MANY_KUNYOMI_SUBJECT_JSON))
        repository.syncSubjects(force = true)

        repository.observeSubjectDetail(550).test {
            val detail = awaitItem()
            assertThat(detail?.onyomiReadings).containsExactly("セイ", "ショウ")
            assertThat(detail?.kunyomiReadings).containsExactly("い", "う", "は", "き", "なま")
            assertThat(detail?.nanoriReadings).containsExactly("ふ")
        }
    }

    @Test
    fun `observeSubjectSummaries resolves a batch of subject ids into tiles`() = runTest {
        server.enqueue(jsonResponse(SUBJECTS_JSON))
        repository.syncSubjects(force = true)

        repository.observeSubjectSummaries(listOf(440)).test {
            val summaries = awaitItem()
            assertThat(summaries).hasSize(1)
            assertThat(summaries.first().meanings).containsExactly("Water")
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
                    "meanings": [{"meaning": "Water", "primary": true, "accepted_answer": true}],
                    "readings": [
                        {"reading": "スイ", "primary": true, "accepted_answer": true, "type": "onyomi"},
                        {"reading": "みず", "primary": true, "accepted_answer": true, "type": "kunyomi"}
                    ],
                    "meaning_mnemonic": "Looks like <radical>water</radical> flowing.",
                    "visually_similar_subject_ids": [441],
                    "context_sentences": [{"en": "I drink water.", "ja": "水を飲みます。"}],
                    "pronunciation_audios": [
                        {
                          "url": "https://api.wanikani.com/audio/mizu.mp3",
                          "content_type": "audio/mpeg",
                          "metadata": {"gender": "female", "pronunciation": "みず"}
                        }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val MANY_KUNYOMI_SUBJECT_JSON = """
            {
              "object": "collection",
              "url": "https://api.wanikani.com/v2/subjects",
              "total_count": 1,
              "data": [
                {
                  "id": 550,
                  "object": "kanji",
                  "url": "https://api.wanikani.com/v2/subjects/550",
                  "data_updated_at": "2026-01-01T00:00:00.000000Z",
                  "data": {
                    "created_at": "2020-01-01T00:00:00.000000Z",
                    "level": 1,
                    "slug": "生",
                    "characters": "生",
                    "meanings": [{"meaning": "Life", "primary": true, "accepted_answer": true}],
                    "readings": [
                        {"reading": "セイ", "primary": true, "accepted_answer": true, "type": "onyomi"},
                        {"reading": "ショウ", "primary": false, "accepted_answer": false, "type": "onyomi"},
                        {"reading": "い", "primary": false, "accepted_answer": false, "type": "kunyomi"},
                        {"reading": "う", "primary": false, "accepted_answer": false, "type": "kunyomi"},
                        {"reading": "は", "primary": false, "accepted_answer": false, "type": "kunyomi"},
                        {"reading": "き", "primary": false, "accepted_answer": false, "type": "kunyomi"},
                        {"reading": "なま", "primary": false, "accepted_answer": false, "type": "kunyomi"},
                        {"reading": "ふ", "primary": false, "accepted_answer": false, "type": "nanori"}
                    ],
                    "meaning_mnemonic": "A single stroke sprouting from the ground."
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
