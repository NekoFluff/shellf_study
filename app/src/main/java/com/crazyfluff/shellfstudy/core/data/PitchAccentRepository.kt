package com.crazyfluff.shellfstudy.core.data

import com.crazyfluff.shellfstudy.shared.database.pitchaccent.PitchAccentCacheDao
import com.crazyfluff.shellfstudy.shared.database.pitchaccent.PitchAccentCacheEntity
import com.crazyfluff.shellfstudy.shared.data.PitchAccentBundledSource
import com.crazyfluff.shellfstudy.shared.data.PitchAccentProvider
import com.crazyfluff.shellfstudy.shared.data.WeblioPitchAccentParser
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.network.weblio.WeblioApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Orchestrates the two pitch-accent sources: the bundled dictionary (always available, static)
 * and the live weblio.jp scrape cache (fills gaps/keeps entries current, populated by
 * [com.crazyfluff.shellfstudy.core.sync.PitchAccentScrapeWorker]). A cached scrape — even an empty
 * one, meaning "weblio has no entry for this word" — takes priority over the bundled data once it
 * exists, since it's more likely to be current.
 *
 * Implements [PitchAccentProvider] (the small interface [com.crazyfluff.shellfstudy.shared.data.SubjectRepository]
 * actually needs) since this class itself isn't portable yet — [bundledSource] loads an Android
 * asset via a real Context. [parser] itself (unlike before) is portable, now that it's built on
 * Ksoup rather than jsoup.
 */
class PitchAccentRepository(
    private val bundledSource: PitchAccentBundledSource,
    private val cacheDao: PitchAccentCacheDao,
    private val weblioApi: WeblioApi,
    private val parser: WeblioPitchAccentParser
) : PitchAccentProvider {
    override fun observePitchAccents(characters: String): Flow<List<PitchAccent>> =
        cacheDao.observeByCharacters(characters).map { cached ->
            cached?.pitchAccents ?: bundledSource.get(characters)
        }

    /** Fetches and caches weblio's pitch data for [characters]. Fails silently — a bad scrape just leaves the bundled fallback in place. */
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
