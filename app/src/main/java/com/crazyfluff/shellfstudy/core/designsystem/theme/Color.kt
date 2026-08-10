package com.crazyfluff.shellfstudy.core.designsystem.theme

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

/** WaniKani's own SRS-stage color convention. */
object SrsStageColors {
    val Apprentice = Color(0xFFDD0093)
    val Guru = Color(0xFF882D9E)
    val Master = Color(0xFF294DDB)
    val Enlightened = Color(0xFF0093DD)
    val Burned = Color(0xFF434343)
    val Locked = Color(0xFF9E9E9E)
}
