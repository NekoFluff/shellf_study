package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import kotlinx.coroutines.flow.Flow

/**
 * The one piece of [SubjectRepository]'s pitch-accent orchestration it actually needs. The real
 * implementation (bundled dictionary + weblio.jp scrape cache) stays in :app for now — it depends
 * on Android resource loading and jsoup, neither of which is portable yet.
 */
fun interface PitchAccentProvider {
    fun observePitchAccents(characters: String): Flow<List<PitchAccent>>
}
