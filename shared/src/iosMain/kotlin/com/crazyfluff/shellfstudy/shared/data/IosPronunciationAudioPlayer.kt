package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
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
 *
 * The audio session is activated right before each clip and deactivated right after — never left
 * active in between. [AVAudioSessionCategoryOptionDuckOthers] makes the OS lower (not pause) any
 * other app's audio for exactly that window, which is both the "tone down the music" behavior and
 * what keeps this from ever telling the OS another app is now free to resume: deactivation here
 * never passes `notifyOthersOnDeactivation`, so a paused app is never nudged back into playing.
 * Scoping activation tightly to each play() (rather than once for the player's lifetime) also
 * means the OS is asked "is other audio playing right now?" fresh for every clip, instead of an
 * app-lifetime-old answer becoming stale once the user pauses their own music mid-session.
 */
@OptIn(ExperimentalForeignApi::class)
class IosPronunciationAudioPlayer : PronunciationAudioPlayer {

    private val player = AVPlayer()
    private val _state = MutableStateFlow(PlaybackState.IDLE)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var endObserver: NSObjectProtocol? = null
    private var failureObserver: NSObjectProtocol? = null

    init {
        AVAudioSession.sharedInstance().setCategory(
            AVAudioSessionCategoryPlayback,
            withOptions = AVAudioSessionCategoryOptionDuckOthers,
            error = null
        )

        NSTimer.scheduledTimerWithTimeInterval(
            interval = 0.2,
            repeats = true
        ) { pollState() }
    }

    private fun removeItemObservers() {
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        failureObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = null
        failureObserver = null
    }

    /** Scoped to [item] rather than observed globally (`object = null`) so a stale notification
     *  from a clip that already finished can't fire after a newer clip has started playing. */
    private fun addItemObservers(item: AVPlayerItem) {
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue
        ) {
            _state.value = PlaybackState.IDLE
            deactivateSession()
        }

        failureObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemFailedToPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue
        ) {
            _state.value = PlaybackState.ERROR
            deactivateSession()
        }
    }

    /** Never passes `notifyOthersOnDeactivation` — see class doc. */
    private fun deactivateSession() {
        AVAudioSession.sharedInstance().setActive(false, null)
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
        removeItemObservers()
        AVAudioSession.sharedInstance().setActive(true, null)
        val item = AVPlayerItem(uRL = url)
        addItemObservers(item)
        player.replaceCurrentItemWithPlayerItem(item)
        player.play()
    }

    override fun stop() {
        removeItemObservers()
        player.pause()
        player.seekToTime(CMTimeMake(0, 1))
        player.replaceCurrentItemWithPlayerItem(null)
        _state.value = PlaybackState.IDLE
        deactivateSession()
    }
}
