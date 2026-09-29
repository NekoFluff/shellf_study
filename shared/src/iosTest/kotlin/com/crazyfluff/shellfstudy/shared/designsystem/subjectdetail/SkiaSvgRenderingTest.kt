package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Data
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM

/**
 * Renders a WaniKani radical through Skia's SVG module — what Coil's SVG decoder uses on iOS.
 *
 * Skia does not apply `<style>` stylesheets, so as delivered every path falls back to the SVG default
 * of a solid black fill and no stroke: the radical's outlines come out as filled blobs. Inlined, they
 * are strokes around an empty interior.
 */
class SkiaSvgRenderingTest {

    /** WaniKani's "Death Star" radical, as files.wanikani.com serves it. */
    private val deathStar = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 1000"><defs><clipPath id="a"><path d="M0 39h1000v421H0z" class="a"/></clipPath><clipPath id="b"><path d="M240 420h220v520H240z" class="a"/></clipPath><style>.a,.b{fill:none}.b{stroke:var(--color-text, #000);stroke-linecap:square;stroke-miterlimit:2;stroke-width:68px}</style></defs><path d="M240 940V420h220v440c0 40-40 80-80 80M340 300h320" class="b"/><path d="M780 300C676 244.9 580 169 500 60c-80 109-176 184.9-280 240" class="b" style="clip-path:url(#a)"/><path d="M600 780V460M760 420v440c0 40-40 80-80 80h-60" class="b"/><g style="clip-path:url(#b)"><path d="M460 740H240M460 580H240" class="b"/></g></svg>"""

    // Inside the box drawn by "M240 940V420h220v440…", between its 580 and 740 crossbars — in
    // viewBox units (350, 500), so (70, 100) on a 200px canvas.
    private val insideTheBox = 70 to 100

    private fun colorAt(svg: String, point: Pair<Int, Int>): Int {
        val size = 200
        val surface = Surface.makeRasterN32Premul(size, size)
        surface.canvas.clear(Color.TRANSPARENT)
        SVGDOM(Data.makeFromBytes(svg.encodeToByteArray())).apply {
            setContainerSize(size.toFloat(), size.toFloat())
            render(surface.canvas)
        }
        val bitmap = Bitmap().apply { allocN32Pixels(size, size) }
        surface.makeImageSnapshot().readPixels(bitmap, 0, 0)
        return bitmap.getColor(point.first, point.second)
    }

    @Test
    fun asDeliveredTheRadicalRendersAsAFilledBlob() {
        assertNotEquals(Color.TRANSPARENT, colorAt(deathStar, insideTheBox))
    }

    @Test
    fun inlinedTheRadicalRendersAsStrokesAroundAnEmptyBox() {
        val svg = inlineSvgStyles(deathStar)

        assertEquals(Color.TRANSPARENT, colorAt(svg, insideTheBox))
        // …and the box's left edge, stroked at x = 240, is drawn.
        assertNotEquals(Color.TRANSPARENT, colorAt(svg, 48 to 100))
    }
}
