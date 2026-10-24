package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import kotlinx.coroutines.flow.StateFlow

enum class PlaybackState { IDLE, BUFFERING, PLAYING, ERROR }

/** Plays a single pronunciation clip at a time. */
interface PronunciationAudioPlayer {
    val state: StateFlow<PlaybackState>
    fun play(audio: PronunciationAudio)
    fun stop()

    /** Whether [audio] can play without the network — see
     *  [com.crazyfluff.shellfstudy.shared.audio.selectAudioFor], which prefers clips that can. */
    fun isAvailableOffline(audio: PronunciationAudio): Boolean = true

    /** Called while a screen that plays a run of clips (reviews, lessons) is in front, so the
     *  platform can lower other apps' audio once for the whole visit rather than per clip. No-op where
     *  the platform handles that per clip (iOS). */
    fun holdFocus() {}

    /** Ends [holdFocus]. */
    fun releaseFocus() {}
}
