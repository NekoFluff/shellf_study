package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.core.data.strokeorder.StrokeOrderRepository

/** In-memory stand-in for [StrokeOrderRepository] — real asset reading needs Robolectric/Context. */
class FakeStrokeOrderRepository(
    private val strokesByCharacter: Map<Char, List<String>> = emptyMap()
) : StrokeOrderRepository {
    override suspend fun getStrokeOrder(character: Char): List<String>? = strokesByCharacter[character]
}
