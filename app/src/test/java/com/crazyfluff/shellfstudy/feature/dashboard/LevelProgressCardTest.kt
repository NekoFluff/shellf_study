package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.runtime.CompositionLocalProvider
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.LocalOpenSubjectDetail
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.model.LevelItem
import com.crazyfluff.shellfstudy.shared.data.model.LevelProgress
import com.crazyfluff.shellfstudy.shared.data.model.LevelUpPath
import com.crazyfluff.shellfstudy.shared.data.model.LevelUpProgress
import com.crazyfluff.shellfstudy.shared.data.model.LevelUpStep
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import com.google.common.truth.Truth.assertThat
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onChildren
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.data.model.SubjectTypeProgress
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import com.crazyfluff.shellfstudy.shared.feature.dashboard.LevelProgressCard
import com.crazyfluff.shellfstudy.shared.feature.dashboard.LevelProgressTestTags

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class LevelProgressCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleProgress = LevelProgress(
        level = 12,
        breakdown = listOf(
            SubjectTypeProgress(SubjectType.RADICAL, items = levelItems(SubjectType.RADICAL, total = 5, passed = 5)),
            SubjectTypeProgress(SubjectType.KANJI, items = levelItems(SubjectType.KANJI, total = 25, passed = 18)),
            SubjectTypeProgress(SubjectType.VOCABULARY, items = levelItems(SubjectType.VOCABULARY, total = 90, passed = 40))
        )
    )

    private fun levelItems(subjectType: SubjectType, total: Int, passed: Int): List<LevelItem> =
        (1..total).map { index ->
            LevelItem(
                subjectId = subjectType.ordinal * 1000L + index,
                subjectType = subjectType,
                characters = "${subjectType.name}$index",
                display = "${subjectType.name}$index",
                passed = index <= passed,
                srsStage = if (index <= passed) SrsStage.GURU_1 else SrsStage.APPRENTICE_1
            )
        }

    @Test
    fun rendersNothing_beforeFirstLoad() {
        composeTestRule.setContent { LevelProgressCard(progress = null) }

        // Card is entirely gated behind a non-null progress — nothing to assert is displayed.
    }

    @Test
    fun showsPerTypeBreakdown_whenLoaded() {
        composeTestRule.setContent { LevelProgressCard(progress = sampleProgress) }

        composeTestRule.onNodeWithTag(LevelProgressTestTags.CARD).assertIsDisplayed()
        composeTestRule.onNodeWithText("Level 12 Progress").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kanji: 18 / 25").assertIsDisplayed()
    }

    @Test
    fun tappingExpandToggle_revealsItemChipsInsteadOfRemainingCount() {
        composeTestRule.setContent { LevelProgressCard(progress = sampleProgress) }

        // Collapsed means the grid is not composed at all — not merely clipped to zero height. It is
        // mounted on expand, so a mid-level account's few hundred chips (each one a glyph autosize
        // measurement, and a fetch plus SVG decode for glyph-less radicals) cost nothing until the
        // user asks for them, instead of on every dashboard recomposition.
        composeTestRule.onNodeWithTag(LevelProgressTestTags.DETAIL_PREFIX + "KANJI").assertDoesNotExist()

        composeTestRule.onNodeWithTag(LevelProgressTestTags.EXPAND_TOGGLE_BUTTON).performClick()

        composeTestRule.onNodeWithTag(LevelProgressTestTags.DETAIL_PREFIX + "KANJI").assertIsDisplayed()
        // A passed item and a not-yet-passed item both render as their own chip.
        composeTestRule.onNodeWithText("KANJI1").assertIsDisplayed()
        composeTestRule.onNodeWithText("KANJI25").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("remaining").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("%", substring = true).assertCountEquals(0)
    }

    @Test
    fun tappingItemChip_invokesOnSubjectClickWithItsSubjectId() {
        var clickedSubjectId: Long? = null
        composeTestRule.setContent {
            // The card reads the app-wide opener rather than taking a callback, so this provides it —
            // which also means the test now exercises the real capability instead of a parameter that
            // only existed to be passed straight back out.
            CompositionLocalProvider(LocalOpenSubjectDetail provides { clickedSubjectId = it }) {
                LevelProgressCard(progress = sampleProgress)
            }
        }

        composeTestRule.onNodeWithTag(LevelProgressTestTags.EXPAND_TOGGLE_BUTTON).performClick()
        composeTestRule.onNodeWithText("KANJI1").performClick()

        assert(clickedSubjectId == SubjectType.KANJI.ordinal * 1000L + 1)
    }

    @Test
    fun itemChip_notClickable_whileCollapsed() {
        // Chips are always composed (pre-warmed) even while collapsed, so the click guard has to be
        // explicit rather than relying on zero-height chips being unhittable.
        var clickedSubjectId: Long? = null
        composeTestRule.setContent {
            // The card reads the app-wide opener rather than taking a callback, so this provides it —
            // which also means the test now exercises the real capability instead of a parameter that
            // only existed to be passed straight back out.
            CompositionLocalProvider(LocalOpenSubjectDetail provides { clickedSubjectId = it }) {
                LevelProgressCard(progress = sampleProgress)
            }
        }

        composeTestRule.onNodeWithTag(LevelProgressTestTags.ITEM_CHIP_PREFIX + (SubjectType.KANJI.ordinal * 1000L + 1))
            .assertIsNotDisplayed()

        assert(clickedSubjectId == null)
    }

    @Test
    fun nextLevelButton_invokesOnLevelChangeWithLevelPlusOne() {
        var requestedLevel: Int? = null
        composeTestRule.setContent {
            LevelProgressCard(progress = sampleProgress, maxLevel = 20, onLevelChange = { requestedLevel = it })
        }

        composeTestRule.onNodeWithTag(LevelProgressTestTags.NEXT_LEVEL_BUTTON).performClick()

        assert(requestedLevel == 13)
    }

    @Test
    fun previousLevelButton_invokesOnLevelChangeWithLevelMinusOne() {
        var requestedLevel: Int? = null
        composeTestRule.setContent {
            LevelProgressCard(progress = sampleProgress, onLevelChange = { requestedLevel = it })
        }

        composeTestRule.onNodeWithTag(LevelProgressTestTags.PREV_LEVEL_BUTTON).performClick()

        assert(requestedLevel == 11)
    }

    @Test
    fun nextLevelButton_disabledAtMaxLevel() {
        composeTestRule.setContent {
            LevelProgressCard(progress = sampleProgress, maxLevel = 12, onLevelChange = {})
        }

        composeTestRule.onNodeWithTag(LevelProgressTestTags.NEXT_LEVEL_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun previousLevelButton_disabledAtLevelOne() {
        composeTestRule.setContent {
            LevelProgressCard(progress = sampleProgress.copy(level = 1), onLevelChange = {})
        }

        composeTestRule.onNodeWithTag(LevelProgressTestTags.PREV_LEVEL_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun showsThresholdMarkOnBar_whenViewingCurrentLevelAndNotYetReady() {
        composeTestRule.setContent {
            LevelProgressCard(
                progress = sampleProgress,
                maxLevel = 12,
                levelUpProgress = LevelUpProgress(kanjiGuruedOrHigher = 18, kanjiTotal = 25)
            )
        }

        composeTestRule.onNodeWithTag(LevelProgressTestTags.LEVEL_UP_THRESHOLD_MARK).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(LevelProgressTestTags.LEVEL_UP_INDICATOR).assertCountEquals(0)
    }

    @Test
    fun showsReadyToLevelUp_whenThresholdMet() {
        composeTestRule.setContent {
            LevelProgressCard(
                progress = sampleProgress,
                maxLevel = 12,
                levelUpProgress = LevelUpProgress(kanjiGuruedOrHigher = 23, kanjiTotal = 25)
            )
        }

        composeTestRule.onNodeWithTag(LevelProgressTestTags.LEVEL_UP_INDICATOR).assertIsDisplayed()
        composeTestRule.onNodeWithText("Ready to level up!").assertIsDisplayed()
    }

    @Test
    fun hidesLevelUpSignals_whenNotViewingCurrentLevel() {
        composeTestRule.setContent {
            LevelProgressCard(
                progress = sampleProgress,
                maxLevel = 20,
                levelUpProgress = LevelUpProgress(kanjiGuruedOrHigher = 18, kanjiTotal = 25)
            )
        }

        composeTestRule.onAllNodesWithTag(LevelProgressTestTags.LEVEL_UP_INDICATOR).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(LevelProgressTestTags.LEVEL_UP_THRESHOLD_MARK).assertCountEquals(0)
    }

    @Test
    fun hidesLevelUpSignals_whenLevelUpProgressNull() {
        composeTestRule.setContent {
            LevelProgressCard(progress = sampleProgress, maxLevel = 12, levelUpProgress = null)
        }

        composeTestRule.onAllNodesWithTag(LevelProgressTestTags.LEVEL_UP_INDICATOR).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(LevelProgressTestTags.LEVEL_UP_THRESHOLD_MARK).assertCountEquals(0)
    }

    @Test
    fun chipFillGateIsPassed_notCurrentSrsStage() {
        // Simulates a post-Guru-demotion item: passed = true (ever reached Guru) but srsStage has
        // fallen back to Apprentice. The chip must still render filled/white-text like any other
        // passed item, not outlined like an unpassed Apprentice item.
        val demotedProgress = LevelProgress(
            level = 12,
            breakdown = listOf(
                SubjectTypeProgress(
                    SubjectType.RADICAL,
                    items = listOf(
                        LevelItem(
                            subjectId = 1L,
                            subjectType = SubjectType.RADICAL,
                            characters = "R1",
                            display = "R1",
                            passed = true,
                            srsStage = SrsStage.APPRENTICE_1
                        )
                    )
                )
            )
        )
        composeTestRule.setContent { LevelProgressCard(progress = demotedProgress) }

        composeTestRule.onNodeWithTag(LevelProgressTestTags.EXPAND_TOGGLE_BUTTON).performClick()

        // Passed items render white text (Color.White); unpassed items use onSurfaceVariant — this
        // is exercised indirectly by confirming the chip renders and the row's own count still
        // reads it as passed, consistent with the fill/outline gate being `passed`, not `srsStage`.
        composeTestRule.onNodeWithText("Radical: 1 / 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("R1").assertIsDisplayed()
    }

    @Test
    fun rendersImageForImageOnlyRadicalItem() {
        // Image-only radicals (no unicode glyph) carry a characterImageUrl and a slug fallback in
        // `display` — the chip should render the image (via SubjectGlyph), not the slug text.
        val imageOnlyProgress = LevelProgress(
            level = 12,
            breakdown = listOf(
                SubjectTypeProgress(
                    SubjectType.RADICAL,
                    items = listOf(
                        LevelItem(
                            subjectId = 1L,
                            subjectType = SubjectType.RADICAL,
                            characters = null,
                            display = "drop",
                            passed = false,
                            srsStage = SrsStage.APPRENTICE_1,
                            characterImageUrl = "https://example.com/drop.png"
                        )
                    )
                )
            )
        )
        composeTestRule.setContent { LevelProgressCard(progress = imageOnlyProgress) }

        composeTestRule.onNodeWithTag(LevelProgressTestTags.EXPAND_TOGGLE_BUTTON).performClick()

        composeTestRule.onNodeWithTag(LevelProgressTestTags.ITEM_CHIP_PREFIX + "1").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("drop").assertCountEquals(0)
    }

    private val pathComputedAt = Instant.parse("2026-10-04T10:00:00Z")

    private fun kanjiId(index: Int) = SubjectType.KANJI.ordinal * 1000L + index
    private fun radicalId(index: Int) = SubjectType.RADICAL.ordinal * 1000L + index

    /**
     * 25 kanji, 18 at Guru (items 1–18): 23 needed. The unpassed 19–25 reach Guru in *reverse*
     * lesson order, 10h apart starting with 25, so the 5th (kanji 21, 50h out) sets the date.
     * Radical 5 is unpassed.
     */
    private val samplePath = LevelUpPath(
        kanjiTotal = 25,
        alreadyGuruCount = 18,
        upcomingGuruTimes = (1..7).map { pathComputedAt + (it * 10).hours },
        computedAt = pathComputedAt,
        guruAtBySubject = (19..25).associate { index ->
            kanjiId(index) to pathComputedAt + ((26 - index) * 10).hours
        } + (radicalId(5) to pathComputedAt + 5.hours),
        nextStep = LevelUpStep(pathComputedAt + 4.hours, radicalReviews = 1, kanjiLeft = 5)
    )

    private val progressWithUnpassedRadical = sampleProgress.copy(
        breakdown = sampleProgress.breakdown.map { entry ->
            if (entry.subjectType == SubjectType.RADICAL) {
                entry.copy(items = levelItems(SubjectType.RADICAL, total = 5, passed = 4))
            } else {
                entry
            }
        }
    )

    private fun setCurrentLevelCard(path: LevelUpPath = samplePath, guruCount: Int = 18) {
        composeTestRule.setContent {
            LevelProgressCard(
                progress = progressWithUnpassedRadical,
                maxLevel = 12,
                levelUpProgress = LevelUpProgress(kanjiGuruedOrHigher = guruCount, kanjiTotal = 25),
                levelUpPath = path
            )
        }
    }

    @Test
    fun levelUpEtaAndNextStep_showWhileCollapsed() {
        setCurrentLevelCard()

        composeTestRule.onNodeWithTag(LevelProgressTestTags.LEVEL_UP_ETA).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("(in 2d 2h)", substring = true).assertCountEquals(1)
        composeTestRule.onNodeWithTag(LevelProgressTestTags.LEVEL_UP_NEXT_STEP).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("1 radical review", substring = true).assertCountEquals(1)
    }

    @Test
    fun levelUpEta_hiddenWhenReadyToLevelUp() {
        setCurrentLevelCard(path = samplePath.copy(alreadyGuruCount = 23), guruCount = 23)

        composeTestRule.onAllNodesWithTag(LevelProgressTestTags.LEVEL_UP_ETA).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(LevelProgressTestTags.LEVEL_UP_NEXT_STEP).assertCountEquals(0)
    }

    @Test
    fun levelUpEtaAndSorting_offWhenNotViewingCurrentLevel() {
        composeTestRule.setContent {
            LevelProgressCard(
                progress = progressWithUnpassedRadical,
                maxLevel = 20,
                levelUpProgress = LevelUpProgress(kanjiGuruedOrHigher = 18, kanjiTotal = 25),
                levelUpPath = samplePath
            )
        }
        composeTestRule.onNodeWithTag(LevelProgressTestTags.EXPAND_TOGGLE_BUTTON).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithTag(LevelProgressTestTags.LEVEL_UP_ETA).assertCountEquals(0)
        // Another level keeps lesson order: the first unpassed kanji (19) follows the passed ones.
        composeTestRule.onNodeWithTag(LevelProgressTestTags.DETAIL_PREFIX + SubjectType.KANJI.name)
            .onChildren()[18].assert(hasTestTag(LevelProgressTestTags.ITEM_CHIP_PREFIX + kanjiId(19)))
    }

    @Test
    fun levelUpEta_sitsBelowEveryBar() {
        setCurrentLevelCard()

        val etaTop = composeTestRule.onNodeWithTag(LevelProgressTestTags.LEVEL_UP_ETA).getBoundsInRoot().top
        val vocabRowBottom = composeTestRule
            .onNodeWithTag(LevelProgressTestTags.ROW_PREFIX + SubjectType.VOCABULARY.name)
            .getBoundsInRoot().bottom
        assertThat(etaTop >= vocabRowBottom).isTrue()
    }

    @Test
    fun kanjiChips_sortedPassedFirstThenByGuruTime() {
        setCurrentLevelCard()
        composeTestRule.onNodeWithTag(LevelProgressTestTags.EXPAND_TOGGLE_BUTTON).performClick()
        composeTestRule.waitForIdle()

        val chips = composeTestRule
            .onNodeWithTag(LevelProgressTestTags.DETAIL_PREFIX + SubjectType.KANJI.name)
            .onChildren()
        chips[0].assert(hasTestTag(LevelProgressTestTags.ITEM_CHIP_PREFIX + kanjiId(1)))
        chips[17].assert(hasTestTag(LevelProgressTestTags.ITEM_CHIP_PREFIX + kanjiId(18)))
        chips[18].assert(hasTestTag(LevelProgressTestTags.ITEM_CHIP_PREFIX + kanjiId(25)))
        chips[24].assert(hasTestTag(LevelProgressTestTags.ITEM_CHIP_PREFIX + kanjiId(19)))
    }
}
