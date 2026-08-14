package com.crazyfluff.shellfstudy.core.data

import android.content.Context
import com.crazyfluff.shellfstudy.R
import com.crazyfluff.shellfstudy.shared.data.PitchAccentBundledSource
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads the bundled pitch-accent dictionary (res/raw/pitch_info.json, compiled offline by the
 * Smouldering Durtles project from a weblio.jp scrape + a third-party userscript dataset) into an
 * in-memory lookup. Each JSON value is a list of heterogeneous 3-element tuples
 * `[reading|null, partOfSpeech|null, pitchNumber]`, so this is parsed via the raw [JsonElement] API
 * rather than a typed list — there's no clean data-class shape for a tuple. A separate interface
 * (rather than a concrete class) so unit tests can supply a no-resource-needed fake instead of
 * exercising `Resources.openRawResource`, which needs a real Android [Context] to run. The
 * [PitchAccentBundledSource] interface itself lives in :shared; only this Context-based
 * implementation stays Android-only until an NSBundle-backed iOS actual is written.
 */
class AndroidPitchAccentBundledSource(
    private val context: Context
) : PitchAccentBundledSource {
    private val entries: Map<String, List<PitchAccent>> by lazy { loadEntries() }

    override fun get(characters: String): List<PitchAccent> = entries[characters].orEmpty()

    private fun loadEntries(): Map<String, List<PitchAccent>> {
        val json = context.resources.openRawResource(R.raw.pitch_info).use { it.readBytes() }
            .toString(Charsets.UTF_8)
        val root = Json.parseToJsonElement(json).jsonObject
        return root.mapValues { (_, value) -> value.jsonArray.map { it.jsonArray.toPitchAccent() } }
    }

    private fun JsonArray.toPitchAccent(): PitchAccent {
        val reading = this[0].takeUnless { it is JsonNull }?.jsonPrimitive?.content
        val partOfSpeech = this[1].takeUnless { it is JsonNull }?.jsonPrimitive?.content
        val pitchNumber = this[2].jsonPrimitive.int
        return PitchAccent(reading = reading, partOfSpeech = partOfSpeech, pitchNumber = pitchNumber)
    }
}
