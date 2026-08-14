package com.crazyfluff.shellfstudy.shared.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PitchAccentTest {

    @Test
    fun forReadingMatchesAnExactKatakanaEntryWhenQueriedWithHiragana() {
        val entries = listOf(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0))

        val match = entries.forReading("みず")

        assertEquals(entries.first(), match)
    }

    @Test
    fun forReadingFallsBackToAWildcardNullReadingEntryWhenNoExactReadingMatches() {
        val wildcard = PitchAccent(reading = null, partOfSpeech = null, pitchNumber = 1)
        val entries = listOf(PitchAccent(reading = "オミヤゲ", partOfSpeech = null, pitchNumber = 0), wildcard)

        val match = entries.forReading("ちがうよみ")

        assertEquals(wildcard, match)
    }

    @Test
    fun forReadingReturnsNullWhenNothingMatchesAndThereIsNoWildcard() {
        val entries = listOf(PitchAccent(reading = "オミヤゲ", partOfSpeech = null, pitchNumber = 0))

        assertNull(entries.forReading("ちがうよみ"))
    }

    @Test
    fun forReadingOnAnEmptyListReturnsNull() {
        assertNull(emptyList<PitchAccent>().forReading("みず"))
    }
}
