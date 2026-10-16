package com.crazyfluff.shellfstudy.core.audio

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Audio focus held across a quick run of pronunciation clips rather than per clip, and only when
 * there is another app's audio to duck.
 *
 * Every focus request tells the app that held focus before to duck or pause, and every abandon
 * tells it focus is back — Android has no equivalent of iOS's "don't notify others on
 * deactivation". Media apps such as Spotify often keep focus while paused, so requesting and
 * abandoning around every clip sent a paused Spotify a loss/gain pair per clip, and a fast run of
 * reviews was enough to start it playing on its own. So:
 *
 * - The lease starts with [acquire]. Focus is requested only if other audio is playing right then
 *   ([AudioManager.isMusicActive]) or a call is up (whose focus the OS then denies); otherwise
 *   the lease plays without focus and no other app hears anything from it. The check runs only
 *   at the start of a lease — once it's active our own clip would count as active music.
 * - [release] doesn't end the lease straight away: it waits [releaseDelayMillis], and an [acquire]
 *   in that window carries on the same lease. Clearing twenty reviews in a row is one loss/gain
 *   pair for the music app, not twenty, at the cost of it staying ducked that much longer.
 *
 * [onLoss] runs when another app takes focus from us, permanently or transiently; that ends the
 * lease at once. Main thread only.
 */
class AudioFocusLease(
    private val audioManager: AudioManager,
    private val onLoss: () -> Unit,
    private val releaseDelayMillis: Long = RELEASE_DELAY_MILLIS,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    private var leaseActive = false
    private var holdsFocus = false
    private val endLease = Runnable { end() }

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setOnAudioFocusChangeListener({ focusChange ->
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                // End the lease outright, so the next clip asks again instead of assuming it still
                // has focus — during a phone call that request is denied and the clip stays silent.
                handler.removeCallbacks(endLease)
                end()
                onLoss()
            }
        }, handler)
        .build()

    /**
     * Starts or continues the lease. False when the OS denied focus (e.g. a phone call holds it
     * exclusively), in which case the clip shouldn't play.
     */
    fun acquire(): Boolean {
        handler.removeCallbacks(endLease)
        if (!leaseActive) {
            if (audioManager.isMusicActive || audioManager.mode != AudioManager.MODE_NORMAL) {
                if (audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                    return false
                }
                holdsFocus = true
            }
            leaseActive = true
        }
        return true
    }

    /** The current clip is done; the lease ends unless another [acquire] follows soon. */
    fun release() {
        if (!leaseActive) return
        handler.removeCallbacks(endLease)
        handler.postDelayed(endLease, releaseDelayMillis)
    }

    private fun end() {
        leaseActive = false
        if (holdsFocus) {
            holdsFocus = false
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    companion object {
        const val RELEASE_DELAY_MILLIS = 1_500L
    }
}
