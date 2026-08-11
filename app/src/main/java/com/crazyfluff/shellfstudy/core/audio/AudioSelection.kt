package com.crazyfluff.shellfstudy.core.audio

import com.crazyfluff.shellfstudy.core.data.model.PronunciationAudio

/**
 * Picks the best pronunciation clip for [reading] out of [audios]. Prefers clips whose metadata
 * names this exact reading, falling back to the full list when none do (some clips omit
 * `pronunciation` metadata entirely). [preference] then narrows by voice gender; `ALTERNATE` has no
 * single-call meaning (it needs state a caller would own across plays), so it just falls back to
 * `first()` for now.
 *
 * When [mp3Only] is set, clips are first narrowed to `audio/mpeg` — WaniKani publishes an MP3
 * alongside every Ogg clip for the same recording, so this never drops coverage, it just guarantees
 * playability on devices (e.g. e-ink readers) that can't decode Ogg.
 */
fun selectAudioFor(
    audios: List<PronunciationAudio>,
    reading: String,
    preference: VoicePreference? = null,
    mp3Only: Boolean = false
): PronunciationAudio? {
    val eligible = if (mp3Only) audios.filter { it.contentType == "audio/mpeg" } else audios
    if (eligible.isEmpty()) return null
    val matching = eligible.filter { it.pronunciation == null || it.pronunciation == reading }
        .ifEmpty { eligible }
    if (matching.isEmpty()) return null
    return when (preference) {
        VoicePreference.MALE -> matching.firstOrNull { it.gender == "male" } ?: matching.first()
        VoicePreference.FEMALE -> matching.firstOrNull { it.gender == "female" } ?: matching.first()
        VoicePreference.RANDOM -> matching.random()
        VoicePreference.ALTERNATE, null -> matching.first()
    }
}
