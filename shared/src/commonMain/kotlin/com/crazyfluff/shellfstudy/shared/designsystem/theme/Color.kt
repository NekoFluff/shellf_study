package com.crazyfluff.shellfstudy.shared.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * The app's three main colors — every other color (buttons, dashboard cards, review-screen
 * accents) derives from these so the palette reads as one consistent family rather than several
 * near-but-not-quite-matching shades.
 */
object SubjectTypeColors {
    val Radical = Color(0xFF0093DD)
    val Kanji = Color(0xFFDD0093)
    val Vocabulary = Color(0xFFA020F0)
}

// Lighter tints of the same three hues, for the dark color scheme.
val RadicalLight = Color(0xFFB3D9FF)
val KanjiLight = Color(0xFFFFB3E6)
val VocabularyLight = Color(0xFFE3B3FF)

/** SRS stage colors — a cool-to-warm progression from a vivid green through vivid blue,
 *  then Material journey purple and deep orange, ending in a deep red for Burned. */
object SrsStageColors {
    val Apprentice = Color(0xFF2ECC71)
    val Guru = Color(0xFF3498DB)
    val Master = Color(0xFF6A1B9A)
    val Enlightened = Color(0xFFEF6C00)
    val Burned = Color(0xFFB71C1C)
    val Locked = Color(0xFF9E9E9E)
}

/** Accent for "Correct!" feedback text and success icons in quiz flows. */
val CorrectAnswerColor = SubjectTypeColors.Radical
val CorrectAnswerColorDark = Color(0xFF2E2E2E)

/** Smouldering Durtles' pitch-accent-pattern color convention, kept for a familiar look. */
object PitchAccentColors {
    val Heiban = Color(0xFFD20CA3)
    val Atamadaka = Color(0xFFEA9316)
    val Nakadaka = Color(0xFF27A2FF)
    val Odaka = Color(0xFF0CD24D)
}

/**
 * Grayscale surfaces for the e-ink theme (Boox and similar panels). Surface is a shade off
 * background — rather than an identical white — so cards read as distinct blocks without relying
 * on elevation shadows, which don't render on e-ink.
 */
object EinkPalette {
    val Background = Color(0xFFFFFFFF)
    val Surface = Color(0xFFF5F5F5)
    val SurfaceVariant = Color(0xFFE0E0E0)
    val Outline = Color(0xFF757575)
    val OnSurfaceVariant = Color(0xFF424242)
}

/** Extra grayscale slots for the 6-entry leaderboard / race-chart user palette (positions 4–6). */
object EinkExtraColors {
    val Slot4 = Color(0xFFAAAAAA)
    val Slot5 = Color(0xFFBBBBBB)
    val Slot6 = Color(0xFFCCCCCC)
}

/** Grayscale stand-ins for [SubjectTypeColors], distinguishable by lightness alone. */
object EinkSubjectColors {
    val Radical = Color(0xFF000000)
    val Kanji = Color(0xFF4D4D4D)
    val Vocabulary = Color(0xFF8A8A8A)
}

/**
 * Grayscale stand-ins for [SrsStageColors]. A rough Locked-to-Burned light-to-dark ramp — exact
 * identification always comes from the adjacent label/count text, not the shade itself.
 */
object EinkStageColors {
    val Locked = Color(0xFFBDBDBD)
    val Apprentice = Color(0xFF8A8A8A)
    val Guru = Color(0xFF6B6B6B)
    val Master = Color(0xFF4D4D4D)
    val Enlightened = Color(0xFF2E2E2E)
    val Burned = Color(0xFF000000)
}
