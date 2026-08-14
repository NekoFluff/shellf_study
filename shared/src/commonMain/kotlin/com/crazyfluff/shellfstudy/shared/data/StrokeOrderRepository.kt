package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.StrokeOrderStroke

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
