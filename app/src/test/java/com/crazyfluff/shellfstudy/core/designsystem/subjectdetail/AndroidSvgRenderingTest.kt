package com.crazyfluff.shellfstudy.core.designsystem.subjectdetail

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caverock.androidsvg.SVG
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.inlineSvgStyles
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Renders a WaniKani radical through AndroidSVG — what Coil's SVG decoder uses on Android — the
 * counterpart of shared's iOS `SkiaSvgRenderingTest`. AndroidSVG does apply `<style>`, but has no
 * `var()` support, and the rule holding `stroke:var(--color-text, #000)` is dropped whole: every
 * path is left with `fill:none` and no stroke, and the radical renders as nothing at all.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidSvgRenderingTest {

    private val deathStar = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 1000"><defs><clipPath id="a"><path d="M0 39h1000v421H0z" class="a"/></clipPath><clipPath id="b"><path d="M240 420h220v520H240z" class="a"/></clipPath><style>.a,.b{fill:none}.b{stroke:var(--color-text, #000);stroke-linecap:square;stroke-miterlimit:2;stroke-width:68px}</style></defs><path d="M240 940V420h220v440c0 40-40 80-80 80M340 300h320" class="b"/><path d="M780 300C676 244.9 580 169 500 60c-80 109-176 184.9-280 240" class="b" style="clip-path:url(#a)"/><path d="M600 780V460M760 420v440c0 40-40 80-80 80h-60" class="b"/><g style="clip-path:url(#b)"><path d="M460 740H240M460 580H240" class="b"/></g></svg>"""

    private fun render(svg: String): Bitmap {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        SVG.getFromString(svg).apply {
            documentWidth = 200f
            documentHeight = 200f
        }.renderToCanvas(Canvas(bitmap))
        return bitmap
    }

    private fun Bitmap.paintedPixels(): Int {
        val pixels = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
        return pixels.count { it != Color.TRANSPARENT }
    }

    @Test
    fun `as delivered the radical renders as nothing`() {
        assertThat(render(deathStar).paintedPixels()).isEqualTo(0)
    }

    @Test
    fun `inlined the radical renders as strokes around an empty box`() {
        val bitmap = render(inlineSvgStyles(deathStar))

        assertThat(bitmap.getPixel(70, 100)).isEqualTo(Color.TRANSPARENT)
        assertThat(bitmap.getPixel(48, 100)).isNotEqualTo(Color.TRANSPARENT)
    }
}
