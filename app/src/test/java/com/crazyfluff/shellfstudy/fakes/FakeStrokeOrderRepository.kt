package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.shared.data.model.StrokeOrderStroke
import com.crazyfluff.shellfstudy.shared.data.StrokeOrderRepository

/** In-memory stand-in for [StrokeOrderRepository] — real asset reading needs Robolectric/Context. */
class FakeStrokeOrderRepository(
    private val strokesByCharacter: Map<Char, List<StrokeOrderStroke>> = emptyMap()
) : StrokeOrderRepository {
    /** How many lookups ran — each is a parse-backed map read on a real device. */
    var lookups = 0
        private set

    override suspend fun getStrokeOrder(character: Char): List<StrokeOrderStroke>? {
        lookups++
        return strokesByCharacter[character]
    }

    override suspend fun preload() = Unit
}
