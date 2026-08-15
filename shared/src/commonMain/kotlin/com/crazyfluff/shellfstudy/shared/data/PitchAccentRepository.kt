package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.database.pitchaccent.PitchAccentCacheDao
import com.crazyfluff.shellfstudy.shared.database.pitchaccent.PitchAccentCacheEntity
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.network.weblio.WeblioApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

/**
 * Orchestrates the two pitch-accent sources: the bundled dictionary (always available, static) and
 * the live weblio.jp scrape cache (fills gaps/keeps entries current, populated by the Android
 * PitchAccentScrapeWorker). A cached scrape — even an empty one — takes priority once it exists.
 */
class PitchAccentRepository(
    private val bundledSource: PitchAccentBundledSource,
    private val cacheDao: PitchAccentCacheDao,
    private val weblioApi: WeblioApi,
    private val parser: WeblioPitchAccentParser
) : PitchAccentProvider {
    override fun observePitchAccents(characters: String): Flow<List<PitchAccent>> =
        cacheDao.observeByCharacters(characters).transform { cached ->
            emit(cached?.pitchAccents ?: bundledSource.get(characters))
        }

    /** Fetches and caches weblio's pitch data for [characters]. Fails silently. */
    suspend fun scrapeAndCache(characters: String, now: Long) {
        val scraped = runCatching { parser.parse(weblioApi.getEntry(characters)) }.getOrNull()
        cacheDao.upsert(
            PitchAccentCacheEntity(
                characters = characters,
                pitchAccents = scraped.orEmpty(),
                fetchedAt = if (scraped != null) now else null,
                lastAttemptedAt = now
            )
        )
    }
}
