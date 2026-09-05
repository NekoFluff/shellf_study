package com.crazyfluff.shellfstudy.shared.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.crazyfluff.shellfstudy.shared.generated.resources.Res
import com.crazyfluff.shellfstudy.shared.generated.resources.noto_sans_jp
import com.crazyfluff.shellfstudy.shared.generated.resources.plus_jakarta_sans
import org.jetbrains.compose.resources.Font

/**
 * Plus Jakarta Sans — a modern, rounded humanist sans-serif with a warmer, friendlier feel than
 * Android's default Roboto, closer to the typography of contemporary AI app UIs (e.g. Claude's
 * own apps). It's a variable font; loading it once per [FontWeight] here (rather than relying on
 * FontVariation axis settings, which Compose Multiplatform's resource-backed Font() doesn't expose
 * the way the Android-only androidx.compose.ui.text.font.Font(resId, variationSettings) overload
 * did) is a deliberate simplification for the multiplatform port — see appTypography's doc comment.
 */
@Composable
private fun plusJakartaSans(): FontFamily = FontFamily(
    Font(Res.font.plus_jakarta_sans, FontWeight.Normal),
    Font(Res.font.plus_jakarta_sans, FontWeight.Medium),
    Font(Res.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(Res.font.plus_jakarta_sans, FontWeight.Bold)
)

/**
 * Noto Sans JP — bundled so kanji/kana render identically on both platforms instead of falling
 * back to whichever CJK font each OS happens to ship (Noto Sans CJK on Android, Hiragino Sans on
 * iOS, or an OEM substitute on some Android skins). Only the Regular weight is bundled: every
 * Japanese-content call site in this app uses a 400-weight type slot, so there's nothing that
 * needs Bold today. See [LocalJapaneseFontFamily].
 */
@Composable
fun notoSansJp(): FontFamily = FontFamily(
    Font(Res.font.noto_sans_jp, FontWeight.Normal)
)

/**
 * Material 3's default type scale, used as a baseline so every size/line-height/letter-spacing
 * stays at its well-tuned default — only the font family (and our two prior custom overrides)
 * changes. A `@Composable` function rather than a top-level `val` because Compose Multiplatform's
 * resource-backed [Font] (unlike Android's resource-ID-based one) can only be loaded within a
 * composition.
 */
@Composable
fun appTypography(): Typography {
    val fontFamily = plusJakartaSans()
    val defaults = Typography()
    return Typography(
        displayLarge = defaults.displayLarge.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 48.sp,
            lineHeight = 56.sp,
            letterSpacing = 0.sp
        ),
        displayMedium = defaults.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = defaults.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = defaults.headlineMedium.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            letterSpacing = 0.sp
        ),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = defaults.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = defaults.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = defaults.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = defaults.bodyLarge.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp
        ),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = defaults.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = defaults.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = defaults.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = defaults.labelSmall.copy(fontFamily = fontFamily)
    )
}
