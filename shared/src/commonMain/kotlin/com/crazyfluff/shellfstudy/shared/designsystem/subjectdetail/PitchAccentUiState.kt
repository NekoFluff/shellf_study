package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent

/**
 * What we know about a word's pitch accent right now. An empty [Available] list is not a state worth
 * rendering — it means the entries we do have carry no pattern for the specific reading on screen
 * (a word can have several readings) — whereas [Unavailable] is a confirmed answer: the word has
 * been looked up and neither weblio nor the bundled dictionary documents a pitch accent for it.
 * [Loading] means the lookup hasn't produced a confirmed result yet (no scrape has run, or the last
 * one failed); the background PitchAccentScrapeWorker retries it.
 */
sealed interface PitchAccentUiState {
    data object Loading : PitchAccentUiState
    data object Unavailable : PitchAccentUiState
    data class Available(val pitchAccents: List<PitchAccent>) : PitchAccentUiState
}

/**
 * The entries in this state, or an empty list for anything that isn't [PitchAccentUiState.Available]
 * — for callers that only ever render a list (the quiz-time answer hint), where "pending" and
 * "confirmed absent" both just mean "no hint to show right now".
 */
fun PitchAccentUiState.availableOrEmpty(): List<PitchAccent> =
    (this as? PitchAccentUiState.Available)?.pitchAccents.orEmpty()
