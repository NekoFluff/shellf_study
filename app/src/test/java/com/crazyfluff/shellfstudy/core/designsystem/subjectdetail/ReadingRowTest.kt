package com.crazyfluff.shellfstudy.core.designsystem.subjectdetail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.fakes.FakePronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.data.PlaybackState
import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.LocalPronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.ReadingRow
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Covers [ReadingRow] — the reading and its optional play button. Rendering it is how a screen shows a
 * reading whether or not the user wants pitch-accent diagrams, so it never consults pitch data.
 * Playback goes through [LocalPronunciationAudioPlayer], so every case here composes the row inside a
 * provider unless the point is that there isn't one.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReadingRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val audio = PronunciationAudio(
        url = "https://example.com/mizu.mp3",
        contentType = "audio/mpeg",
        pronunciation = "みず",
        gender = null,
        voiceActorId = null,
        voiceActorName = null,
        voiceDescription = null
    )

    private fun setContentWithPlayer(
        audio: PronunciationAudio?,
        player: FakePronunciationAudioPlayer = FakePronunciationAudioPlayer()
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalPronunciationAudioPlayer provides player) {
                ReadingRow(reading = "みず", audio = { audio })
            }
        }
    }

    @Test
    fun `renders the reading`() {
        setContentWithPlayer(audio = null)

        composeTestRule.onNodeWithText("みず").assertIsDisplayed()
    }

    @Test
    fun `shows a play button and plays the passed clip when one is offered`() {
        val player = FakePronunciationAudioPlayer()
        setContentWithPlayer(audio = audio, player = player)

        composeTestRule.onNodeWithContentDescription("Play pronunciation for みず").performClick()

        assertThat(player.playedAudios).containsExactly(audio)
    }

    @Test
    fun `re-invokes the audio selector on every tap instead of reusing the first pick`() {
        // Regression test: audio used to be a plain value selected once at composition, so every
        // tap replayed whatever composition happened to pick first — a caller backed by a pool of
        // clips (VoicePreference.RANDOM) never actually varied across taps, which is exactly what
        // made the randomizer look broken.
        var callCount = 0
        val player = FakePronunciationAudioPlayer()
        composeTestRule.setContent {
            CompositionLocalProvider(LocalPronunciationAudioPlayer provides player) {
                ReadingRow(reading = "みず", audio = { audio.copy(url = "${audio.url}#${callCount++}") })
            }
        }

        val playButton = composeTestRule.onNodeWithContentDescription("Play pronunciation for みず")
        playButton.performClick()
        playButton.performClick()

        // Each tap got its own distinct call — a memoized/precomputed value would have played the
        // same clip (whatever composition's own gating call produced) both times.
        assertThat(player.playedAudios).hasSize(2)
        assertThat(player.playedAudios[0]).isNotEqualTo(player.playedAudios[1])
    }

    @Test
    fun `shows no play button when there is no clip for the reading`() {
        setContentWithPlayer(audio = null)

        composeTestRule.onAllNodesWithContentDescription("Play pronunciation for みず").assertCountEquals(0)
    }

    @Test
    fun `shows no play button when no player is provided`() {
        composeTestRule.setContent {
            ReadingRow(reading = "みず", audio = { audio })
        }

        composeTestRule.onAllNodesWithContentDescription("Play pronunciation for みず").assertCountEquals(0)
    }

    @Test
    fun `reports a failed playback instead of looking idle`() {
        val player = FakePronunciationAudioPlayer().apply { reportState(PlaybackState.ERROR) }
        setContentWithPlayer(audio = audio, player = player)

        composeTestRule.onNodeWithContentDescription("Audio unavailable for みず").assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription("Play pronunciation for みず").assertCountEquals(0)
    }

    @Test
    fun `centers the play button against the reading it sits beside`() {
        setContentWithPlayer(audio = audio)

        val readingBounds = composeTestRule.onNodeWithText("みず").getUnclippedBoundsInRoot()
        val buttonBounds = composeTestRule.onNodeWithContentDescription("Play pronunciation for みず").getUnclippedBoundsInRoot()
        val readingCenterY = (readingBounds.top + readingBounds.bottom) / 2
        val buttonCenterY = (buttonBounds.top + buttonBounds.bottom) / 2
        assertThat((buttonCenterY - readingCenterY).value).isLessThan(4f)
    }
}
