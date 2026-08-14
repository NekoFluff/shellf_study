package com.crazyfluff.shellfstudy.core.data

import app.cash.turbine.test
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.fakes.FakePitchAccentBundledSource
import com.crazyfluff.shellfstudy.fakes.FakePitchAccentCacheDao
import com.crazyfluff.shellfstudy.fakes.FakeWeblioApi
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PitchAccentRepositoryTest {

    private val bundled = FakePitchAccentBundledSource(
        mapOf("水" to listOf(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0)))
    )
    private val cacheDao = FakePitchAccentCacheDao()

    @Test
    fun `observePitchAccents falls back to the bundled dictionary when nothing is cached yet`() = runTest {
        val repository = PitchAccentRepository(bundled, cacheDao, FakeWeblioApi(), WeblioPitchAccentParser())

        repository.observePitchAccents("水").test {
            assertThat(awaitItem()).containsExactly(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0))
        }
    }

    @Test
    fun `scrapeAndCache stores a successful scrape and observePitchAccents prefers it over the bundled entry`() = runTest {
        val html = """<div class="NetDicHead">オミヤゲ<span style="font-size:75%;">［0］</span></div>"""
        val weblioApi = FakeWeblioApi(mapOf("お土産" to html))
        val repository = PitchAccentRepository(FakePitchAccentBundledSource(), cacheDao, weblioApi, WeblioPitchAccentParser())

        repository.scrapeAndCache("お土産", now = 1_000L)

        repository.observePitchAccents("お土産").test {
            assertThat(awaitItem()).containsExactly(PitchAccent(reading = "オミヤゲ", partOfSpeech = null, pitchNumber = 0))
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
