package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeblioPitchAccentParserTest {

    private val parser = WeblioPitchAccentParser()

    @Test
    fun parseExtractsReadingAndPitchNumberFromANetDicHeadHeading() {
        val html = """<div class="NetDicHead">ミズ<span style="font-size:75%;">［0］</span></div>"""

        val result = parser.parse(html)

        assertEquals(listOf(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0)), result)
    }

    @Test
    fun parseExtractsPartOfSpeechVariantsFromTheSiblingNetDicBodyWhenTheHeadingHasNoPitchNumber() {
        val html = """
            <div class="NetDicHead">タベル</div>
            <div class="NetDicBody">
                <span class="wrap"><span data-txt-len="1" style="background-color:black;">v</span></span><span class="variant">（動詞）［2］</span>
            </div>
        """.trimIndent()

        val result = parser.parse(html)

        assertEquals(listOf(PitchAccent(reading = "タベル", partOfSpeech = "動詞", pitchNumber = 2)), result)
    }

    @Test
    fun parseStripsStrayNonKanaCharactersEmbeddedAheadOfThePitchMarker() {
        val html = """<div class="NetDicHead">ミズ1<span style="font-size:75%;">［1］</span></div>"""

        val result = parser.parse(html)

        assertEquals(listOf(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 1)), result)
    }

    @Test
    fun parseReturnsAnEmptyListWhenThePageHasNoNetDicHeadEntries() {
        assertTrue(parser.parse("<html><body>not a dictionary page</body></html>").isEmpty())
    }

    @Test
    fun parseSkipsANetDicBodyVariantWhenItsBackgroundColorBlackSpanIsMissing() {
        val html = """
            <div class="NetDicHead">タベル</div>
            <div class="NetDicBody">
                <span class="variant">（動詞）［2］</span>
            </div>
        """.trimIndent()

        assertTrue(parser.parse(html).isEmpty())
    }
}
