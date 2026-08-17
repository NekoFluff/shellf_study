package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFoundation.AVAudioSession
import platform.AVFoundation.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFoundation.AVAudioSessionCategoryPlayback
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.currentItem
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSTimer
import platform.Foundation.NSURL
import platform.darwin.NSObjectProtocol

/**
 * AVPlayer-backed implementation for iOS. State is derived from [AVPlayer.timeControlStatus]
 * (playing/buffering), polled on a repeating [NSTimer] rather than KVO — subclassing NSObject to
 * observe a keyPath is possible from Kotlin/Native but far more cinterop surface than this narrow
 * player needs — plus notification observers for end-of-playback and failure. There's no
 * multiplatform equivalent of ExoPlayer's [androidx.media3.common.Player.Listener], so this
 * mirrors the same states by different means.
 */
@OptIn(ExperimentalForeignApi::class)
class IosPronunciationAudioPlayer : PronunciationAudioPlayer {

    private val player = AVPlayer()
    private val _state = MutableStateFlow(PlaybackState.IDLE)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var endObserver: NSObjectProtocol? = null
    private var failureObserver: NSObjectProtocol? = null

    init {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(
            AVAudioSessionCategoryPlayback,
            withOptions = AVAudioSessionCategoryOptionDuckOthers,
            error = null
        )
        session.setActive(true, null)

        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _state.value = PlaybackState.IDLE }

        failureObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemFailedToPlayToEndTimeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _state.value = PlaybackState.ERROR }

        NSTimer.scheduledTimerWithTimeInterval(
            interval = 0.2,
            repeats = true
        ) { pollState() }
    }

    private fun pollState() {
        if (_state.value == PlaybackState.ERROR) return
        _state.value = when (player.timeControlStatus) {
            AVPlayerTimeControlStatusPlaying -> PlaybackState.PLAYING
            AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate -> PlaybackState.BUFFERING
            else -> if (player.currentItem == null) PlaybackState.IDLE else _state.value
        }
    }

    override fun play(audio: PronunciationAudio) {
        val url = NSURL.URLWithString(audio.url)
        if (url == null) {
            _state.value = PlaybackState.ERROR
            return
        }
        player.replaceCurrentItemWithPlayerItem(AVPlayerItem(uRL = url))
        player.play()
    }

    override fun stop() {
        player.pause()
        player.seekToTime(CMTimeMake(0, 1))
        player.replaceCurrentItemWithPlayerItem(null)
        _state.value = PlaybackState.IDLE
    }
}
