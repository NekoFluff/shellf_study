package com.crazyfluff.shellfstudy.core.audio

import android.content.Context
import android.media.AudioManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * Every focus request and abandon notifies the app that held focus before — a paused Spotify
 * among them, which a fast run of reviews used to wake into playing. These pin down when the lease
 * talks to the OS at all.
 */
@RunWith(AndroidJUnit4::class)
class AudioFocusLeaseTest {

    private val audioManager =
        ApplicationProvider.getApplicationContext<Context>().getSystemService(AudioManager::class.java)
    private val shadowAudioManager = shadowOf(audioManager)
    private var losses = 0
    private val lease = AudioFocusLease(audioManager, onLoss = { losses++ }, releaseDelayMillis = DELAY)

    private fun advance(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    @Test
    fun `no other audio playing - plays without ever touching focus`() {
        shadowAudioManager.setIsMusicActive(false)

        assertThat(lease.acquire()).isTrue()
        lease.release()
        advance(DELAY)

        assertThat(shadowAudioManager.lastAudioFocusRequest).isNull()
        assertThat(shadowAudioManager.lastAbandonedAudioFocusRequest).isNull()
    }

    @Test
    fun `our own clip counting as music during an active lease does not start a focus request`() {
        shadowAudioManager.setIsMusicActive(false)
        lease.acquire()

        // The first clip is still playing when the next one is graded.
        shadowAudioManager.setIsMusicActive(true)
        lease.acquire()

        assertThat(shadowAudioManager.lastAudioFocusRequest).isNull()
    }

    @Test
    fun `other audio playing - requests focus and abandons it only after the delay`() {
        shadowAudioManager.setIsMusicActive(true)

        assertThat(lease.acquire()).isTrue()
        assertThat(shadowAudioManager.lastAudioFocusRequest).isNotNull()

        lease.release()
        advance(DELAY - 1)
        assertThat(shadowAudioManager.lastAbandonedAudioFocusRequest).isNull()

        advance(1)
        assertThat(shadowAudioManager.lastAbandonedAudioFocusRequest).isNotNull()
    }

    @Test
    fun `a quick run of clips is one request and one abandon`() {
        shadowAudioManager.setIsMusicActive(true)
        lease.acquire()
        val firstRequest = shadowAudioManager.lastAudioFocusRequest

        repeat(20) {
            lease.release()
            advance(DELAY / 2)
            lease.acquire()
        }

        assertThat(shadowAudioManager.lastAudioFocusRequest).isSameInstanceAs(firstRequest)
        assertThat(shadowAudioManager.lastAbandonedAudioFocusRequest).isNull()

        lease.release()
        advance(DELAY)
        assertThat(shadowAudioManager.lastAbandonedAudioFocusRequest).isNotNull()
    }

    @Test
    fun `a new lease after the delay checks for other audio again`() {
        shadowAudioManager.setIsMusicActive(false)
        lease.acquire()
        lease.release()
        advance(DELAY)

        shadowAudioManager.setIsMusicActive(true)
        lease.acquire()

        assertThat(shadowAudioManager.lastAudioFocusRequest).isNotNull()
    }

    @Test
    fun `denied focus refuses the clip`() {
        shadowAudioManager.setIsMusicActive(true)
        shadowAudioManager.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)

        assertThat(lease.acquire()).isFalse()
    }

    @Test
    fun `during a call focus is still requested, so the OS can deny it`() {
        shadowAudioManager.setIsMusicActive(false)
        audioManager.mode = AudioManager.MODE_IN_CALL
        shadowAudioManager.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)

        assertThat(lease.acquire()).isFalse()
    }

    @Test
    fun `losing focus ends the lease, so the next clip asks again`() {
        shadowAudioManager.setIsMusicActive(true)
        lease.acquire()
        val firstRequest = shadowAudioManager.lastAudioFocusRequest

        firstRequest.listener
            .onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertThat(losses).isEqualTo(1)

        shadowAudioManager.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        assertThat(lease.acquire()).isFalse()
        assertThat(shadowAudioManager.lastAudioFocusRequest).isNotSameInstanceAs(firstRequest)
    }

    @Test
    fun `ducking another app's request is not a loss`() {
        shadowAudioManager.setIsMusicActive(true)
        lease.acquire()

        shadowAudioManager.lastAudioFocusRequest.listener
            .onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)

        assertThat(losses).isEqualTo(0)
    }

    private companion object {
        const val DELAY = 1_500L
    }
}
