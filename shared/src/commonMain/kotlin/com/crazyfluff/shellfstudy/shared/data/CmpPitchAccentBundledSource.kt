package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * CMP resource-backed implementation of [PitchAccentBundledSource] — reads pitch_info.json from
 * shared composeResources, so it works on both Android and iOS. The file stores each entry as a
 * heterogeneous tuple rather than an object, hence the manual JsonArray parsing below.
 */
class CmpPitchAccentBundledSource : PitchAccentBundledSource {

    private val mutex = Mutex()
    private var cache: Map<String, List<PitchAccent>>? = null

    override suspend fun get(characters: String): List<PitchAccent> =
        loadAll()[characters].orEmpty()

    override suspend fun preload() {
        loadAll()
    }

    private suspend fun loadAll(): Map<String, List<PitchAccent>> {
        cache?.let { return it }
        return mutex.withLock {
            cache ?: withContext(Dispatchers.Default) {
                val bytes = Res.readBytes("files/pitch_info.json")
                val root = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
                root.mapValues { (_, value) -> value.jsonArray.map { it.jsonArray.toPitchAccent() } }
            }.also { cache = it }
        }
    }

    private fun JsonArray.toPitchAccent(): PitchAccent {
        val reading = this[0].takeUnless { it is JsonNull }?.jsonPrimitive?.content
        val partOfSpeech = this[1].takeUnless { it is JsonNull }?.jsonPrimitive?.content
        val pitchNumber = this[2].jsonPrimitive.int
        return PitchAccent(reading = reading, partOfSpeech = partOfSpeech, pitchNumber = pitchNumber)
    }
}
