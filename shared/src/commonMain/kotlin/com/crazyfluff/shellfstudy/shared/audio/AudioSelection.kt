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

/**
 * Picks the clip to play for [reading].
 *
 * [VoicePreference.RANDOM] picks a *voice* first and then that voice's clip, preferring mp3. WaniKani
 * sends every voice in several formats, so picking a file at random weighted voices by how many
 * formats each had. Pass the clip this button last played as [previous] and RANDOM skips that voice
 * whenever another one exists. Without that, repeated taps on one reading often replayed the same
 * voice several times in a row (a coin flip per tap), which looked like the button was stuck.
 */
fun selectAudioFor(
    audios: List<PronunciationAudio>,
    reading: String,
    preference: VoicePreference = VoicePreference.RANDOM,
    mp3Only: Boolean = false,
    previous: PronunciationAudio? = null
): PronunciationAudio? {
    val eligible = if (mp3Only) audios.filter { it.contentType == MP3_CONTENT_TYPE } else audios
    if (eligible.isEmpty()) return null
    val matching = eligible.filter { it.pronunciation == null || it.pronunciation == reading }
        .ifEmpty { eligible }
    if (matching.isEmpty()) return null
    return when (preference) {
        VoicePreference.MALE -> matching.firstOrNull { it.gender == "male" } ?: matching.first()
        VoicePreference.FEMALE -> matching.firstOrNull { it.gender == "female" } ?: matching.first()
        VoicePreference.RANDOM -> {
            val voices = matching.groupBy(::voiceKey)
            val fresh = previous?.let { voices - voiceKey(it) }?.takeIf { it.isNotEmpty() } ?: voices
            val clips = fresh.values.random()
            clips.firstOrNull { it.contentType == MP3_CONTENT_TYPE } ?: clips.first()
        }
        VoicePreference.ALTERNATE -> matching.first()
    }
}

private const val MP3_CONTENT_TYPE = "audio/mpeg"

/** Which voice a clip is in: the voice actor when WaniKani says, else the gender, else the clip itself. */
private fun voiceKey(audio: PronunciationAudio): Any = audio.voiceActorId ?: audio.gender ?: audio.url
