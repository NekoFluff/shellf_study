package com.crazyfluff.shellfstudy.core.audio

import android.net.Uri
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
 * Audio focus is handled here (through [AudioFocusLease]) rather than left to ExoPlayer's own
 * automatic handling: that feature only works for `USAGE_MEDIA`/`USAGE_GAME` — for this player's
 * `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` it silently never requests focus at all. The lease is
 * acquired right before each clip and released when it ends, fails or is stopped; it decides
 * whether focus is needed at all and holds it across a quick run of clips.
 */
class RealPronunciationAudioPlayer(
    private val exoPlayer: ExoPlayer,
    private val audioManager: AudioManager
) : PronunciationAudioPlayer {

    private val _state = MutableStateFlow(PlaybackState.IDLE)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val focus = AudioFocusLease(audioManager, onLoss = ::stop)

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
        focus.release()
    }

    override fun play(audio: PronunciationAudio) {
        // A denied request (e.g. an active phone call holding exclusive focus) means the OS has
        // decided this shouldn't play right now.
        if (!focus.acquire()) {
            _state.value = PlaybackState.ERROR
            return
        }
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(audio.url))))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    override fun stop() {
        releaseFocus()
        _state.value = PlaybackState.IDLE
    }
}
