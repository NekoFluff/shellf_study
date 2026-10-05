package com.crazyfluff.shellfstudy.shared.data.audio

import com.crazyfluff.shellfstudy.shared.database.SubjectAudioRow
import com.crazyfluff.shellfstudy.shared.network.PronunciationAudioData
import com.crazyfluff.shellfstudy.shared.network.PronunciationAudioMetadataData
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryClipsTest {

    private fun data(url: String, contentType: String, gender: String, actor: Long, pronunciation: String = "みず") =
        PronunciationAudioData(
            url = url,
            contentType = contentType,
            metadata = PronunciationAudioMetadataData(
                gender = gender,
                voiceActorId = actor,
                pronunciation = pronunciation
            )
        )

    /** WaniKani's shape: each voice in three formats. */
    private val row = SubjectAudioRow(
        level = 4,
        pronunciationAudios = listOf(
            data("m.mp3", "audio/mpeg", "male", 1),
            data("m.ogg", "audio/ogg", "male", 1),
            data("m.webm", "audio/webm", "male", 1),
            data("f.mp3", "audio/mpeg", "female", 2),
            data("f.ogg", "audio/ogg", "female", 2),
            data("f.webm", "audio/webm", "female", 2)
        )
    )

    @Test
    fun `all voices keeps one mp3 per voice tagged with the subject's level`() {
        val clips = libraryClips(row, OfflineAudioVoices.ALL)
        assertEquals(setOf("m.mp3", "f.mp3"), clips.map { it.url }.toSet())
        assertEquals(setOf(4), clips.map { it.level }.toSet())
    }

    @Test
    fun `one gender keeps only that gender's voice`() {
        assertEquals(listOf("f.mp3"), libraryClips(row, OfflineAudioVoices.FEMALE).map { it.url })
        assertEquals(listOf("m.mp3"), libraryClips(row, OfflineAudioVoices.MALE).map { it.url })
    }

    @Test
    fun `a word recorded in none of the chosen voices keeps the voices it has`() {
        val maleOnly = row.copy(pronunciationAudios = row.pronunciationAudios.filter { it.metadata?.gender == "male" })
        assertEquals(listOf("m.mp3"), libraryClips(maleOnly, OfflineAudioVoices.FEMALE).map { it.url })
    }

    @Test
    fun `a voice with no mp3 keeps its first format`() {
        val noMp3 = row.copy(pronunciationAudios = row.pronunciationAudios.filter { it.contentType != "audio/mpeg" })
        assertEquals(setOf("m.ogg", "f.ogg"), libraryClips(noMp3, OfflineAudioVoices.ALL).map { it.url }.toSet())
    }

    @Test
    fun `each reading of a word keeps its own clips`() {
        val twoReadings = row.copy(
            pronunciationAudios = listOf(
                data("a.mp3", "audio/mpeg", "female", 2, pronunciation = "いち"),
                data("b.mp3", "audio/mpeg", "female", 2, pronunciation = "ひとつ")
            )
        )
        assertEquals(2, libraryClips(twoReadings, OfflineAudioVoices.FEMALE).size)
    }
}
