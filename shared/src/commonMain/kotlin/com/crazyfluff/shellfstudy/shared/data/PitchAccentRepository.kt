package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.database.pitchaccent.PitchAccentCacheDao
import com.crazyfluff.shellfstudy.shared.database.pitchaccent.PitchAccentCacheEntity
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentUiState
import com.crazyfluff.shellfstudy.shared.network.weblio.WeblioApi
import com.crazyfluff.shellfstudy.shared.network.weblio.WeblioEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Orchestrates the two pitch-accent sources: the bundled dictionary (always available, static) and
 * the live weblio.jp scrape cache (fills gaps/keeps entries current, populated by the Android
 * PitchAccentScrapeWorker). A cached scrape only wins over the bundled dictionary once it has
 * actually *succeeded* — see [observePitchAccents].
 */
class PitchAccentRepository(
    private val bundledSource: PitchAccentBundledSource,
    private val cacheDao: PitchAccentCacheDao,
    private val weblioApi: WeblioApi,
    private val parser: WeblioPitchAccentParser
) {
    /**
     * Classifies what's known about [characters] into the three renderable states — see
     * [PitchAccentUiState]. A cache row alone proves nothing: it exists for a failed attempt too
     * ([PitchAccentCacheEntity.fetchedAt] null) and can hold an empty list from a scrape that
     * succeeded without finding an entry. Only that second case is a *confirmed* absence; the first
     * is still pending, so it must not shadow the bundled dictionary — hence the bundled lookup on
     * every branch except a cached-with-data one.
     */
    fun observePitchAccents(characters: String): Flow<PitchAccentUiState> =
        cacheDao.observeByCharacters(characters).map { cached ->
            val bundled = bundledSource.get(characters)
            when {
                cached?.fetchedAt == null ->
                    bundled.takeIf { it.isNotEmpty() }?.let(PitchAccentUiState::Available)
                        ?: PitchAccentUiState.Loading
                cached.pitchAccents.isNotEmpty() -> PitchAccentUiState.Available(cached.pitchAccents)
                bundled.isNotEmpty() -> PitchAccentUiState.Available(bundled)
                else -> PitchAccentUiState.Unavailable
            }
        }

    /**
     * Fetches and caches weblio's pitch data for [characters]. The attempt is recorded either way —
     * a failure writes the same row with a null [PitchAccentCacheEntity.fetchedAt], which
     * [observePitchAccents] reads as still-unknown, so the background worker retries it later.
     *
     * A weblio 404 ([WeblioEntry.NotFound]) is not a failure: it is weblio confirming it has no entry,
     * so it is cached with a non-null [PitchAccentCacheEntity.fetchedAt] and no patterns — the same
     * shape as a scrape that succeeded but found nothing — and [observePitchAccents] reads it as a
     * confirmed absence instead of retrying forever. The worker's back-off is untouched because the
     * row shape is identical.
     *
     * Returns true when a definitive result was obtained and cached (a parsed page *or* a 404), false
     * when the fetch or parse failed and the word is still unresolved. Failing silently would be
     * indistinguishable from "never tried" to the user — the UI shows "not checked yet" for both — so
     * the return value exists purely for a caller that wants to say the fetch failed; callers that only
     * maintain the cache (the scrape worker) ignore it.
     */
    suspend fun scrapeAndCache(characters: String, now: Long): Boolean {
        val scraped = runCatching {
            when (val entry = weblioApi.getEntry(characters)) {
                WeblioEntry.NotFound -> emptyList() // definitive: weblio has no entry
                is WeblioEntry.Page -> parser.parse(entry.html)
            }
        }.getOrNull()
        cacheDao.upsert(
            PitchAccentCacheEntity(
                characters = characters,
                pitchAccents = scraped.orEmpty(),
                fetchedAt = if (scraped != null) now else null,
                lastAttemptedAt = now
            )
        )
        return scraped != null
    }
}
