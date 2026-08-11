package com.crazyfluff.shellfstudy.core.data

import com.crazyfluff.shellfstudy.core.data.model.PitchAccent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WeblioPitchAccentParserTest {

    private val parser = WeblioPitchAccentParser()

    @Test
    fun `parse extracts reading and pitch number from a NetDicHead heading`() {
        val html = """<div class="NetDicHead">ミズ<span style="font-size:75%;">［0］</span></div>"""

        val result = parser.parse(html)

        assertThat(result).containsExactly(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0))
    }

    @Test
    fun `parse extracts part-of-speech variants from the sibling NetDicBody when the heading has no pitch number`() {
        val html = """
            <div class="NetDicHead">タベル</div>
            <div class="NetDicBody">
                <span class="wrap"><span data-txt-len="1" style="background-color:black;">v</span></span><span class="variant">（動詞）［2］</span>
            </div>
        """.trimIndent()

        val result = parser.parse(html)

        assertThat(result).containsExactly(PitchAccent(reading = "タベル", partOfSpeech = "動詞", pitchNumber = 2))
    }

    @Test
    fun `parse strips stray non-kana characters embedded ahead of the pitch marker`() {
        val html = """<div class="NetDicHead">ミズ1<span style="font-size:75%;">［1］</span></div>"""

        val result = parser.parse(html)

        assertThat(result).containsExactly(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 1))
    }

    @Test
    fun `parse returns an empty list when the page has no NetDicHead entries`() {
        assertThat(parser.parse("<html><body>not a dictionary page</body></html>")).isEmpty()
    }

    @Test
    fun `parse skips a NetDicBody variant when its background-color-black span is missing`() {
        val html = """
            <div class="NetDicHead">タベル</div>
            <div class="NetDicBody">
                <span class="variant">（動詞）［2］</span>
            </div>
        """.trimIndent()

        assertThat(parser.parse(html)).isEmpty()
    }
}
