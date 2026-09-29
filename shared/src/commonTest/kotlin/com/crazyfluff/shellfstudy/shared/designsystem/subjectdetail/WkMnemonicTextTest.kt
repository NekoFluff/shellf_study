package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.parseWkMarkup
import com.crazyfluff.shellfstudy.shared.designsystem.theme.SubjectTypeColors
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class WkMnemonicTextTest {

    @Test
    fun `plain text has no spans`() {
        val result = parseWkMarkup("no markup here")

        assertEquals("no markup here", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `radical tag colors its enclosed text and strips the tag itself`() {
        val result = parseWkMarkup("<radical>drop</radical> means water")

        assertEquals("drop means water", result.text)
        val span = result.spanStyles.single()
        assertEquals("drop", result.text.substring(span.start, span.end))
        assertEquals(SubjectTypeColors.Radical, span.item.color)
    }

    @Test
    fun `kanji and vocabulary tags use their own type colors`() {
        val kanji = parseWkMarkup("<kanji>水</kanji>")
        assertEquals(SubjectTypeColors.Kanji, kanji.spanStyles.single().item.color)

        val vocab = parseWkMarkup("<vocabulary>水道</vocabulary>")
        assertEquals(SubjectTypeColors.Vocabulary, vocab.spanStyles.single().item.color)

        val kanaVocab = parseWkMarkup("<kana_vocabulary>みず</kana_vocabulary>")
        assertEquals(SubjectTypeColors.Vocabulary, kanaVocab.spanStyles.single().item.color)
    }

    @Test
    fun `reading tag is italic rather than colored`() {
        val result = parseWkMarkup("<reading>mizu</reading>")

        val span = result.spanStyles.single()
        assertEquals(FontStyle.Italic, span.item.fontStyle)
        assertFalse(span.item.color.isSpecified)
    }

    @Test
    fun `ja tag is medium weight rather than colored`() {
        val result = parseWkMarkup("<ja>水</ja>")

        val span = result.spanStyles.single()
        assertEquals(FontWeight.Medium, span.item.fontWeight)
    }

    @Test
    fun `unknown tags are stripped silently without leaking raw markup or crashing`() {
        val result = parseWkMarkup("before <mystery>middle</mystery> after")

        assertEquals("before middle after", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `adjacent tags do not bleed styling into each other`() {
        val result = parseWkMarkup("<radical>drop</radical> and <kanji>water</kanji>")

        assertEquals(2, result.spanStyles.size)
        val radicalSpan = result.spanStyles.first { result.text.substring(it.start, it.end) == "drop" }
        val kanjiSpan = result.spanStyles.first { result.text.substring(it.start, it.end) == "water" }
        assertEquals(SubjectTypeColors.Radical, radicalSpan.item.color)
        assertEquals(SubjectTypeColors.Kanji, kanjiSpan.item.color)
    }

    @Test
    fun `bare url becomes a clickable link and is not left as plain text`() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val result = parseWkMarkup("check this out: $url")

        assertEquals("check this out: $url", result.text)
        val annotation = result.getLinkAnnotations(0, result.text.length).single()
        assertEquals(url, result.text.substring(annotation.start, annotation.end))
        assertEquals(url, (annotation.item as LinkAnnotation.Url).url)
    }

    @Test
    fun `url alongside semantic tags keeps both the link and the tag styling`() {
        val url = "https://youtu.be/abc123"
        val result = parseWkMarkup("<radical>drop</radical> like this video $url")

        assertEquals("drop like this video $url", result.text)
        val linkAnnotation = result.getLinkAnnotations(0, result.text.length).single()
        assertEquals(url, (linkAnnotation.item as LinkAnnotation.Url).url)
        val radicalSpan = result.spanStyles.single()
        assertEquals("drop", result.text.substring(radicalSpan.start, radicalSpan.end))
    }

    @Test
    fun `html anchor tag becomes a clickable link using its anchor text with no raw markup leaking`() {
        val href = "https://www.youtube.com/watch?v=XaCrQL_8eMY"
        val result = parseWkMarkup(
            "you're the <a href=\"$href\" target=\"_blank\">Whole. Damn. Meal.</a> apparently"
        )

        assertEquals("you're the Whole. Damn. Meal. apparently", result.text)
        val annotation = result.getLinkAnnotations(0, result.text.length).single()
        assertEquals("Whole. Damn. Meal.", result.text.substring(annotation.start, annotation.end))
        assertEquals(href, (annotation.item as LinkAnnotation.Url).url)
    }
}
