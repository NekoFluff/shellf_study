package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.core.audio.PlaybackState
import com.crazyfluff.shellfstudy.core.audio.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.core.data.model.PronunciationAudio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakePronunciationAudioPlayer : PronunciationAudioPlayer {
    private val _state = MutableStateFlow(PlaybackState.IDLE)
    override val state: StateFlow<PlaybackState> = _state

    val playedAudios = mutableListOf<PronunciationAudio>()

    override fun play(audio: PronunciationAudio) {
        playedAudios.add(audio)
        _state.value = PlaybackState.PLAYING
    }

    override fun stop() {
        _state.value = PlaybackState.IDLE
    }
}
