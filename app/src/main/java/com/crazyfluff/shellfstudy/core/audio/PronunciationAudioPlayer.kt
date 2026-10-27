package com.crazyfluff.shellfstudy.core.audio

import android.net.Uri
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
import java.io.File

/**
 * Backed by one reusable [ExoPlayer] instance. Plays local files only: it sits behind
 * [com.crazyfluff.shellfstudy.shared.data.audio.LibraryBackedAudioPlayer], which hands it each clip
 * with `url` set to the stored file's path (see AudioModule). Calling [play] while a clip is already
 * playing just replaces the current media item rather than overlapping two clips. The
 * [PronunciationAudioPlayer] interface itself lives in :shared, alongside an AVPlayer-backed iOS implementation
 * ([com.crazyfluff.shellfstudy.shared.data.IosPronunciationAudioPlayer]) — this ExoPlayer-based
 * one stays Android-only.
 *
 * Audio focus is held per clip, as Android's guidance for short sounds has it: requested
 * (`GAIN_TRANSIENT_MAY_DUCK`) right before each clip and abandoned when it ends, fails or is
 * stopped — the same window iOS activates its audio session for. Since Android 8 the OS ducks the
 * other app's playing audio itself for a may-duck request and doesn't tell that app at all, so it
 * also hears no "focus is back" on abandon (the framework only dispatches a gain after a loss it
 * dispatched). A paused Spotify has nothing playing to duck, so it hears nothing either; only an
 * app that opted into pausing instead of ducking (`setWillPauseWhenDucked`) gets the loss/gain pair,
 * which is what it asked for. A denied request — a phone call — means the clip doesn't play.
 * ExoPlayer's own focus handling is off (see AudioModule).
 */
class RealPronunciationAudioPlayer(
    private val exoPlayer: ExoPlayer,
    private val audioManager: AudioManager
) : PronunciationAudioPlayer {

    private val _state = MutableStateFlow(PlaybackState.IDLE)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var holdsFocus = false

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).build()
        )
        .setOnAudioFocusChangeListener { focusChange ->
            // Another app (or a call) took focus: this clip is over, and the next one asks again.
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                stop()
            }
        }
        .build()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    endClip()
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
                endClip()
                _state.value = PlaybackState.ERROR
            }
        })
    }

    override fun play(audio: PronunciationAudio) {
        // Replacing a clip that's still playing keeps the focus it already holds.
        if (!holdsFocus) {
            if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                _state.value = PlaybackState.ERROR
                return
            }
            holdsFocus = true
        }
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(audio.url))))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    override fun stop() {
        endClip()
        _state.value = PlaybackState.IDLE
    }

    private fun endClip() {
        exoPlayer.stop()
        if (holdsFocus) {
            holdsFocus = false
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }
}
