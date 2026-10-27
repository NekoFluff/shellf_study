package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MutingPronunciationAudioPlayerTest {

    private class RecordingPlayer : PronunciationAudioPlayer {
        override val state = MutableStateFlow(PlaybackState.IDLE)
        val played = mutableListOf<PronunciationAudio>()
        var stopCount = 0

        override fun play(audio: PronunciationAudio) {
            played += audio
            state.value = PlaybackState.PLAYING
        }

        override fun stop() {
            stopCount++
            state.value = PlaybackState.IDLE
        }
    }

    private val clip = PronunciationAudio(
        url = "https://example.com/mizu.mp3",
        contentType = "audio/mpeg",
        pronunciation = "みず",
        gender = null,
        voiceActorId = null,
        voiceActorName = null,
        voiceDescription = null
    )

    private fun TestScope.player(delegate: PronunciationAudioPlayer, muted: StateFlow<Boolean>) =
        MutingPronunciationAudioPlayer(delegate, muted, backgroundScope)

    @Test
    fun `plays through while unmuted`() = runTest(UnconfinedTestDispatcher()) {
        val delegate = RecordingPlayer()
        val player = player(delegate, MutableStateFlow(false))

        player.play(clip)

        assertEquals(listOf(clip), delegate.played)
    }

    @Test
    fun `drops play while muted`() = runTest(UnconfinedTestDispatcher()) {
        val delegate = RecordingPlayer()
        val player = player(delegate, MutableStateFlow(true))

        player.play(clip)

        assertEquals(emptyList(), delegate.played)
    }

    @Test
    fun `muting stops the clip in progress and unmuting lets play through again`() =
        runTest(UnconfinedTestDispatcher()) {
            val delegate = RecordingPlayer()
            val muted = MutableStateFlow(false)
            val player = player(delegate, muted)
            player.play(clip)

            muted.value = true

            assertEquals(PlaybackState.IDLE, player.state.value)
            assertEquals(1, delegate.stopCount)

            muted.value = false
            player.play(clip)

            assertEquals(listOf(clip, clip), delegate.played)
        }
}
