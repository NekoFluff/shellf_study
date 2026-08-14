package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import kotlinx.coroutines.flow.StateFlow

enum class PlaybackState { IDLE, BUFFERING, PLAYING, ERROR }

/** Plays a single pronunciation clip at a time. */
interface PronunciationAudioPlayer {
    val state: StateFlow<PlaybackState>
    fun play(audio: PronunciationAudio)
    fun stop()
}
