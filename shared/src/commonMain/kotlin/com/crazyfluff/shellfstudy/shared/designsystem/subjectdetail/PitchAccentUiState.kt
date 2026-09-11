package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.data.model.allForReading

/**
 * What we know about a *word's* pitch accent right now. [Unavailable] is a confirmed answer: the word
 * has been looked up and neither weblio nor the bundled dictionary documents a pitch accent for it.
 * [Loading] means no lookup has produced a confirmed result yet (no scrape has run, or the last one
 * failed); the background PitchAccentScrapeWorker retries it.
 *
 * This is the word-level answer the repository produces, keyed by the word's characters. What a
 * surface renders is [ReadingPitchAccent] — this answer projected onto one specific reading by
 * [forReading] — because which reading an entry applies to lives inside each [PitchAccent], not in
 * this type.
 */
sealed interface PitchAccentUiState {
    data object Loading : PitchAccentUiState
    data object Unavailable : PitchAccentUiState
    data class Available(val pitchAccents: List<PitchAccent>) : PitchAccentUiState
}

/**
 * One reading of a word and what we know about *its* pitch accent — the reading and its data travel
 * together, since the data is keyed by headword while each entry names the reading it applies to.
 * This is the only form a surface renders, so no renderer has to join the two itself.
 */
sealed interface ReadingPitchAccent {
    val reading: String

    /** Looked up, but nothing documented applies to this reading. That covers both a word with no
     *  pitch accent at all and a word whose entries name a different reading — either way there is no
     *  diagram to draw for this one, which is worth saying rather than leaving blank. */
    sealed interface NoPatterns : ReadingPitchAccent {
        /** The one-line explanation shown in place of a diagram. */
        val message: String
    }

    /** No lookup has produced a confirmed result for this word yet. */
    data class Pending(override val reading: String) : NoPatterns {
        override val message: String get() = "Pitch accent not checked yet"
    }

    /** The word has been checked and this reading has no documented pitch accent. */
    data class NoEntry(override val reading: String) : NoPatterns {
        override val message: String get() = "Pitch accent not available"
    }

    /** The patterns that apply to this reading — never empty: a reading with no match is [NoEntry]. */
    data class Patterns(override val reading: String, val patterns: List<PitchAccent>) : ReadingPitchAccent
}

/**
 * Projects word-level knowledge onto one of its readings: exact (katakana-normalised) matches, or the
 * wildcard entries that apply regardless of reading. Pure, so the matching rules stay testable without
 * Compose — and the only place the two are joined.
 *
 * Says nothing about whether the result should be *shown*: the "show pitch accent" preference belongs
 * to whoever is composing the screen, and it decides by rendering a diagram or not.
 */
fun PitchAccentUiState.forReading(reading: String): ReadingPitchAccent = when (this) {
    PitchAccentUiState.Loading -> ReadingPitchAccent.Pending(reading)
    PitchAccentUiState.Unavailable -> ReadingPitchAccent.NoEntry(reading)
    is PitchAccentUiState.Available -> pitchAccents.allForReading(reading)
        .takeIf { it.isNotEmpty() }
        ?.let { ReadingPitchAccent.Patterns(reading, it) }
        ?: ReadingPitchAccent.NoEntry(reading)
}
