package com.crazyfluff.shellfstudy.shared.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
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

/** Returns [einkValue] under the e-ink theme, [default] otherwise. */
@Composable
fun themeAwareColor(default: Color, einkValue: Color): Color =
    if (LocalEinkTheme.current) einkValue else default

@Composable
fun ShellfStudyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val isEink = themeMode == ThemeMode.EINK
    val colorScheme = when {
        isEink -> EinkColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalEinkTheme provides isEink, LocalDarkTheme provides (darkTheme && !isEink)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = appTypography(),
            content = content
        )
    }
}
