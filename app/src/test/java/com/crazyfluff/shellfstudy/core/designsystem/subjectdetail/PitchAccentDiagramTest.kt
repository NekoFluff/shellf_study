package com.crazyfluff.shellfstudy.core.designsystem.subjectdetail

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.core.data.model.PitchAccent
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PitchAccentDiagramTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `splitIntoMorae merges a combining small kana with the preceding mora`() {
        assertThat(splitIntoMorae("きゃく")).containsExactly("きゃ", "く").inOrder()
    }

    @Test
    fun `splitIntoMorae treats sokuon and chōon as their own morae`() {
        assertThat(splitIntoMorae("がっこう")).containsExactly("が", "っ", "こ", "う").inOrder()
    }

    @Test
    fun `isHighMora follows heiban atamadaka nakadaka and odaka patterns`() {
        // heiban (0): low, high, high, high
        assertThat((0..3).map { isHighMora(it, pitchNumber = 0, moraCount = 4) }).containsExactly(false, true, true, true).inOrder()
        // atamadaka (1): high, low, low, low
        assertThat((0..3).map { isHighMora(it, pitchNumber = 1, moraCount = 4) }).containsExactly(true, false, false, false).inOrder()
        // nakadaka (2 of 4): low, high, low, low
        assertThat((0..3).map { isHighMora(it, pitchNumber = 2, moraCount = 4) }).containsExactly(false, true, false, false).inOrder()
        // odaka (4 of 4): low, high, high, high
        assertThat((0..3).map { isHighMora(it, pitchNumber = 4, moraCount = 4) }).containsExactly(false, true, true, true).inOrder()
    }

    @Test
    fun `renders a diagram for every pitch pattern without crashing`() {
        composeTestRule.setContent {
            Column {
                for (pitchNumber in 0..3) {
                    PitchAccentDiagram(
                        reading = "みずうみ",
                        pitchAccent = PitchAccent(reading = "ミズウミ", partOfSpeech = null, pitchNumber = pitchNumber)
                    )
                }
            }
        }

        composeTestRule.onAllNodesWithTag(PitchAccentTestTags.DIAGRAM).assertCountEquals(4)
    }

    @Test
    fun `reading row shows the diagram plus the reading text when pitch data matches`() {
        composeTestRule.setContent {
            PitchAccentReadingRow(
                reading = "みず",
                pitchAccents = listOf(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0))
            )
        }

        composeTestRule.onNodeWithTag(PitchAccentTestTags.DIAGRAM).assertIsDisplayed()
        composeTestRule.onNodeWithText("みず").assertIsDisplayed()
    }

    @Test
    fun `reading row falls back to plain text when no pitch accent matches`() {
        composeTestRule.setContent {
            PitchAccentReadingRow(reading = "みず", pitchAccents = emptyList())
        }

        composeTestRule.onAllNodesWithTag(PitchAccentTestTags.DIAGRAM).assertCountEquals(0)
        composeTestRule.onNodeWithText("みず").assertIsDisplayed()
    }
}
