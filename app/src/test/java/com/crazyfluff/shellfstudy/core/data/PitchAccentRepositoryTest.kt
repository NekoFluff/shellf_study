package com.crazyfluff.shellfstudy.core.data

import app.cash.turbine.test
import com.crazyfluff.shellfstudy.fakes.FakePitchAccentBundledSource
import com.crazyfluff.shellfstudy.fakes.FakePitchAccentCacheDao
import com.crazyfluff.shellfstudy.fakes.FakeWeblioApi
import com.crazyfluff.shellfstudy.shared.data.PitchAccentRepository
import com.crazyfluff.shellfstudy.shared.data.WeblioPitchAccentParser
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.database.pitchaccent.PitchAccentCacheEntity
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentUiState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

private val MIZU = PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0)
private val SUI = PitchAccent(reading = "スイ", partOfSpeech = null, pitchNumber = 1)

class PitchAccentRepositoryTest {

    private val bundled = FakePitchAccentBundledSource(mapOf("水" to listOf(MIZU)))
    private val cacheDao = FakePitchAccentCacheDao()

    private fun repository(bundledSource: FakePitchAccentBundledSource = bundled) =
        PitchAccentRepository(bundledSource, cacheDao, FakeWeblioApi(), WeblioPitchAccentParser())

    /** Mirrors what [PitchAccentRepository.scrapeAndCache] writes: [fetchedAt] is null for an attempt
     *  that errored, and a non-null [fetchedAt] with no entries is a scrape that found nothing. */
    private suspend fun seedCache(characters: String, pitchAccents: List<PitchAccent> = emptyList(), fetchedAt: Long? = null) {
        cacheDao.upsert(
            PitchAccentCacheEntity(
                characters = characters,
                pitchAccents = pitchAccents,
                fetchedAt = fetchedAt,
                lastAttemptedAt = fetchedAt ?: 1_000L
            )
        )
    }

    private suspend fun PitchAccentRepository.stateFor(characters: String = "水"): PitchAccentUiState =
        observePitchAccents(characters).first()

    // --- No cache row yet: a never-scraped word (newly unlocked, worker hasn't run on a 7-day cadence). ---

    @Test
    fun `no cache row reports Loading when the bundled dictionary has nothing either`() = runTest {
        assertThat(repository(FakePitchAccentBundledSource()).stateFor()).isEqualTo(PitchAccentUiState.Loading)
    }

    @Test
    fun `no cache row reports the bundled entries as Available`() = runTest {
        assertThat(repository().stateFor()).isEqualTo(PitchAccentUiState.Available(listOf(MIZU)))
    }

    // --- Failed attempt (fetchedAt null): still unresolved, so combined with the bundled dictionary. ---

    @Test
    fun `a failed attempt with nothing bundled stays Loading rather than reading as confirmed absent`() = runTest {
        seedCache("水", fetchedAt = null)

        assertThat(repository(FakePitchAccentBundledSource()).stateFor()).isEqualTo(PitchAccentUiState.Loading)
    }

    @Test
    fun `a failed attempt does not shadow the bundled dictionary`() = runTest {
        seedCache("水", fetchedAt = null)

        assertThat(repository().stateFor()).isEqualTo(PitchAccentUiState.Available(listOf(MIZU)))
    }

    // --- Successful but empty scrape: a confirmed absence, unless the bundled dictionary covers it. ---

    @Test
    fun `a successful scrape that found nothing reports Unavailable when nothing is bundled`() = runTest {
        seedCache("水", fetchedAt = 2_000L)

        assertThat(repository(FakePitchAccentBundledSource()).stateFor())
            .isEqualTo(PitchAccentUiState.Unavailable)
    }

    @Test
    fun `an empty-but-successful scrape does not shadow the bundled dictionary`() = runTest {
        // Regression: the cache used to win whenever a row existed at all, so an empty successful
        // scrape permanently hid a bundled entry that should have been filling the gap.
        seedCache("水", fetchedAt = 2_000L)

        assertThat(repository().stateFor()).isEqualTo(PitchAccentUiState.Available(listOf(MIZU)))
    }

    // --- Scraped data present: it wins, whatever the bundled dictionary holds. ---

    @Test
    fun `cached entries are Available and win over the bundled dictionary`() = runTest {
        seedCache("水", pitchAccents = listOf(SUI), fetchedAt = 2_000L)

        assertThat(repository().stateFor()).isEqualTo(PitchAccentUiState.Available(listOf(SUI)))
    }

    @Test
    fun `cached entries are Available even when the bundled dictionary also covers the word`() = runTest {
        seedCache("水", pitchAccents = listOf(SUI, MIZU), fetchedAt = 2_000L)

        assertThat(repository().stateFor()).isEqualTo(PitchAccentUiState.Available(listOf(SUI, MIZU)))
    }

    // --- Integration with the real scrape/write path. ---

    @Test
    fun `scrapeAndCache stores a successful scrape and observePitchAccents prefers it over the bundled entry`() = runTest {
        val html = """<div class="NetDicHead">オミヤゲ<span style="font-size:75%;">［0］</span></div>"""
        val weblioApi = FakeWeblioApi(mapOf("お土産" to html))
        val repository = PitchAccentRepository(FakePitchAccentBundledSource(), cacheDao, weblioApi, WeblioPitchAccentParser())

        repository.scrapeAndCache("お土産", now = 1_000L)

        assertThat(repository.observePitchAccents("お土産").first())
            .isEqualTo(PitchAccentUiState.Available(listOf(PitchAccent(reading = "オミヤゲ", partOfSpeech = null, pitchNumber = 0))))
    }

    @Test
    fun `a scrape landing on an already-observed word flips Loading to Available live`() = runTest {
        val html = """<div class="NetDicHead">オミヤゲ<span style="font-size:75%;">［0］</span></div>"""
        val repository = PitchAccentRepository(
            FakePitchAccentBundledSource(), cacheDao, FakeWeblioApi(mapOf("お土産" to html)), WeblioPitchAccentParser()
        )

        repository.observePitchAccents("お土産").test {
            assertThat(awaitItem()).isEqualTo(PitchAccentUiState.Loading)
            repository.scrapeAndCache("お土産", now = 1_000L)
            assertThat(awaitItem())
                .isEqualTo(PitchAccentUiState.Available(listOf(PitchAccent(reading = "オミヤゲ", partOfSpeech = null, pitchNumber = 0))))
        }
    }

    @Test
    fun `scrapeAndCache on a failed fetch records the attempt without clobbering with fake data`() = runTest {
        val repository = PitchAccentRepository(bundled, cacheDao, FakeWeblioApi(), WeblioPitchAccentParser())

        repository.scrapeAndCache("水", now = 2_000L)

        val cached = cacheDao.observeByCharacters("水")
        cached.test {
            val entity = awaitItem()
            assertThat(entity?.fetchedAt).isNull()
            assertThat(entity?.lastAttemptedAt).isEqualTo(2_000L)
            assertThat(entity?.pitchAccents).isEmpty()
        }
    }
}
