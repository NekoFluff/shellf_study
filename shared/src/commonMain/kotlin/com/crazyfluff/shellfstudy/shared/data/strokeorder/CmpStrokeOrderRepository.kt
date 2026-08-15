package com.crazyfluff.shellfstudy.shared.data.strokeorder

import com.crazyfluff.shellfstudy.shared.data.StrokeOrderRepository
import com.crazyfluff.shellfstudy.shared.data.model.StrokeOrderStroke
import com.crazyfluff.shellfstudy.shared.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * CMP resource-backed implementation of [StrokeOrderRepository] — reads stroke_data.json from
 * shared composeResources instead of Android's res/raw, making it available on all targets.
 * Same Mutex-guarded lazy-load pattern as the former AndroidStrokeOrderRepository.
 */
class CmpStrokeOrderRepository : StrokeOrderRepository {

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
            cache ?: withContext(Dispatchers.Default) {
                val bytes = Res.readBytes("files/stroke_data.json")
                Json.decodeFromString<Map<String, List<StrokeOrderStroke>>>(bytes.decodeToString())
            }.also { cache = it }
        }
    }
}
