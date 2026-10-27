package com.crazyfluff.shellfstudy.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.PlaybackState
import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * Focus is held for exactly as long as a clip plays: requested right before it, abandoned when it
 * ends, fails or is stopped.
 */
@RunWith(AndroidJUnit4::class)
class RealPronunciationAudioPlayerFocusTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val shadowAudioManager = shadowOf(audioManager)
    private val exoPlayer = ExoPlayer.Builder(context).build()
    private val player = RealPronunciationAudioPlayer(exoPlayer, audioManager)

    private val clip = PronunciationAudio(
        url = "/nonexistent/mizu.mp3",
        contentType = "audio/mpeg",
        pronunciation = "みず",
        gender = null,
        voiceActorId = null,
        voiceActorName = null,
        voiceDescription = null
    )

    @After
    fun tearDown() = exoPlayer.release()

    @Test
    fun `a clip requests transient may-duck focus, without speech content type`() {
        player.play(clip)

        val request = shadowAudioManager.lastAudioFocusRequest
        assertThat(request).isNotNull()
        assertThat(request.durationHint).isEqualTo(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        assertThat(request.audioFocusRequest.audioAttributes.contentType)
            .isEqualTo(AudioAttributes.CONTENT_TYPE_UNKNOWN)
    }

    @Test
    fun `stopping the clip abandons focus`() {
        player.play(clip)
        assertThat(shadowAudioManager.lastAbandonedAudioFocusRequest).isNull()

        player.stop()

        assertThat(shadowAudioManager.lastAbandonedAudioFocusRequest).isNotNull()
    }

    @Test
    fun `replacing a playing clip keeps its focus`() {
        player.play(clip)
        val first = shadowAudioManager.lastAudioFocusRequest

        player.play(clip)

        assertThat(shadowAudioManager.lastAudioFocusRequest).isSameInstanceAs(first)
        assertThat(shadowAudioManager.lastAbandonedAudioFocusRequest).isNull()
    }

    @Test
    fun `each clip after the last ended asks again`() {
        player.play(clip)
        val first = shadowAudioManager.lastAudioFocusRequest
        player.stop()

        player.play(clip)

        assertThat(shadowAudioManager.lastAudioFocusRequest).isNotSameInstanceAs(first)
    }

    @Test
    fun `stopping with no clip playing does not abandon`() {
        player.stop()

        assertThat(shadowAudioManager.lastAbandonedAudioFocusRequest).isNull()
    }

    @Test
    fun `losing focus stops the clip and abandons`() {
        player.play(clip)

        shadowAudioManager.lastAudioFocusRequest.listener
            .onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)

        assertThat(player.state.value).isEqualTo(PlaybackState.IDLE)
        assertThat(shadowAudioManager.lastAbandonedAudioFocusRequest).isNotNull()
    }

    @Test
    fun `denied focus - a phone call - refuses the clip`() {
        shadowAudioManager.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)

        player.play(clip)

        assertThat(player.state.value).isEqualTo(PlaybackState.ERROR)
        player.stop()
        assertThat(shadowAudioManager.lastAbandonedAudioFocusRequest).isNull()
    }
}
