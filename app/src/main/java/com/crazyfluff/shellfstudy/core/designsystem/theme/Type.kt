package com.crazyfluff.shellfstudy.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.crazyfluff.shellfstudy.R

/**
 * Plus Jakarta Sans — a modern, rounded humanist sans-serif with a warmer, friendlier feel than
 * Android's default Roboto, closer to the typography of contemporary AI app UIs (e.g. Claude's
 * own apps). It's a variable font, so each weight below maps to the same file via FontVariation
 * rather than needing a separate static file per weight.
 */
@OptIn(ExperimentalTextApi::class)
private val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

// Material 3's default type scale, used as a baseline so every size/line-height/letter-spacing
// stays at its well-tuned default — only the font family (and our two prior custom overrides)
// changes.
private val defaults = Typography()

val Typography = Typography(
    displayLarge = defaults.displayLarge.copy(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = 0.sp
    ),
    displayMedium = defaults.displayMedium.copy(fontFamily = PlusJakartaSans),
    displaySmall = defaults.displaySmall.copy(fontFamily = PlusJakartaSans),
    headlineLarge = defaults.headlineLarge.copy(fontFamily = PlusJakartaSans),
    headlineMedium = defaults.headlineMedium.copy(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = defaults.headlineSmall.copy(fontFamily = PlusJakartaSans),
    titleLarge = defaults.titleLarge.copy(fontFamily = PlusJakartaSans),
    titleMedium = defaults.titleMedium.copy(fontFamily = PlusJakartaSans),
    titleSmall = defaults.titleSmall.copy(fontFamily = PlusJakartaSans),
    bodyLarge = defaults.bodyLarge.copy(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = defaults.bodyMedium.copy(fontFamily = PlusJakartaSans),
    bodySmall = defaults.bodySmall.copy(fontFamily = PlusJakartaSans),
    labelLarge = defaults.labelLarge.copy(fontFamily = PlusJakartaSans),
    labelMedium = defaults.labelMedium.copy(fontFamily = PlusJakartaSans),
    labelSmall = defaults.labelSmall.copy(fontFamily = PlusJakartaSans)
)
