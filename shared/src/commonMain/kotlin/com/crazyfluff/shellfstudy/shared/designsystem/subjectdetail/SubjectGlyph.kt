package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.crazyfluff.shellfstudy.shared.designsystem.theme.LocalJapaneseFontFamily
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.shared.network.SubjectType

private val MinGlyphFontSize = 10.sp
// Fraction of [size] the glyph ink is allowed to occupy. The text branch uses [GlyphFontFraction]
// as its autoSize ceiling (so a single character renders at ~that fraction of the tile), and the
// image branch bounds the PNG/SVG to [GlyphInkScale] of the box. Keeping both in the same band is
// what makes an image-only radical (one with no unicode glyph) look the same size as the characters
// sitting next to it — before this the image filled the whole box and read roughly twice as large.
// [GlyphInkScale] sits a touch above the text ceiling because WaniKani's decoration PNGs carry a
// little internal padding, so the visible ink ends up comparable rather than smaller.
private const val GlyphFontFraction = 0.55f
private const val GlyphInkScale = 0.6f

/**
 * The single low-level primitive for rendering a subject's glyph: its unicode character when it
 * has one, otherwise its WaniKani-hosted PNG image (radicals like "drop" have no unicode glyph),
 * otherwise a "?" fallback. Every subject-glyph UI in the app (search results, related-subject
 * tiles, level-progress chips, the detail sheet headline) builds on this instead of re-deriving the
 * character-or-image-or-fallback logic separately.
 */
@Composable
fun SubjectGlyph(
    characters: String?,
    characterImageUrl: String?,
    subjectType: SubjectType,
    size: Dp,
    modifier: Modifier = Modifier,
    color: Color? = null,
    fallbackText: String = "?"
) {
    // The ceiling a single short glyph renders at; callers size this from a small 28dp tile chip
    // up to a large detail-sheet headline and get proportionally bigger glyphs.
    val maxFontSize = (size.value * GlyphFontFraction).sp
    val textStyle = MaterialTheme.typography.headlineSmall
    val glyphColor = color ?: subjectColor(subjectType)

    when {
        // Only height is constrained here, not width — vocabulary "characters" can be a whole word
        // (multiple glyphs), and forcing that into a fixed square wrapped it onto a second line.
        // autoSize finds the biggest font (down to MinGlyphFontSize) that fits on one line in a
        // single measure pass — a hand-rolled "shrink a bit each recomposition until it fits" loop
        // was tried first but didn't reliably converge for long strings before hitting the ellipsis
        // fallback; this is Compose's own purpose-built solution for exactly this problem.
        characters != null -> Box(modifier = modifier.height(size), contentAlignment = Alignment.Center) {
            Text(
                text = characters,
                style = textStyle.copy(fontFamily = LocalJapaneseFontFamily.current),
                color = glyphColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = MinGlyphFontSize, maxFontSize = maxFontSize)
            )
        }
        characterImageUrl != null -> Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            // These SVGs are flat monochrome line art (fill:none, a single stroke color) baked to a
            // hardcoded fallback black by SvgCssVariableInterceptor — tinting to the same
            // subject-type color the text-glyph branch above uses keeps them visible and consistent
            // across light/dark/e-ink themes instead of stuck black. Bounded to [GlyphInkScale] of
            // [size] and centered so an image-only radical matches a character glyph's visual size.
            AsyncImage(
                model = characterImageUrl,
                contentDescription = null,
                colorFilter = ColorFilter.tint(glyphColor),
                modifier = Modifier.size(size * GlyphInkScale)
            )
        }
        else -> Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            Text(
                text = fallbackText,
                style = textStyle.copy(fontSize = maxFontSize),
                color = glyphColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
