package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.shared.data.PlaybackState
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakePronunciationAudioPlayer : PronunciationAudioPlayer {
    private val _state = MutableStateFlow(PlaybackState.IDLE)
    override val state: StateFlow<PlaybackState> = _state

    val playedAudios = mutableListOf<PronunciationAudio>()
    var focusHeld = false
        private set

    override fun play(audio: PronunciationAudio) {
        playedAudios.add(audio)
        _state.value = PlaybackState.PLAYING
    }

    override fun stop() {
        _state.value = PlaybackState.IDLE
    }

    override fun holdFocus() {
        focusHeld = true
    }

    override fun releaseFocus() {
        focusHeld = false
    }

    /** Test hook for reporting a state [play]/[stop] can't reach on their own — e.g. a failed load. */
    fun reportState(state: PlaybackState) {
        _state.value = state
    }
}
