package com.crazyfluff.shellfstudy.shared.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.crazyfluff.shellfstudy.shared.data.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = VocabularyLight,
    secondary = KanjiLight,
    tertiary = RadicalLight
)

private val LightColorScheme = lightColorScheme(
    primary = SubjectTypeColors.Vocabulary,
    secondary = SubjectTypeColors.Kanji,
    tertiary = SubjectTypeColors.Radical
)

// Hand-authored grayscale scheme for e-ink panels (Boox etc.) — flat colors instead of tonal
// elevation, since neither shadows nor saturated hues render well on those displays.
// primary/secondary/tertiary mirror the light scheme's Vocabulary/Kanji/Radical mapping so that
// subjectColor() can derive its eink values from the scheme rather than hardcoding them.
private val EinkColorScheme = lightColorScheme(
    primary = EinkSubjectColors.Vocabulary,
    onPrimary = Color.White,
    secondary = EinkSubjectColors.Kanji,
    onSecondary = Color.White,
    tertiary = EinkSubjectColors.Radical,
    onTertiary = Color.White,
    background = EinkPalette.Background,
    onBackground = Color.Black,
    surface = EinkPalette.Surface,
    onSurface = Color.Black,
    surfaceVariant = EinkPalette.SurfaceVariant,
    onSurfaceVariant = EinkPalette.OnSurfaceVariant,
    outline = EinkPalette.Outline,
    error = Color.Black,
    onError = Color.White
)

/** Whether the e-ink theme is active — lets categorical colors (subject type, SRS stage, pitch accent) fall back to grayscale. */
val LocalEinkTheme = staticCompositionLocalOf { false }

/** Whether the dark color scheme is active — lets categorical colors that don't otherwise track
 *  Material's color scheme (e.g. [SrsStageColors.Burned]) swap in a legible dark-theme variant. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** The font used for Japanese subject content (kanji, kana, readings) — Noto Sans JP, so it
 *  renders identically on Android and iOS rather than falling back to whatever CJK font each OS
 *  ships. Defaults to [FontFamily.Default] outside of [ShellfStudyTheme] (e.g. previews/tests). */
val LocalJapaneseFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

/**
 * Resolves a colour that the light palette defines but the other two themes cannot reuse.
 *
 * [darkValue] deliberately defaults to [default] rather than to [einkValue]: an e-ink stand-in is
 * usually a near-black grayscale (see `CorrectAnswerColorDark`, 1.37:1 on the dark surface), so
 * treating it as the dark value would be a regression rather than a fix. Call sites that genuinely
 * need a separate dark colour pass one; the rest keep their light value in dark mode and stay
 * readable because most of this palette already clears the 3:1 UI-component guideline on both
 * surfaces (`SrsStageColorContrastTest` pins which ones do).
 */
@Composable
fun themeAwareColor(default: Color, einkValue: Color, darkValue: Color = default): Color = when {
    LocalEinkTheme.current -> einkValue
    LocalDarkTheme.current -> darkValue
    else -> default
}

@Composable
fun ShellfStudyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isEink = themeMode == ThemeMode.EINK
    // Resolved from the mode rather than taken as a separate `darkTheme` parameter. With a parameter,
    // the app's two roots disagreed about it — Android passed the mode's own answer, iOS left it to
    // the system — so choosing Dark on iOS while the system was light rendered light.
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT, ThemeMode.EINK -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = when {
        isEink -> EinkColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(
        LocalEinkTheme provides isEink,
        LocalDarkTheme provides (darkTheme && !isEink),
        LocalJapaneseFontFamily provides notoSansJp()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = appTypography(),
            content = content
        )
    }
}
