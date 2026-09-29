package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SvgStyleInlinerTest {

    /** WaniKani's "Death Star" radical, as files.wanikani.com serves it. */
    private val deathStar = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 1000"><defs><clipPath id="a"><path d="M0 39h1000v421H0z" class="a"/></clipPath><style>.a,.b{fill:none}.b{stroke:var(--color-text, #000);stroke-linecap:square;stroke-width:68px}</style></defs><path d="M240 940V420" class="b"/><path d="M780 300C676" class="b" style="clip-path:url(#a)"/></svg>"""

    @Test
    fun removesTheStylesheet() {
        assertFalse("<style" in inlineSvgStyles(deathStar))
    }

    @Test
    fun appliesEveryMatchingClassRuleAsAttributes() {
        val out = inlineSvgStyles(deathStar)

        assertTrue(
            """<path d="M240 940V420" class="b" fill="none" stroke="#000" stroke-linecap="square" stroke-width="68px"/>""" in out,
            out
        )
    }

    @Test
    fun resolvesCustomPropertiesToTheirFallback() {
        assertFalse("var(" in inlineSvgStyles(deathStar))
    }

    @Test
    fun anElementsOwnStyleAttributeBecomesAttributesToo() {
        val out = inlineSvgStyles(deathStar)

        assertTrue("""class="b" fill="none" stroke="#000" stroke-linecap="square" stroke-width="68px" clip-path="url(#a)"/>""" in out, out)
        assertFalse(" style=" in out, out)
    }

    @Test
    fun aLaterRuleWinsOverAnEarlierOneAndOverAPresentationAttribute() {
        val svg = """<svg><style>.x{fill:red}.x{fill:blue}</style><path fill="green" class="x"/></svg>"""

        assertEquals("""<svg><path fill="blue" class="x"/></svg>""", inlineSvgStyles(svg))
    }

    @Test
    fun theInlineStyleWinsOverTheStylesheet() {
        val svg = """<svg><style>.x{fill:red}</style><path class="x" style="fill:blue"/></svg>"""

        assertEquals("""<svg><path class="x" fill="blue"/></svg>""", inlineSvgStyles(svg))
    }

    @Test
    fun ignoresRulesWithSelectorsItDoesNotUnderstand() {
        val svg = """<svg><style>path{fill:red}.x{stroke:blue}</style><path class="x"/></svg>"""

        assertEquals("""<svg><path class="x" stroke="blue"/></svg>""", inlineSvgStyles(svg))
    }

    @Test
    fun leavesAnSvgWithoutStylesUntouched() {
        val svg = """<svg viewBox="0 0 10 10"><path d="M0 0h10" stroke="#000"/></svg>"""

        assertEquals(svg, inlineSvgStyles(svg))
    }
}
