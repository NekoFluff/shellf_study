package com.crazyfluff.shellfstudy.core.data.strokeorder

import android.content.Context
import com.crazyfluff.shellfstudy.R
import com.crazyfluff.shellfstudy.core.data.model.StrokeOrderStroke
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Looks up a character's stroke-order data: an ordered list of [StrokeOrderStroke], one per
 * stroke. Returns null if the character has none (e.g. most WaniKani radicals, which have no
 * real Unicode glyph, or any character outside KanjiVG's set).
 */
interface StrokeOrderRepository {
    suspend fun getStrokeOrder(character: Char): List<StrokeOrderStroke>?

    /** Parses and caches the bundled dictionary ahead of the first real lookup, so opening a
     *  subject detail sheet doesn't pay for it inline. Safe to call repeatedly — subsequent calls
     *  are no-ops once cached. */
    suspend fun preload()
}

/**
 * Reads the bundled stroke-order dictionary (res/raw/stroke_data.json, compiled offline by
 * tools/kanjivg/generate_stroke_data.py from the KanjiVG project — see
 * tools/kanjivg/KANJIVG_LICENSE.txt for its CC BY-SA attribution) — the same res/raw + Context
 * pattern this app already uses for its bundled pitch-accent dictionary
 * ([com.crazyfluff.shellfstudy.core.data.AndroidPitchAccentBundledSource]). No Room table: the
 * data is immutable and ships with the app, so there's nothing to sync or invalidate.
 *
 * Unlike that pitch-accent source, this one parses lazily behind a suspend function with an
 * explicit [Dispatchers.IO] hop rather than a synchronous `by lazy`: pitch-accent lookups ride
 * along on a Room query Flow that's already off the main thread, but stroke-order lookups are
 * driven directly from a ViewModel's `viewModelScope` (main thread by default), and this JSON is
 * an order of magnitude larger, so parsing it inline would risk visible jank on first use.
 *
 * Interface + impl split (rather than a plain class like SubjectRepository) mirrors TokenCipher
 * and PitchAccentBundledSource, since the real implementation needs a real Android [Context] to
 * read resources, which isn't available on the plain host JVM used by ViewModel/repository unit
 * tests — those inject a hand-written fake instead.
 */
@Singleton
class AndroidStrokeOrderRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : StrokeOrderRepository {

    private val mutex = Mutex()
    private var cache: Map<String, List<StrokeOrderStroke>>? = null

    override suspend fun getStrokeOrder(character: Char): List<StrokeOrderStroke>? =
        loadAll()[character.toString()]

    override suspend fun preload() {
        loadAll()
    }

    private suspend fun loadAll(): Map<String, List<StrokeOrderStroke>> {
        cache?.let { return it }
        return mutex.withLock {
            cache ?: withContext(Dispatchers.IO) {
                context.resources.openRawResource(R.raw.stroke_data).use { it.readBytes() }
                    .toString(Charsets.UTF_8)
                    .let { Json.decodeFromString<Map<String, List<StrokeOrderStroke>>>(it) }
            }.also { cache = it }
        }
    }
}
