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
    selectAudioFor(pronunciationAudios, reading, preference, mp3Only, isAvailable = ::isAvailableOffline)?.let(::play)
}

/**
 * Picks the clip to play for [reading].
 *
 * [VoicePreference.RANDOM] picks a *voice* first and then that voice's clip, preferring mp3. WaniKani
 * sends every voice in several formats, so picking a file at random weighted voices by how many
 * formats each had. Pass the clip this button last played as [previous] and RANDOM skips that voice
 * whenever another one exists. Without that, repeated taps on one reading often replayed the same
 * voice several times in a row (a coin flip per tap), which looked like the button was stuck.
 *
 * [isAvailable] says which clips can play without the network (see
 * [PronunciationAudioPlayer.isAvailableOffline]). Those win over the rest at every step, so offline the
 * rotation stays among voices that are downloaded instead of landing on one that would play nothing.
 * When none are, the choice is made as if all were.
 */
fun selectAudioFor(
    audios: List<PronunciationAudio>,
    reading: String,
    preference: VoicePreference = VoicePreference.RANDOM,
    mp3Only: Boolean = false,
    previous: PronunciationAudio? = null,
    isAvailable: (PronunciationAudio) -> Boolean = { true }
): PronunciationAudio? {
    val eligible = if (mp3Only) audios.filter { it.contentType == MP3_CONTENT_TYPE } else audios
    if (eligible.isEmpty()) return null
    val matching = eligible.filter { it.pronunciation == null || it.pronunciation == reading }
        .ifEmpty { eligible }
    if (matching.isEmpty()) return null
    val ordered = matching.sortedByDescending(isAvailable)
    return when (preference) {
        VoicePreference.MALE -> ordered.firstOrNull { it.gender == "male" } ?: ordered.first()
        VoicePreference.FEMALE -> ordered.firstOrNull { it.gender == "female" } ?: ordered.first()
        VoicePreference.RANDOM -> randomVoice(ordered, previous, isAvailable)
        VoicePreference.ALTERNATE -> ordered.first()
    }
}

/** [VoicePreference.RANDOM]: a voice other than [previous]'s, then that voice's best clip — playable
 *  offline first, mp3 next. */
private fun randomVoice(
    clips: List<PronunciationAudio>,
    previous: PronunciationAudio?,
    isAvailable: (PronunciationAudio) -> Boolean
): PronunciationAudio {
    val allVoices = clips.groupBy(::voiceKey)
    val voices = allVoices.filterValues { voiceClips -> voiceClips.any(isAvailable) }.ifEmpty { allVoices }
    val fresh = previous?.let { voices - voiceKey(it) }?.takeIf { it.isNotEmpty() } ?: voices
    val voiceClips = fresh.values.random()
    return voiceClips.firstOrNull { isAvailable(it) && it.contentType == MP3_CONTENT_TYPE }
        ?: voiceClips.firstOrNull(isAvailable)
        ?: voiceClips.firstOrNull { it.contentType == MP3_CONTENT_TYPE }
        ?: voiceClips.first()
}

private const val MP3_CONTENT_TYPE = "audio/mpeg"

/** Which voice a clip is in: the voice actor when WaniKani says, else the gender, else the clip itself. */
private fun voiceKey(audio: PronunciationAudio): Any = audio.voiceActorId ?: audio.gender ?: audio.url
