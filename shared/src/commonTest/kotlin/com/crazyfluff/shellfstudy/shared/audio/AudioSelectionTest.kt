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
    gender: String? = null,
    voiceActorId: Long? = null
) = PronunciationAudio(
    url = url,
    contentType = contentType,
    pronunciation = pronunciation,
    gender = gender,
    voiceActorId = voiceActorId,
    voiceActorName = null,
    voiceDescription = null
)

class AudioSelectionTest {

    /** WaniKani's shape: two voice actors, each in several formats. */
    private val twoVoicesThreeFormats = listOf(
        audio("kenichi.mp3", gender = "male", voiceActorId = 1),
        audio("kenichi.ogg", contentType = "audio/ogg", gender = "male", voiceActorId = 1),
        audio("kenichi.webm", contentType = "audio/webm", gender = "male", voiceActorId = 1),
        audio("kyoko.mp3", gender = "female", voiceActorId = 2),
        audio("kyoko.ogg", contentType = "audio/ogg", gender = "female", voiceActorId = 2),
        audio("kyoko.webm", contentType = "audio/webm", gender = "female", voiceActorId = 2)
    )

    @Test
    fun `random never repeats the previous voice when another voice exists`() {
        var previous = selectAudioFor(twoVoicesThreeFormats, reading = "reading")
        repeat(50) {
            val next = selectAudioFor(twoVoicesThreeFormats, reading = "reading", previous = previous)
            assertTrue(next!!.voiceActorId != previous!!.voiceActorId)
            previous = next
        }
    }

    @Test
    fun `random replays the only voice when there is no other`() {
        val oneVoice = twoVoicesThreeFormats.filter { it.voiceActorId == 1L }
        val previous = selectAudioFor(oneVoice, reading = "reading")
        assertEquals(1L, selectAudioFor(oneVoice, reading = "reading", previous = previous)?.voiceActorId)
    }

    @Test
    fun `random plays a voice's mp3 when it has one`() {
        val picked = (1..50).map { selectAudioFor(twoVoicesThreeFormats, reading = "reading")?.url }.toSet()
        assertEquals(setOf("kenichi.mp3", "kyoko.mp3"), picked)
    }

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

    @Test
    fun `offline random rotates only among voices that are downloaded`() {
        val downloaded = setOf("kyoko.mp3")
        repeat(30) {
            val picked = selectAudioFor(
                twoVoicesThreeFormats,
                reading = "reading",
                isAvailable = { it.url in downloaded }
            )
            assertEquals("kyoko.mp3", picked?.url)
        }
    }

    @Test
    fun `offline the only downloaded voice replays rather than switching to one that can't play`() {
        val kyoko = twoVoicesThreeFormats.first { it.url == "kyoko.mp3" }
        val picked = selectAudioFor(
            twoVoicesThreeFormats,
            reading = "reading",
            previous = kyoko,
            isAvailable = { it.url == "kyoko.mp3" }
        )
        assertEquals("kyoko.mp3", picked?.url)
    }

    @Test
    fun `with nothing downloaded the choice is made as if everything were`() {
        val picked = (1..50).map {
            selectAudioFor(twoVoicesThreeFormats, reading = "reading", isAvailable = { false })?.url
        }.toSet()
        assertEquals(setOf("kenichi.mp3", "kyoko.mp3"), picked)
    }
}
