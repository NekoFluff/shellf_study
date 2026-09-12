package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent

/** Looks up bundled/pre-scraped pitch-accent entries for a vocabulary word, keyed by its characters. */
interface PitchAccentBundledSource {
    suspend fun get(characters: String): List<PitchAccent>

    /** Parses and caches the bundled dictionary ahead of the first real lookup, so grading the
     *  first reading answer doesn't pay for it inline. Safe to call repeatedly — subsequent calls
     *  are no-ops once cached. */
    suspend fun preload()
}
