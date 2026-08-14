package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.core.data.PitchAccentBundledSource
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent

/** In-memory stand-in for [PitchAccentBundledSource] — the real one needs a real Android Context to read res/raw. */
class FakePitchAccentBundledSource(
    private val entries: Map<String, List<PitchAccent>> = emptyMap()
) : PitchAccentBundledSource {
    override fun get(characters: String): List<PitchAccent> = entries[characters].orEmpty()
}
