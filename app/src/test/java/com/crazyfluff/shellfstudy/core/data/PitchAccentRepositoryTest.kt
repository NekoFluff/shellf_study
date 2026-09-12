package com.crazyfluff.shellfstudy.core.data

import com.crazyfluff.shellfstudy.fakes.FakePitchAccentBundledSource
import com.crazyfluff.shellfstudy.shared.data.PitchAccentRepository
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentUiState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

private val MIZU = PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0)

/**
 * The bundled dictionary is now the sole pitch-accent source, so this classifies down to two states:
 * a bundled hit ([PitchAccentUiState.Available]) or a bundled miss ([PitchAccentUiState.Unavailable]).
 */
class PitchAccentRepositoryTest {

    @Test
    fun `a word covered by the bundled dictionary reports Available`() = runTest {
        val repository = PitchAccentRepository(FakePitchAccentBundledSource(mapOf("水" to listOf(MIZU))))

        assertThat(repository.observePitchAccents("水").first()).isEqualTo(PitchAccentUiState.Available(listOf(MIZU)))
    }

    @Test
    fun `a word absent from the bundled dictionary reports Unavailable`() = runTest {
        val repository = PitchAccentRepository(FakePitchAccentBundledSource())

        assertThat(repository.observePitchAccents("水").first()).isEqualTo(PitchAccentUiState.Unavailable)
    }
}
