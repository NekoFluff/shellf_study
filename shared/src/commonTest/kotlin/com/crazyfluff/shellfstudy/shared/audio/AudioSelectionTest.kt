package com.crazyfluff.shellfstudy.shared.audio

import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun audio(
    url: String,
    contentType: String = "audio/mpeg",
    pronunciation: String? = null,
    gender: String? = null
) = PronunciationAudio(
    url = url,
    contentType = contentType,
    pronunciation = pronunciation,
    gender = gender,
    voiceActorId = null,
    voiceActorName = null,
    voiceDescription = null
)

class AudioSelectionTest {

    @Test
    fun `random default varies across the eligible clips`() {
        val audios = listOf(
            audio("male.mp3", gender = "male"),
            audio("female.mp3", gender = "female")
        )
        val seenUrls = (1..50).map { selectAudioFor(audios, reading = "reading")?.url }.toSet()
        assertEquals(setOf("male.mp3", "female.mp3"), seenUrls)
    }

    @Test
    fun `mp3 only filters out non-mpeg clips before the random pick`() {
        val audios = listOf(
            audio("clip.webm", contentType = "audio/webm"),
            audio("clip.ogg", contentType = "audio/ogg")
        )
        assertNull(selectAudioFor(audios, reading = "reading", mp3Only = true))
    }

    @Test
    fun `mp3 only keeps mpeg clips eligible for the random pick`() {
        val audios = listOf(
            audio("clip.mp3", contentType = "audio/mpeg"),
            audio("clip.webm", contentType = "audio/webm")
        )
        assertEquals("clip.mp3", selectAudioFor(audios, reading = "reading", mp3Only = true)?.url)
    }

    @Test
    fun `single clip is deterministic`() {
        val audios = listOf(audio("only.mp3"))
        assertTrue((1..10).all { selectAudioFor(audios, reading = "reading")?.url == "only.mp3" })
    }

    @Test
    fun `male preference prefers a male clip regardless of default randomness`() {
        val audios = listOf(
            audio("female.mp3", gender = "female"),
            audio("male.mp3", gender = "male")
        )
        assertEquals("male.mp3", selectAudioFor(audios, reading = "reading", preference = VoicePreference.MALE)?.url)
    }

    @Test
    fun `female preference prefers a female clip regardless of default randomness`() {
        val audios = listOf(
            audio("male.mp3", gender = "male"),
            audio("female.mp3", gender = "female")
        )
        assertEquals(
            "female.mp3",
            selectAudioFor(audios, reading = "reading", preference = VoicePreference.FEMALE)?.url
        )
    }

    @Test
    fun `alternate preference always takes the first eligible clip`() {
        val audios = listOf(audio("first.mp3"), audio("second.mp3"))
        assertTrue(
            (1..10).all {
                selectAudioFor(audios, reading = "reading", preference = VoicePreference.ALTERNATE)?.url == "first.mp3"
            }
        )
    }
}
