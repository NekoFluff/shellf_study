package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The word-level -> reading-level projection, where the two halves of a pitch-accent answer are joined
 * (the data is keyed by headword; each entry names the reading it applies to). Pure Kotlin, so it runs
 * in the fast source set rather than under Compose.
 */
class ReadingPitchAccentTest {

    private val mizu = PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0)
    private val sui = PitchAccent(reading = "スイ", partOfSpeech = null, pitchNumber = 1)

    @Test
    fun anUncheckedWordIsPendingForEveryReading() {
        val projected = PitchAccentUiState.Loading.forReading("みず")

        assertEquals(ReadingPitchAccent.Pending("みず"), projected)
        assertEquals("Pitch accent not checked yet", (projected as ReadingPitchAccent.NoPatterns).message)
    }

    @Test
    fun aWordWithNoDocumentedPitchAccentHasNoEntryForEveryReading() {
        val projected = PitchAccentUiState.Unavailable.forReading("みず")

        assertEquals(ReadingPitchAccent.NoEntry("みず"), projected)
        assertEquals("Pitch accent not available", (projected as ReadingPitchAccent.NoPatterns).message)
    }

    @Test
    fun anAvailableWordResolvesEachReadingToItsOwnPatterns() {
        val state = PitchAccentUiState.Available(listOf(mizu, sui))

        assertEquals(ReadingPitchAccent.Patterns("みず", listOf(mizu)), state.forReading("みず"))
        assertEquals(ReadingPitchAccent.Patterns("スイ", listOf(sui)), state.forReading("スイ"))
    }

    @Test
    fun patternsCarryTheReadingTheyWereResolvedFor() {
        // The reading the caller asked about, even though the entry's own reading is katakana.
        val projected = PitchAccentUiState.Available(listOf(mizu)).forReading("みず")

        assertEquals("みず", projected.reading)
    }

    @Test
    fun aReadingWithNoEntryOfItsOwnIsLabelledRatherThanLeftBlank() {
        // The word has data, but for a different reading — weblio keys an entry by one canonical
        // reading, so this used to render silently while the sibling row drew a diagram.
        val projected = PitchAccentUiState.Available(listOf(sui)).forReading("みず")

        assertEquals(ReadingPitchAccent.NoEntry("みず"), projected)
    }

    @Test
    fun aWildcardEntryAppliesToWhicheverReadingWasAsked() {
        // Bundled dictionaries carry entries with no reading of their own (e.g. "ふじ山": [[null, null, 1]]),
        // which match any reading rather than one.
        val wildcard = PitchAccent(reading = null, partOfSpeech = null, pitchNumber = 1)

        val projected = PitchAccentUiState.Available(listOf(wildcard)).forReading("みず")

        assertEquals(ReadingPitchAccent.Patterns("みず", listOf(wildcard)), projected)
    }

    @Test
    fun anAvailableWordWithNoEntriesAtAllIsNoEntryForItsReadings() {
        // Not a state the repository produces, but the projection has to answer for it rather than
        // handing a renderer an empty pattern list to draw nothing from.
        val projected = PitchAccentUiState.Available(emptyList()).forReading("みず")

        assertEquals(ReadingPitchAccent.NoEntry("みず"), projected)
    }
}
