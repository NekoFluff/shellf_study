package com.crazyfluff.shellfstudy.core.designsystem.subjectdetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.LocalPitchAccentCheck
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentDiagram
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.ReadingPitchAccent
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.isHighMora
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.splitIntoMorae
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private val MIZU = PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0)

/**
 * Covers [PitchAccentDiagram]: the patterns it was handed, drawn one diagram each, or the caption
 * rendered when there are none ("not checked yet" vs "not available"). The reading row beside it is
 * [com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.ReadingRow] — its own test file — and
 * how a word's data becomes those patterns is
 * [com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.forReading]'s job, also tested on its own.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PitchAccentDiagramTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // --- Mora splitting and pitch rules (no composition). ---

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

    // --- The row: the reading, its diagrams, and the caption when there are none. ---

    @Test
    fun `draws a diagram for every pattern and labels each by part of speech`() {
        composeTestRule.setContent {
            PitchAccentDiagram(
                readingPitchAccent = ReadingPitchAccent.Patterns(
                    reading = "いっそう",
                    patterns = listOf(
                        PitchAccent(reading = "イッソウ", partOfSpeech = "副", pitchNumber = 0),
                        PitchAccent(reading = "イッソウ", partOfSpeech = "名", pitchNumber = 1)
                    )
                )
            )
        }

        composeTestRule.onAllNodesWithTag(PitchAccentTestTags.DIAGRAM).assertCountEquals(2)
        composeTestRule.onNodeWithText("副").assertIsDisplayed()
        composeTestRule.onNodeWithText("名").assertIsDisplayed()
    }

    @Test
    fun `draws every one of four patterns without crashing`() {
        composeTestRule.setContent {
            Column {
                PitchAccentDiagram(
                    readingPitchAccent = ReadingPitchAccent.Patterns(
                        reading = "みずうみ",
                        patterns = (0..3).map { PitchAccent(reading = "ミズウミ", partOfSpeech = null, pitchNumber = it) }
                    )
                )
            }
        }

        composeTestRule.onAllNodesWithTag(PitchAccentTestTags.DIAGRAM).assertCountEquals(4)
    }

    @Test
    fun `sizes the diagram to its content instead of filling the available row`() {
        composeTestRule.setContent {
            Box(modifier = Modifier.width(400.dp)) {
                PitchAccentDiagram(readingPitchAccent = ReadingPitchAccent.Patterns(
                    reading = "くつ",
                    patterns = listOf(PitchAccent(reading = "クツ", partOfSpeech = null, pitchNumber = 2))
                ))
            }
        }

        val bounds = composeTestRule.onNodeWithTag(PitchAccentTestTags.DIAGRAM).getUnclippedBoundsInRoot()
        assertThat((bounds.right - bounds.left).value).isLessThan(150f)
    }

    @Test
    fun `stacks multiple patterns vertically rather than side by side`() {
        composeTestRule.setContent {
            PitchAccentDiagram(
                readingPitchAccent = ReadingPitchAccent.Patterns(
                    reading = "いっそう",
                    patterns = listOf(
                        PitchAccent(reading = "イッソウ", partOfSpeech = "副", pitchNumber = 0),
                        PitchAccent(reading = "イッソウ", partOfSpeech = "名", pitchNumber = 1)
                    )
                )
            )
        }

        val diagrams = composeTestRule.onAllNodesWithTag(PitchAccentTestTags.DIAGRAM)
        val firstBounds = diagrams[0].getUnclippedBoundsInRoot()
        val secondBounds = diagrams[1].getUnclippedBoundsInRoot()
        assertThat(secondBounds.top.value).isAtLeast(firstBounds.bottom.value)
    }

    @Test
    fun `captions a reading whose pitch accent has not been checked yet`() {
        composeTestRule.setContent {
            PitchAccentDiagram(readingPitchAccent = ReadingPitchAccent.Pending("みず"))
        }

        composeTestRule.onAllNodesWithTag(PitchAccentTestTags.DIAGRAM).assertCountEquals(0)
        composeTestRule.onNodeWithTag(PitchAccentTestTags.ROOT).assertIsDisplayed()
        composeTestRule.onNodeWithText("Pitch accent not checked yet").assertIsDisplayed()
        // The two states have to read differently — that distinction is the point of the type.
        composeTestRule.onAllNodesWithText("Pitch accent not available").assertCountEquals(0)
    }

    @Test
    fun `captions a reading with no documented pitch accent instead of showing nothing`() {
        composeTestRule.setContent {
            PitchAccentDiagram(readingPitchAccent = ReadingPitchAccent.NoEntry("みず"))
        }

        composeTestRule.onAllNodesWithTag(PitchAccentTestTags.DIAGRAM).assertCountEquals(0)
        composeTestRule.onNodeWithTag(PitchAccentTestTags.MESSAGE).assertIsDisplayed()
        composeTestRule.onNodeWithText("Pitch accent not available").assertIsDisplayed()
    }

    @Test
    fun `offers a check button under the not-checked-yet caption when the caller can fetch it`() {
        var checks = 0
        composeTestRule.setContent {
            CompositionLocalProvider(LocalPitchAccentCheck provides { checks++ }) {
                PitchAccentDiagram(readingPitchAccent = ReadingPitchAccent.Pending("みず"))
            }
        }

        composeTestRule.onNodeWithText("Pitch accent not checked yet").assertIsDisplayed()
        composeTestRule.onNodeWithTag(PitchAccentTestTags.CHECK).assertIsDisplayed().performClick()
        assertThat(checks).isEqualTo(1)
    }

    @Test
    fun `offers no check button when the caller has nothing to fetch with`() {
        composeTestRule.setContent {
            PitchAccentDiagram(readingPitchAccent = ReadingPitchAccent.Pending("みず"))
        }

        composeTestRule.onNodeWithText("Pitch accent not checked yet").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(PitchAccentTestTags.CHECK).assertCountEquals(0)
    }

    @Test
    fun `offers no check button for a confirmed absence even when the caller could fetch`() {
        // NoEntry has already been looked up — a retry there would be offering to repeat a
        // question that already has its answer.
        composeTestRule.setContent {
            CompositionLocalProvider(LocalPitchAccentCheck provides {}) {
                PitchAccentDiagram(readingPitchAccent = ReadingPitchAccent.NoEntry("みず"))
            }
        }

        composeTestRule.onNodeWithText("Pitch accent not available").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(PitchAccentTestTags.CHECK).assertCountEquals(0)
    }

}
