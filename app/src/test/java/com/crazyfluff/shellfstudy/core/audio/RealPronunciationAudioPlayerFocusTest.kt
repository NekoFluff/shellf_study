package com.crazyfluff.shellfstudy.core.audio

import android.content.Context
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
 * Every focus request and abandon notifies the app that held focus before — a paused Spotify among
 * them, which used to wake into playing. Focus now moves only when a quiz screen resumes or pauses,
 * never per clip.
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
    fun `playing clips never touches focus`() {
        repeat(5) { player.play(clip) }
        player.stop()

        assertThat(shadowAudioManager.lastAudioFocusRequest).isNull()
        assertThat(shadowAudioManager.lastAbandonedAudioFocusRequest).isNull()
    }

    @Test
    fun `holding focus requests it once and releasing abandons it`() {
        player.holdFocus()
        val request = shadowAudioManager.lastAudioFocusRequest
        assertThat(request).isNotNull()
        assertThat(request.durationHint).isEqualTo(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)

        player.holdFocus()
        assertThat(shadowAudioManager.lastAudioFocusRequest).isSameInstanceAs(request)

        player.releaseFocus()
        assertThat(shadowAudioManager.lastAbandonedAudioFocusRequest).isNotNull()
    }

    @Test
    fun `releasing without holding does not abandon`() {
        player.releaseFocus()

        assertThat(shadowAudioManager.lastAbandonedAudioFocusRequest).isNull()
    }

    @Test
    fun `losing focus stops the clip and the next hold asks again`() {
        player.holdFocus()
        val first = shadowAudioManager.lastAudioFocusRequest
        player.play(clip)

        first.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertThat(player.state.value).isEqualTo(PlaybackState.IDLE)

        player.releaseFocus()
        assertThat(shadowAudioManager.lastAbandonedAudioFocusRequest).isNull()
        player.holdFocus()
        assertThat(shadowAudioManager.lastAudioFocusRequest).isNotSameInstanceAs(first)
    }

    @Test
    fun `a clip during a call is refused`() {
        audioManager.mode = AudioManager.MODE_IN_CALL

        player.play(clip)

        assertThat(player.state.value).isEqualTo(PlaybackState.ERROR)
    }
}
