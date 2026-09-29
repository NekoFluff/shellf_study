package com.crazyfluff.shellfstudy.shared.audio

import com.crazyfluff.shellfstudy.shared.audio.VoicePreference
import com.crazyfluff.shellfstudy.shared.audio.selectAudioFor
import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

class AudioReadingMatchTest {

    private fun audio(
        pronunciation: String?,
        gender: String? = null,
        contentType: String = "audio/mpeg"
    ) = PronunciationAudio(
        url = "https://example.com/$pronunciation-$gender.mp3",
        contentType = contentType,
        pronunciation = pronunciation,
        gender = gender,
        voiceActorId = null,
        voiceActorName = null,
        voiceDescription = null
    )

    @Test
    fun `prefers the audio whose metadata names the requested reading`() {
        val audios = listOf(audio(pronunciation = "みず"), audio(pronunciation = "スイ"))

        val selected = selectAudioFor(audios, reading = "スイ")

        assertEquals("スイ", selected?.pronunciation)
    }

    @Test
    fun `falls back to the full list when no clip has matching reading metadata`() {
        val audios = listOf(audio(pronunciation = "べつ"), audio(pronunciation = "べつ"))

        val selected = selectAudioFor(audios, reading = "みず")

        assertNotNull(selected)
    }

    @Test
    fun `treats a clip with no pronunciation metadata as matching any reading`() {
        val audios = listOf(audio(pronunciation = null))

        val selected = selectAudioFor(audios, reading = "みず")

        assertEquals(audios.first(), selected)
    }

    @Test
    fun `MALE preference picks a male clip among matches when one exists`() {
        val audios = listOf(
            audio(pronunciation = "みず", gender = "female"),
            audio(pronunciation = "みず", gender = "male")
        )

        val selected = selectAudioFor(audios, reading = "みず", preference = VoicePreference.MALE)

        assertEquals("male", selected?.gender)
    }

    @Test
    fun `MALE preference falls back to the first match when no male clip exists`() {
        val audios = listOf(audio(pronunciation = "みず", gender = "female"))

        val selected = selectAudioFor(audios, reading = "みず", preference = VoicePreference.MALE)

        assertEquals(audios.first(), selected)
    }

    @Test
    fun `an empty audio list returns null`() {
        assertNull(selectAudioFor(emptyList(), reading = "みず"))
    }

    @Test
    fun `mp3Only filters out non-mp3 clips before matching`() {
        val audios = listOf(
            audio(pronunciation = "みず", contentType = "audio/ogg"),
            audio(pronunciation = "みず", contentType = "audio/mpeg")
        )

        val selected = selectAudioFor(audios, reading = "みず", mp3Only = true)

        assertEquals("audio/mpeg", selected?.contentType)
    }

    @Test
    fun `mp3Only returns null when no mp3 candidate exists`() {
        val audios = listOf(audio(pronunciation = "みず", contentType = "audio/ogg"))

        val selected = selectAudioFor(audios, reading = "みず", mp3Only = true)

        assertNull(selected)
    }

    @Test
    fun `mp3Only still applies voice preference among the filtered clips`() {
        val audios = listOf(
            audio(pronunciation = "みず", gender = "female", contentType = "audio/mpeg"),
            audio(pronunciation = "みず", gender = "male", contentType = "audio/mpeg"),
            audio(pronunciation = "みず", gender = "male", contentType = "audio/ogg")
        )

        val selected = selectAudioFor(audios, reading = "みず", preference = VoicePreference.MALE, mp3Only = true)

        assertEquals("male", selected?.gender)
        assertEquals("audio/mpeg", selected?.contentType)
    }
}
