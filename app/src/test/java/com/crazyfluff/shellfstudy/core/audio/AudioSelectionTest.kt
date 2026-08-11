package com.crazyfluff.shellfstudy.core.audio

import com.crazyfluff.shellfstudy.core.data.model.PronunciationAudio
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AudioSelectionTest {

    private fun audio(pronunciation: String?, gender: String? = null) = PronunciationAudio(
        url = "https://example.com/$pronunciation-$gender.mp3",
        contentType = "audio/mpeg",
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

        assertThat(selected?.pronunciation).isEqualTo("スイ")
    }

    @Test
    fun `falls back to the full list when no clip has matching reading metadata`() {
        val audios = listOf(audio(pronunciation = "べつ"), audio(pronunciation = "べつ"))

        val selected = selectAudioFor(audios, reading = "みず")

        assertThat(selected).isNotNull()
    }

    @Test
    fun `treats a clip with no pronunciation metadata as matching any reading`() {
        val audios = listOf(audio(pronunciation = null))

        val selected = selectAudioFor(audios, reading = "みず")

        assertThat(selected).isEqualTo(audios.first())
    }

    @Test
    fun `MALE preference picks a male clip among matches when one exists`() {
        val audios = listOf(
            audio(pronunciation = "みず", gender = "female"),
            audio(pronunciation = "みず", gender = "male")
        )

        val selected = selectAudioFor(audios, reading = "みず", preference = VoicePreference.MALE)

        assertThat(selected?.gender).isEqualTo("male")
    }

    @Test
    fun `MALE preference falls back to the first match when no male clip exists`() {
        val audios = listOf(audio(pronunciation = "みず", gender = "female"))

        val selected = selectAudioFor(audios, reading = "みず", preference = VoicePreference.MALE)

        assertThat(selected).isEqualTo(audios.first())
    }

    @Test
    fun `an empty audio list returns null`() {
        assertThat(selectAudioFor(emptyList(), reading = "みず")).isNull()
    }
}
