package com.crazyfluff.shellfstudy.shared.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Holds the SRS stage palette to the 3:1 contrast guideline for UI components on each surface.
 *
 * This is the test that makes `LocalDarkTheme` load-bearing. Before it, the local was declared,
 * documented as existing "so categorical colours that don't otherwise track Material's colour scheme
 * swap in a legible dark-theme variant", provided by `ShellfStudyTheme` — and read by nothing, so
 * Master (#6A1B9A, 1.82:1) and Burned (#B71C1C, 2.61:1) were all but invisible in dark mode.
 *
 * The light surface is asserted as it actually is, not as it should be: [lightStageColoursBelowBar]
 * names the stages that are still under the bar there, so the outstanding light-theme work is
 * recorded rather than quietly passing. Changing the light palette fails this test on purpose.
 */
class SrsStageColorContrastTest {

    private val lightSurface = lightColorScheme().surface
    private val darkSurface = darkColorScheme().surface

    private data class Stage(val name: String, val light: Color, val dark: Color)

    private val stages = listOf(
        Stage("Locked", SrsStageColors.Locked, SrsStageColorsDark.Locked),
        Stage("Apprentice", SrsStageColors.Apprentice, SrsStageColorsDark.Apprentice),
        Stage("Guru", SrsStageColors.Guru, SrsStageColorsDark.Guru),
        Stage("Master", SrsStageColors.Master, SrsStageColorsDark.Master),
        Stage("Enlightened", SrsStageColors.Enlightened, SrsStageColorsDark.Enlightened),
        Stage("Burned", SrsStageColors.Burned, SrsStageColorsDark.Burned)
    )

    /**
     * The four that measure below 3:1 against the light surface (#FEF7FF): Locked 2.55:1,
     * Apprentice 2.00:1, Guru 3.00:1 and Enlightened 2.93:1. Only Master and Burned clear it there.
     *
     * Fixing these means darkening the light palette — a design change rather than a wiring fix —
     * so they are pinned rather than fixed, and the numbers are recorded so the gap is not mistaken
     * for an oversight. The dark surface turns out to be the *easier* of the two.
     */
    private val lightStageColoursBelowBar = setOf("Locked", "Apprentice", "Guru", "Enlightened")

    @Test
    fun `every dark stage colour clears the UI contrast bar on the dark surface`() {
        val below = stages.filter { contrastRatio(it.dark, darkSurface) < MIN_UI_CONTRAST }
        assertTrue(
            below.isEmpty(),
            "these are too close to the dark surface to read as a distinct UI component: " +
                below.joinToString { "${it.name}=${"%.2f".format(contrastRatio(it.dark, darkSurface))}:1" }
        )
    }

    @Test
    fun `the dark palette differs from the light palette only where the light value fails on dark`() {
        stages.forEach { stage ->
            val lightOnDarkIsFine = contrastRatio(stage.light, darkSurface) >= MIN_UI_CONTRAST
            if (lightOnDarkIsFine) {
                assertEquals(
                    stage.light, stage.dark,
                    "${stage.name} clears the bar on the dark surface already, so its dark variant " +
                        "should reuse it rather than shift the hue"
                )
            } else {
                assertTrue(
                    stage.light != stage.dark,
                    "${stage.name} is below the bar on dark and needs a real dark variant"
                )
            }
        }
    }

    @Test
    fun `the light palette is unchanged, including the stages still below the bar`() {
        val below = stages.filter { contrastRatio(it.light, lightSurface) < MIN_UI_CONTRAST }
            .map { it.name }.toSet()
        assertEquals(lightStageColoursBelowBar, below)
    }

    private companion object {
        /** WCAG 2.2 "graphical objects and user interface components" minimum. */
        const val MIN_UI_CONTRAST = 3.0

        fun contrastRatio(a: Color, b: Color): Double {
            val la = relativeLuminance(a)
            val lb = relativeLuminance(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }

        fun relativeLuminance(color: Color): Double {
            fun channel(value: Float): Double {
                val c = value.toDouble()
                return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
        }
    }
}
