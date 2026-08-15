package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent

/** Looks up bundled/pre-scraped pitch-accent entries for a vocabulary word, keyed by its characters. */
interface PitchAccentBundledSource {
    suspend fun get(characters: String): List<PitchAccent>
}
