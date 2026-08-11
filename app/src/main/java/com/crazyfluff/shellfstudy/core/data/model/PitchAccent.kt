package com.crazyfluff.shellfstudy.core.data.model

import com.crazyfluff.shellfstudy.core.data.toKatakana
import kotlinx.serialization.Serializable

/**
 * One pitch-accent entry for a vocabulary word. [reading] is katakana (matches the bundled
 * dictionary/weblio scrape convention) and is null for wildcard entries that apply regardless of
 * which reading matched — e.g. the bundled data has `"ふじ山": [[null, null, 1]]`.
 */
@Serializable
data class PitchAccent(val reading: String?, val partOfSpeech: String?, val pitchNumber: Int)

/**
 * Resolves the best [PitchAccent] for a specific (hiragana or katakana) reading: an exact
 * katakana-normalized match first, falling back to a wildcard (null-reading) entry if present.
 */
fun List<PitchAccent>.forReading(reading: String): PitchAccent? {
    val katakana = reading.toKatakana()
    return firstOrNull { it.reading == katakana } ?: firstOrNull { it.reading == null }
}
