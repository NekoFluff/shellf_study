package com.crazyfluff.shellfstudy.core.audio

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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

/**
 * Backed by one reusable [ExoPlayer] instance (injected already configured with HTTP caching —
 * see AudioModule). Calling [play] while a clip is already playing just replaces the current
 * media item rather than overlapping two clips. The [PronunciationAudioPlayer] interface itself
 * lives in :shared, alongside an AVPlayer-backed iOS implementation
 * ([com.crazyfluff.shellfstudy.shared.data.IosPronunciationAudioPlayer]) — this ExoPlayer-based
 * one stays Android-only.
 *
 * Audio focus is requested here rather than left to ExoPlayer's own automatic handling: that
 * feature only works for `USAGE_MEDIA`/`USAGE_GAME` — for this player's
 * `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` it silently never requests focus at all, which is why
 * other apps' audio was never being ducked. Requesting `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`
 * ourselves, scoped tightly to each clip (requested right before [play], abandoned right after
 * each clip ends/fails or [stop] is called — never held between clips or for the life of the
 * screen), asks the OS to lower other apps' volume for exactly that window and nothing more.
 */
class RealPronunciationAudioPlayer(
    private val exoPlayer: ExoPlayer,
    private val audioManager: AudioManager
) : PronunciationAudioPlayer {

    private val _state = MutableStateFlow(PlaybackState.IDLE)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setOnAudioFocusChangeListener { focusChange ->
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
            ) {
                exoPlayer.pause()
            }
        }
        .build()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    releaseFocus()
                    _state.value = PlaybackState.IDLE
                    return
                }
                if (_state.value == PlaybackState.ERROR) return
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
                releaseFocus()
                _state.value = PlaybackState.ERROR
            }
        })
    }

    private fun releaseFocus() {
        exoPlayer.stop()
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    override fun play(audio: PronunciationAudio) {
        audioManager.requestAudioFocus(focusRequest)
        exoPlayer.setMediaItem(MediaItem.fromUri(audio.url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    override fun stop() {
        releaseFocus()
        _state.value = PlaybackState.IDLE
    }
}
