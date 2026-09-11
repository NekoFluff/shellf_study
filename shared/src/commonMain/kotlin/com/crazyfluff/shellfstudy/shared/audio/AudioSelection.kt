package com.crazyfluff.shellfstudy.shared.audio

import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio

/** Selects the matching clip for [reading] out of [pronunciationAudios] and plays it if one
 *  exists — the "select, then play if found" pairing every autoplay/manual-play call site needs
 *  (Review/Lesson grading, Review/Lesson's manual reading-hint play button, and the subject-detail
 *  sheet), so a caller with playback audio at hand never has to spell out [selectAudioFor] plus a
 *  null-check itself. */
fun PronunciationAudioPlayer.playMatchingReading(
    pronunciationAudios: List<PronunciationAudio>,
    reading: String,
    preference: VoicePreference = VoicePreference.RANDOM,
    mp3Only: Boolean = false
) {
    selectAudioFor(pronunciationAudios, reading, preference, mp3Only)?.let(::play)
}

fun selectAudioFor(
    audios: List<PronunciationAudio>,
    reading: String,
    preference: VoicePreference = VoicePreference.RANDOM,
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
        VoicePreference.ALTERNATE -> matching.first()
    }
}
