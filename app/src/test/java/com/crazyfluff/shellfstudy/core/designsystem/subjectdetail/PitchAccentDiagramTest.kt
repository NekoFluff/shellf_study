package com.crazyfluff.shellfstudy.core.designsystem.subjectdetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentDiagram
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentReadingRow
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.isHighMora
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.splitIntoMorae
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
    fun `diagram sizes itself to content width instead of filling the available row`() {
        composeTestRule.setContent {
            Box(modifier = Modifier.width(400.dp)) {
                PitchAccentDiagram(
                    reading = "くつ",
                    pitchAccent = PitchAccent(reading = "クツ", partOfSpeech = null, pitchNumber = 2)
                )
            }
        }

        val bounds = composeTestRule.onNodeWithTag(PitchAccentTestTags.DIAGRAM).getUnclippedBoundsInRoot()
        assertThat((bounds.right - bounds.left).value).isLessThan(150f)
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

    @Test
    fun `reading row shows every pitch pattern for a reading with more than one, labeled by part of speech`() {
        composeTestRule.setContent {
            PitchAccentReadingRow(
                reading = "いっそう",
                pitchAccents = listOf(
                    PitchAccent(reading = "イッソウ", partOfSpeech = "副", pitchNumber = 0),
                    PitchAccent(reading = "イッソウ", partOfSpeech = "名", pitchNumber = 1)
                )
            )
        }

        composeTestRule.onAllNodesWithTag(PitchAccentTestTags.DIAGRAM).assertCountEquals(2)
        composeTestRule.onNodeWithText("副").assertIsDisplayed()
        composeTestRule.onNodeWithText("名").assertIsDisplayed()
        composeTestRule.onNodeWithText("いっそう").assertIsDisplayed()
    }

    @Test
    fun `reading row stacks multiple pitch patterns vertically rather than side by side`() {
        composeTestRule.setContent {
            PitchAccentReadingRow(
                reading = "いっそう",
                pitchAccents = listOf(
                    PitchAccent(reading = "イッソウ", partOfSpeech = "副", pitchNumber = 0),
                    PitchAccent(reading = "イッソウ", partOfSpeech = "名", pitchNumber = 1)
                )
            )
        }

        val diagrams = composeTestRule.onAllNodesWithTag(PitchAccentTestTags.DIAGRAM)
        val firstBounds = diagrams[0].getUnclippedBoundsInRoot()
        val secondBounds = diagrams[1].getUnclippedBoundsInRoot()
        assertThat(secondBounds.top.value).isAtLeast(firstBounds.bottom.value)
    }

    @Test
    fun `trailing content stays aligned with the reading text no matter how many pitch patterns render below it`() {
        composeTestRule.setContent {
            PitchAccentReadingRow(
                reading = "けっこう",
                pitchAccents = listOf(
                    PitchAccent(reading = "ケッコウ", partOfSpeech = "副", pitchNumber = 0),
                    PitchAccent(reading = "ケッコウ", partOfSpeech = "名", pitchNumber = 1),
                    PitchAccent(reading = "ケッコウ", partOfSpeech = "名", pitchNumber = 2),
                    PitchAccent(reading = "ケッコウ", partOfSpeech = "形動", pitchNumber = 4)
                )
            ) {
                Box(modifier = Modifier.size(24.dp).testTag("trailing"))
            }
        }

        val textBounds = composeTestRule.onNodeWithText("けっこう").getUnclippedBoundsInRoot()
        val trailingBounds = composeTestRule.onNodeWithTag("trailing").getUnclippedBoundsInRoot()
        val textCenterY = (textBounds.top + textBounds.bottom) / 2
        val trailingCenterY = (trailingBounds.top + trailingBounds.bottom) / 2
        assertThat((trailingCenterY - textCenterY).value).isLessThan(4f)
    }
}
