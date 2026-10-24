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
 * Audio focus is never touched per clip. Every request tells the previous holder it lost focus and
 * every abandon tells it focus is back, and a paused Spotify that still held focus would sometimes
 * take that as a cue to start playing. Instead, review and lesson screens hold focus for as long as
 * they're in front ([holdFocus]/[releaseFocus]), so another app hears at most one loss/gain pair
 * per visit. The request isn't `CONTENT_TYPE_SPEECH`: for speech the OS skips automatic ducking and
 * tells the other app to handle the loss itself, whereas without it the OS just lowers that app's
 * volume, and the app doesn't need to react at all. Clips played elsewhere play without focus,
 * over whatever else is playing. ExoPlayer's own focus handling is off (see AudioModule).
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
            // Another app (or a call) took focus: it's theirs until the screen next resumes.
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                holdsFocus = false
                stop()
            }
        }
        .build()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    exoPlayer.stop()
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
                exoPlayer.stop()
                _state.value = PlaybackState.ERROR
            }
        })
    }

    override fun play(audio: PronunciationAudio) {
        // Not over a phone call — the role focus denial used to play.
        if (audioManager.mode != AudioManager.MODE_NORMAL) {
            _state.value = PlaybackState.ERROR
            return
        }
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(audio.url))))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    override fun stop() {
        exoPlayer.stop()
        _state.value = PlaybackState.IDLE
    }

    override fun holdFocus() {
        if (holdsFocus) return
        holdsFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun releaseFocus() {
        if (!holdsFocus) return
        holdsFocus = false
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
}
