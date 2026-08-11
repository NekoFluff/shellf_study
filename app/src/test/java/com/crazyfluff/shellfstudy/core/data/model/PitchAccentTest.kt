package com.crazyfluff.shellfstudy.core.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PitchAccentTest {

    @Test
    fun `forReading matches an exact katakana entry when queried with hiragana`() {
        val entries = listOf(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0))

        val match = entries.forReading("みず")

        assertThat(match).isEqualTo(entries.first())
    }

    @Test
    fun `forReading falls back to a wildcard null-reading entry when no exact reading matches`() {
        val wildcard = PitchAccent(reading = null, partOfSpeech = null, pitchNumber = 1)
        val entries = listOf(PitchAccent(reading = "オミヤゲ", partOfSpeech = null, pitchNumber = 0), wildcard)

        val match = entries.forReading("ちがうよみ")

        assertThat(match).isEqualTo(wildcard)
    }

    @Test
    fun `forReading returns null when nothing matches and there is no wildcard`() {
        val entries = listOf(PitchAccent(reading = "オミヤゲ", partOfSpeech = null, pitchNumber = 0))

        assertThat(entries.forReading("ちがうよみ")).isNull()
    }

    @Test
    fun `forReading on an empty list returns null`() {
        assertThat(emptyList<PitchAccent>().forReading("みず")).isNull()
    }
}
