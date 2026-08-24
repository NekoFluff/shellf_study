package com.crazyfluff.shellfstudy.shared.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PitchAccentTest {

    @Test
    fun allForReadingMatchesAnExactKatakanaEntryWhenQueriedWithHiragana() {
        val entries = listOf(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0))

        val matches = entries.allForReading("みず")

        assertEquals(entries, matches)
    }

    @Test
    fun allForReadingReturnsEveryEntryForAReadingWithMultiplePitchPatterns() {
        // 一層(いっそう): heiban (0) as an adverb, nakadaka (1) as a noun — both are correct.
        val adverb = PitchAccent(reading = "イッソウ", partOfSpeech = "副", pitchNumber = 0)
        val noun = PitchAccent(reading = "イッソウ", partOfSpeech = "名", pitchNumber = 1)
        val otherReading = PitchAccent(reading = "イッソ", partOfSpeech = null, pitchNumber = 0)
        val entries = listOf(adverb, noun, otherReading)

        val matches = entries.allForReading("いっそう")

        assertEquals(listOf(adverb, noun), matches)
    }

    @Test
    fun allForReadingDeduplicatesIdenticalEntries() {
        val entry = PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0)
        val entries = listOf(entry, entry)

        val matches = entries.allForReading("みず")

        assertEquals(listOf(entry), matches)
    }

    @Test
    fun allForReadingFallsBackToWildcardNullReadingEntriesWhenNoExactReadingMatches() {
        val wildcard = PitchAccent(reading = null, partOfSpeech = null, pitchNumber = 1)
        val entries = listOf(PitchAccent(reading = "オミヤゲ", partOfSpeech = null, pitchNumber = 0), wildcard)

        val matches = entries.allForReading("ちがうよみ")

        assertEquals(listOf(wildcard), matches)
    }

    @Test
    fun allForReadingReturnsEmptyWhenNothingMatchesAndThereIsNoWildcard() {
        val entries = listOf(PitchAccent(reading = "オミヤゲ", partOfSpeech = null, pitchNumber = 0))

        assertEquals(emptyList(), entries.allForReading("ちがうよみ"))
    }

    @Test
    fun allForReadingOnAnEmptyListReturnsEmpty() {
        assertEquals(emptyList(), emptyList<PitchAccent>().allForReading("みず"))
    }
}
