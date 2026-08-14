package com.crazyfluff.shellfstudy.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.core.data.PitchAccentRepository
import com.crazyfluff.shellfstudy.core.data.WeblioPitchAccentParser
import com.crazyfluff.shellfstudy.shared.database.SubjectEntity
import com.crazyfluff.shellfstudy.shared.database.pitchaccent.PitchAccentCacheEntity
import com.crazyfluff.shellfstudy.shared.network.MeaningData
import com.crazyfluff.shellfstudy.shared.network.ReadingData
import com.crazyfluff.shellfstudy.fakes.FakePitchAccentBundledSource
import com.crazyfluff.shellfstudy.fakes.FakePitchAccentCacheDao
import com.crazyfluff.shellfstudy.fakes.FakeSubjectDao
import com.crazyfluff.shellfstudy.fakes.FakeWeblioApi
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PitchAccentScrapeWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun buildWorker(
        subjectDao: FakeSubjectDao,
        cacheDao: FakePitchAccentCacheDao,
        repository: PitchAccentRepository
    ): PitchAccentScrapeWorker =
        TestListenableWorkerBuilder<PitchAccentScrapeWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = PitchAccentScrapeWorker(appContext, workerParameters, subjectDao, cacheDao, repository)
            })
            .build()

    @Test
    fun `doWork scrapes every unlocked vocab word missing a cache entry, skipping non-vocab subjects`() = runTest {
        val subjectDao = FakeSubjectDao()
        subjectDao.upsertAll(
            listOf(
                vocab(id = 1, characters = "水"),
                vocab(id = 2, characters = "火"),
                subjectEntityOfType(id = 3, characters = "木", type = "kanji")
            )
        )
        subjectDao.markUnlocked(1, 2)
        val cacheDao = FakePitchAccentCacheDao()
        val html = """<div class="NetDicHead">ミズ<span style="font-size:75%;">［0］</span></div>"""
        val repository = PitchAccentRepository(
            FakePitchAccentBundledSource(), cacheDao, FakeWeblioApi(mapOf("水" to html, "火" to html)), WeblioPitchAccentParser()
        )

        val result = buildWorker(subjectDao, cacheDao, repository).doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(cacheDao.getFreshCharacters(0L)).containsExactly("水", "火")
    }

    @Test
    fun `doWork does not re-scrape a word that already has a fresh cache entry`() = runTest {
        val subjectDao = FakeSubjectDao()
        subjectDao.upsertAll(listOf(vocab(id = 1, characters = "水")))
        subjectDao.markUnlocked(1)
        val cacheDao = FakePitchAccentCacheDao()
        cacheDao.upsert(
            PitchAccentCacheEntity(characters = "水", pitchAccents = emptyList(), fetchedAt = 1L, lastAttemptedAt = System.currentTimeMillis())
        )
        // No configured weblio response for "水" — if the worker re-scraped it, the fetch would fail
        // and overwrite fetchedAt with null, so an unchanged fetchedAt proves it was skipped.
        val repository = PitchAccentRepository(FakePitchAccentBundledSource(), cacheDao, FakeWeblioApi(), WeblioPitchAccentParser())

        buildWorker(subjectDao, cacheDao, repository).doWork()

        cacheDao.observeByCharacters("水").test {
            assertThat(awaitItem()?.fetchedAt).isEqualTo(1L)
        }
    }

    @Test
    fun `doWork skips vocab that hasn't been unlocked, even with no cache entry`() = runTest {
        val subjectDao = FakeSubjectDao()
        subjectDao.upsertAll(listOf(vocab(id = 1, characters = "水")))
        // Deliberately not marked unlocked.
        val cacheDao = FakePitchAccentCacheDao()
        // No configured weblio response for "水" — if the worker scraped it, the fetch would fail
        // and write a cache entry with fetchedAt = null, so an absent entry proves it was skipped.
        val repository = PitchAccentRepository(FakePitchAccentBundledSource(), cacheDao, FakeWeblioApi(), WeblioPitchAccentParser())

        val result = buildWorker(subjectDao, cacheDao, repository).doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        cacheDao.observeByCharacters("水").test {
            assertThat(awaitItem()).isNull()
        }
    }

    private fun vocab(id: Long, characters: String): SubjectEntity = subjectEntityOfType(id, characters, "vocabulary")

    private fun subjectEntityOfType(id: Long, characters: String, type: String): SubjectEntity = SubjectEntity(
        id = id,
        subjectType = type,
        level = 1,
        slug = characters,
        characters = characters,
        meanings = listOf(MeaningData(meaning = characters, primary = true)),
        readings = listOf(ReadingData(reading = characters, primary = true)),
        documentUrl = null,
        searchTarget = characters
    )
}
