package com.crazyfluff.shellfstudy.core.designsystem.subjectdetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.MoraReadingText
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentDiagram
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentReadingRow
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentUiState
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
    fun `omitting textStyle reproduces today's bodyLarge sizing`() {
        composeTestRule.setContent {
            Box(modifier = Modifier.width(400.dp)) {
                Column {
                    Box(modifier = Modifier.testTag("default")) {
                        PitchAccentDiagram(
                            reading = "みずうみ",
                            pitchAccent = PitchAccent(reading = "ミズウミ", partOfSpeech = null, pitchNumber = 0)
                        )
                    }
                    Box(modifier = Modifier.testTag("explicitBodyLarge")) {
                        PitchAccentDiagram(
                            reading = "みずうみ",
                            pitchAccent = PitchAccent(reading = "ミズウミ", partOfSpeech = null, pitchNumber = 0),
                            textStyle = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }

        val defaultWidth = composeTestRule.onNodeWithTag("default").getUnclippedBoundsInRoot().let { it.right - it.left }
        val explicitBodyLargeWidth = composeTestRule.onNodeWithTag("explicitBodyLarge").getUnclippedBoundsInRoot().let { it.right - it.left }
        assertThat(defaultWidth.value).isEqualTo(explicitBodyLargeWidth.value)
    }

    @Test
    fun `a custom textStyle renders without crashing`() {
        // Robolectric's text measurement doesn't vary by declared font size in this harness (both
        // a bodyLarge- and a labelSmall-styled diagram measure identically here), so this can't
        // assert the resulting width actually narrows the way it does on a real device/simulator —
        // that's covered by manual verification instead. This just guards the signature change
        // itself: a non-default textStyle must not crash or fail to render.
        composeTestRule.setContent {
            PitchAccentDiagram(
                reading = "みずうみ",
                pitchAccent = PitchAccent(reading = "ミズウミ", partOfSpeech = null, pitchNumber = 0),
                textStyle = MaterialTheme.typography.labelSmall
            )
        }

        composeTestRule.onNodeWithTag(PitchAccentTestTags.DIAGRAM).assertIsDisplayed()
    }

    @Test
    fun `MoraReadingText renders one Text per mora`() {
        composeTestRule.setContent {
            MoraReadingText(reading = "みずうみ")
        }

        // "みずうみ" repeats a mora ("み" appears twice), so this checks each distinct mora has at
        // least one match rather than assuming a single unique node per mora.
        splitIntoMorae("みずうみ").distinct().forEach { mora ->
            composeTestRule.onAllNodesWithText(mora)[0].assertIsDisplayed()
        }
    }

    @Test
    fun `MoraReadingText and PitchAccentDiagram render independently, not tied to one another`() {
        composeTestRule.setContent {
            PitchAccentDiagram(
                reading = "みずうみ",
                pitchAccent = PitchAccent(reading = "ミズウミ", partOfSpeech = null, pitchNumber = 0)
            )
        }

        composeTestRule.onNodeWithTag(PitchAccentTestTags.DIAGRAM).assertIsDisplayed()
        splitIntoMorae("みずうみ").forEach { mora -> composeTestRule.onAllNodesWithText(mora).assertCountEquals(0) }
    }

    @Test
    fun `reading row shows the diagram plus the reading text when pitch data matches`() {
        composeTestRule.setContent {
            PitchAccentReadingRow(
                reading = "みず",
                pitchAccentState = PitchAccentUiState.Available(listOf(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0)))
            )
        }

        composeTestRule.onNodeWithTag(PitchAccentTestTags.DIAGRAM).assertIsDisplayed()
        composeTestRule.onNodeWithText("みず").assertIsDisplayed()
    }

    @Test
    fun `reading row renders no diagram when the available entries hold nothing for this reading`() {
        composeTestRule.setContent {
            PitchAccentReadingRow(
                reading = "みず",
                pitchAccentState = PitchAccentUiState.Available(
                    listOf(PitchAccent(reading = "ミズウミ", partOfSpeech = null, pitchNumber = 0))
                )
            )
        }

        composeTestRule.onAllNodesWithTag(PitchAccentTestTags.DIAGRAM).assertCountEquals(0)
        composeTestRule.onNodeWithText("みず").assertIsDisplayed()
    }

    @Test
    fun `reading row captions a confirmed-absent word instead of silently showing nothing`() {
        composeTestRule.setContent {
            PitchAccentReadingRow(reading = "みず", pitchAccentState = PitchAccentUiState.Unavailable)
        }

        composeTestRule.onAllNodesWithTag(PitchAccentTestTags.DIAGRAM).assertCountEquals(0)
        composeTestRule.onNodeWithText("みず").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pitch accent not available").assertIsDisplayed()
    }

    @Test
    fun `reading row renders neither diagram nor caption while the lookup is still pending`() {
        composeTestRule.setContent {
            PitchAccentReadingRow(reading = "みず", pitchAccentState = PitchAccentUiState.Loading)
        }

        composeTestRule.onAllNodesWithTag(PitchAccentTestTags.DIAGRAM).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Pitch accent not available").assertCountEquals(0)
        composeTestRule.onNodeWithText("みず").assertIsDisplayed()
    }

    @Test
    fun `reading row shows every pitch pattern for a reading with more than one, labeled by part of speech`() {
        composeTestRule.setContent {
            PitchAccentReadingRow(
                reading = "いっそう",
                pitchAccentState = PitchAccentUiState.Available(
                    listOf(
                        PitchAccent(reading = "イッソウ", partOfSpeech = "副", pitchNumber = 0),
                        PitchAccent(reading = "イッソウ", partOfSpeech = "名", pitchNumber = 1)
                    )
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
                pitchAccentState = PitchAccentUiState.Available(
                    listOf(
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
    fun `trailing content stays aligned with the reading text no matter how many pitch patterns render below it`() {
        composeTestRule.setContent {
            PitchAccentReadingRow(
                reading = "けっこう",
                pitchAccentState = PitchAccentUiState.Available(
                    listOf(
                        PitchAccent(reading = "ケッコウ", partOfSpeech = "副", pitchNumber = 0),
                        PitchAccent(reading = "ケッコウ", partOfSpeech = "名", pitchNumber = 1),
                        PitchAccent(reading = "ケッコウ", partOfSpeech = "名", pitchNumber = 2),
                        PitchAccent(reading = "ケッコウ", partOfSpeech = "形動", pitchNumber = 4)
                    )
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

    @Test
    fun `trailing content still renders for an Unavailable reading`() {
        composeTestRule.setContent {
            PitchAccentReadingRow(reading = "みず", pitchAccentState = PitchAccentUiState.Unavailable) {
                Box(modifier = Modifier.size(24.dp).testTag("trailing"))
            }
        }

        composeTestRule.onNodeWithTag("trailing").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pitch accent not available").assertIsDisplayed()
    }
}
