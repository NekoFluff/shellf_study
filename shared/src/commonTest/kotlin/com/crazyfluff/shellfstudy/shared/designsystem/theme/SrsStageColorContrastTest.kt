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
 * Master (#6A1B9A, 1.98:1) and Burned (#B71C1C, 2.83:1) were all but invisible in dark mode.
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

    /**
     * Chasing [MIN_UI_CONTRAST] by desaturating is a real trap, and this test exists because it was
     * fallen into: the first dark variants were a lavender Master (S=0.43 against its light 0.83) and
     * a salmon Burned (S=0.49 against 0.85), which satisfy the ratio while reading as neither purple
     * nor red. The palette is meant to be vivid — its own doc says "a vivid green through vivid blue
     * ... ending in a deep red" — and staying inside the Material colour family instead keeps those
     * at 0.62 and 0.80.
     *
     * Measured against each stage's *own* light colour rather than an absolute floor, because `Locked`
     * is deliberately grey: it is the "not started yet" state, so it has no vividness to keep and a
     * fixed floor would fail it for being what it is.
     */
    @Test
    fun `the dark variants keep their light counterpart's vividness`() {
        val washedOut = stages.filter {
            saturation(it.dark) < saturation(it.light) * MIN_SATURATION_RETENTION
        }
        assertTrue(
            washedOut.isEmpty(),
            "these clear the contrast bar by desaturating, which breaks the palette's progression: " +
                washedOut.joinToString {
                    "${it.name}=${"%.2f".format(saturation(it.dark))} of ${"%.2f".format(saturation(it.light))}"
                }
        )
    }

    private companion object {
        /** WCAG 2.2 "graphical objects and user interface components" minimum. */
        const val MIN_UI_CONTRAST = 3.0

        /**
         * How much of a stage's light-palette saturation its dark variant must keep. The on-brand dark
         * variants sit at 0.75 and 0.94 of their light counterparts; the washed-out mistakes at 0.53
         * and 0.57.
         */
        const val MIN_SATURATION_RETENTION = 0.7

        /** HSV saturation, i.e. how far the colour is from grey. */
        fun saturation(color: Color): Double {
            val max = maxOf(color.red, color.green, color.blue).toDouble()
            val min = minOf(color.red, color.green, color.blue).toDouble()
            return if (max == 0.0) 0.0 else (max - min) / max
        }

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
