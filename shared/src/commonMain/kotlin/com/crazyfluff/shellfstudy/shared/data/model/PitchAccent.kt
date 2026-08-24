package com.crazyfluff.shellfstudy.shared.data.model

import com.crazyfluff.shellfstudy.shared.data.toKatakana
import kotlinx.serialization.Serializable

/**
 * One pitch-accent entry for a vocabulary word. [reading] is katakana (matches the bundled
 * dictionary/weblio scrape convention) and is null for wildcard entries that apply regardless of
 * which reading matched — e.g. the bundled data has `"ふじ山": [[null, null, 1]]`.
 */
@Serializable
data class PitchAccent(val reading: String?, val partOfSpeech: String?, val pitchNumber: Int)

/**
 * Resolves every [PitchAccent] for a specific (hiragana or katakana) reading: all entries with an
 * exact katakana-normalized match, or every wildcard (null-reading) entry if none match. A single
 * reading can legitimately carry more than one pitch pattern — e.g. 一層(いっそう) is heiban as an
 * adverb but nakadaka as a noun — so callers must not assume a single result.
 */
fun List<PitchAccent>.allForReading(reading: String): List<PitchAccent> {
    val katakana = reading.toKatakana()
    val exact = filter { it.reading == katakana }
    return exact.ifEmpty { filter { it.reading == null } }.distinct()
}
