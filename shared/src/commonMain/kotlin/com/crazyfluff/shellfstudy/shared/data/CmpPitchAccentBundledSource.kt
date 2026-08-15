package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.generated.resources.Res
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * CMP resource-backed implementation of [PitchAccentBundledSource] — reads pitch_info.json from
 * shared composeResources instead of Android's res/raw. Same heterogeneous-tuple JSON parsing
 * as the former AndroidPitchAccentBundledSource, but now available on all targets.
 */
class CmpPitchAccentBundledSource : PitchAccentBundledSource {

    private val mutex = Mutex()
    private var cache: Map<String, List<PitchAccent>>? = null

    override suspend fun get(characters: String): List<PitchAccent> =
        loadAll()[characters].orEmpty()

    private suspend fun loadAll(): Map<String, List<PitchAccent>> {
        cache?.let { return it }
        return mutex.withLock {
            cache ?: run {
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
