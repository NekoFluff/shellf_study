package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The sole source of pitch-accent data: the bundled dictionary. See [PitchAccentBundledSource].
 */
class PitchAccentRepository(
    private val bundledSource: PitchAccentBundledSource
) {
    /** Classifies what the bundled dictionary knows about [characters] into the two renderable
     *  states — see [PitchAccentUiState]. */
    fun observePitchAccents(characters: String): Flow<PitchAccentUiState> = flow {
        val bundled = bundledSource.get(characters)
        emit(bundled.takeIf { it.isNotEmpty() }?.let(PitchAccentUiState::Available) ?: PitchAccentUiState.Unavailable)
    }
}
