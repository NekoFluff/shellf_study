package com.crazyfluff.shellfstudy.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

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

@Composable
fun ShellfStudyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default: this app has its own brand palette (see SubjectTypeColors / SrsStageColors)
    // that we don't want overridden by the device wallpaper's Material You colors.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
