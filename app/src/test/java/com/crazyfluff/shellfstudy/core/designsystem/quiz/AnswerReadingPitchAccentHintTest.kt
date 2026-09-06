package com.crazyfluff.shellfstudy.core.designsystem.quiz

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.AnswerReadingPitchAccentHint
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AnswerReadingPitchAccentHintTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `shows one diagram per matching pitch pattern, all stacked above one shared reading row`() {
        composeTestRule.setContent {
            AnswerReadingPitchAccentHint(
                reading = "みず",
                pitchAccents = listOf(
                    PitchAccent(reading = "ミズ", partOfSpeech = "副", pitchNumber = 0),
                    PitchAccent(reading = "ミズ", partOfSpeech = "名", pitchNumber = 1)
                )
            )
        }

        composeTestRule.onAllNodesWithTag(PitchAccentTestTags.DIAGRAM).assertCountEquals(2)
        // Each mora renders as its own Text (so it lines up flush against its own dot), not as one
        // merged "みず" node — and only once, shared underneath every diagram, not once per pattern.
        composeTestRule.onNodeWithText("み").assertIsDisplayed()
        composeTestRule.onNodeWithText("ず").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("み").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("ず").assertCountEquals(1)
    }

    @Test
    fun `shows only the reading when no pitch pattern matches`() {
        composeTestRule.setContent {
            AnswerReadingPitchAccentHint(reading = "みず", pitchAccents = emptyList())
        }

        composeTestRule.onAllNodesWithTag(PitchAccentTestTags.DIAGRAM).assertCountEquals(0)
        composeTestRule.onNodeWithText("み").assertIsDisplayed()
        composeTestRule.onNodeWithText("ず").assertIsDisplayed()
    }

    @Test
    fun `play button shows and invokes the callback when audio is available`() {
        var played = false
        composeTestRule.setContent {
            AnswerReadingPitchAccentHint(
                reading = "みず",
                pitchAccents = listOf(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0)),
                hasAudio = true,
                onPlayReading = { played = true }
            )
        }

        composeTestRule.onNodeWithContentDescription("Play pronunciation for みず").performClick()
        assert(played)
    }

    @Test
    fun `play button is absent when there is no audio`() {
        composeTestRule.setContent {
            AnswerReadingPitchAccentHint(
                reading = "みず",
                pitchAccents = listOf(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0)),
                hasAudio = false,
                onPlayReading = { }
            )
        }

        composeTestRule.onAllNodesWithContentDescription("Play pronunciation for みず").assertCountEquals(0)
    }
}
