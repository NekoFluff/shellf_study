package com.crazyfluff.shellfstudy.core.audio

import com.crazyfluff.shellfstudy.core.data.model.PronunciationAudio

/**
 * Picks the best pronunciation clip for [reading] out of [audios]. Prefers clips whose metadata
 * names this exact reading, falling back to the full list when none do (some clips omit
 * `pronunciation` metadata entirely). [preference] then narrows by voice gender; `ALTERNATE` has no
 * single-call meaning (it needs state a caller would own across plays), so it just falls back to
 * `first()` for now.
 */
fun selectAudioFor(
    audios: List<PronunciationAudio>,
    reading: String,
    preference: VoicePreference? = null
): PronunciationAudio? {
    val matching = audios.filter { it.pronunciation == null || it.pronunciation == reading }
        .ifEmpty { audios }
    if (matching.isEmpty()) return null
    return when (preference) {
        VoicePreference.MALE -> matching.firstOrNull { it.gender == "male" } ?: matching.first()
        VoicePreference.FEMALE -> matching.firstOrNull { it.gender == "female" } ?: matching.first()
        VoicePreference.RANDOM -> matching.random()
        VoicePreference.ALTERNATE, null -> matching.first()
    }
}
