package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import kotlinx.coroutines.flow.StateFlow

/** Shared by both platforms' pronunciation-audio disk cache (Android's ExoPlayer SimpleCache,
 *  iOS's hand-rolled LRU file cache) so a change to the cache size budget can't leave one platform
 *  silently out of sync with the other. */
const val AUDIO_CACHE_MAX_BYTES = 50L * 1024 * 1024

enum class PlaybackState { IDLE, BUFFERING, PLAYING, ERROR }

/** Plays a single pronunciation clip at a time. */
interface PronunciationAudioPlayer {
    val state: StateFlow<PlaybackState>
    fun play(audio: PronunciationAudio)
    fun stop()
}
