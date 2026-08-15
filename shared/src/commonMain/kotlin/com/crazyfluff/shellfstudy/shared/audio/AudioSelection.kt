package com.crazyfluff.shellfstudy.shared.audio

import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio

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
