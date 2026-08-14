package com.crazyfluff.shellfstudy.core.audio

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.crazyfluff.shellfstudy.shared.data.PlaybackState
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backed by one reusable [ExoPlayer] instance (injected already configured with HTTP caching and
 * automatic audio-focus handling — see AudioModule). Calling [play] while a clip is already playing
 * just replaces the current media item rather than overlapping two clips. The [PronunciationAudioPlayer]
 * interface itself lives in :shared; only this ExoPlayer-based implementation stays Android-only
 * until an AVPlayer-backed iOS actual is written.
 */
@Singleton
class RealPronunciationAudioPlayer @Inject constructor(
    private val exoPlayer: ExoPlayer
) : PronunciationAudioPlayer {

    private val _state = MutableStateFlow(PlaybackState.IDLE)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.value = when (playbackState) {
                    Player.STATE_BUFFERING -> PlaybackState.BUFFERING
                    Player.STATE_READY -> if (exoPlayer.isPlaying) PlaybackState.PLAYING else PlaybackState.IDLE
                    else -> PlaybackState.IDLE
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) _state.value = PlaybackState.PLAYING
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.value = PlaybackState.ERROR
            }
        })
    }

    override fun play(audio: PronunciationAudio) {
        exoPlayer.setMediaItem(MediaItem.fromUri(audio.url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    override fun stop() {
        exoPlayer.stop()
        _state.value = PlaybackState.IDLE
    }
}
