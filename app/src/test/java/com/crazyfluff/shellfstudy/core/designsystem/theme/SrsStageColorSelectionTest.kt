package com.crazyfluff.shellfstudy.core.designsystem.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.designsystem.theme.EinkStageColors
import com.crazyfluff.shellfstudy.shared.designsystem.theme.LocalDarkTheme
import com.crazyfluff.shellfstudy.shared.designsystem.theme.LocalEinkTheme
import com.crazyfluff.shellfstudy.shared.designsystem.theme.SrsStageColors
import com.crazyfluff.shellfstudy.shared.designsystem.theme.SrsStageColorsDark
import com.crazyfluff.shellfstudy.shared.designsystem.theme.srsStageColor
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Covers which palette `srsStageColor` selects under each theme, which the pure-math
 * `SrsStageColorContrastTest` in commonTest cannot: that one proves the dark palette's colours are
 * legible, this one proves they are actually reached.
 *
 * `LocalDarkTheme` previously had no reader at all, so a correct dark palette would have changed
 * nothing. Robolectric is only standing up a composition here and reading a returned `Color`, not
 * asserting on pixels, so CLAUDE.md's "verify visual effects on a device" caveat does not apply.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SrsStageColorSelectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `dark theme resolves every stage to the dark palette`() {
        val resolved = resolveAll(eink = false, dark = true)

        assertThat(resolved).isEqualTo(SrsStage.entries.associateWith { expectedDark(it) })
        // The two the palette exists for, called out so a regression names itself.
        assertThat(resolved[SrsStage.MASTER]).isEqualTo(SrsStageColorsDark.Master)
        assertThat(resolved[SrsStage.BURNED]).isEqualTo(SrsStageColorsDark.Burned)
    }

    @Test
    fun `light theme resolves every stage to the light palette`() {
        val resolved = resolveAll(eink = false, dark = false)

        assertThat(resolved).isEqualTo(SrsStage.entries.associateWith { expectedLight(it) })
    }

    @Test
    fun `e-ink wins over dark, since its grayscale ramp is the legibility mechanism there`() {
        val resolved = resolveAll(eink = true, dark = true)

        // ShellfStudyTheme cannot produce this combination (it provides dark as `darkTheme && !isEink`),
        // but the precedence is worth pinning rather than leaving to argument order.
        assertThat(resolved[SrsStage.MASTER]).isEqualTo(EinkStageColors.Master)
        assertThat(resolved[SrsStage.BURNED]).isEqualTo(EinkStageColors.Burned)
    }

    private fun resolveAll(eink: Boolean, dark: Boolean): Map<SrsStage, Color> {
        var captured: Map<SrsStage, Color>? = null
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalEinkTheme provides eink,
                LocalDarkTheme provides dark
            ) {
                captured = SrsStage.entries.associateWith { srsStageColor(it) }
            }
        }
        return requireNotNull(captured) { "the composition did not run" }
    }

    private fun expectedLight(stage: SrsStage): Color = when (stage) {
        SrsStage.LOCKED -> SrsStageColors.Locked
        SrsStage.APPRENTICE_1, SrsStage.APPRENTICE_2, SrsStage.APPRENTICE_3, SrsStage.APPRENTICE_4 ->
            SrsStageColors.Apprentice
        SrsStage.GURU_1, SrsStage.GURU_2 -> SrsStageColors.Guru
        SrsStage.MASTER -> SrsStageColors.Master
        SrsStage.ENLIGHTENED -> SrsStageColors.Enlightened
        SrsStage.BURNED -> SrsStageColors.Burned
    }

    private fun expectedDark(stage: SrsStage): Color = when (stage) {
        SrsStage.LOCKED -> SrsStageColorsDark.Locked
        SrsStage.APPRENTICE_1, SrsStage.APPRENTICE_2, SrsStage.APPRENTICE_3, SrsStage.APPRENTICE_4 ->
            SrsStageColorsDark.Apprentice
        SrsStage.GURU_1, SrsStage.GURU_2 -> SrsStageColorsDark.Guru
        SrsStage.MASTER -> SrsStageColorsDark.Master
        SrsStage.ENLIGHTENED -> SrsStageColorsDark.Enlightened
        SrsStage.BURNED -> SrsStageColorsDark.Burned
    }
}
