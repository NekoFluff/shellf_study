package com.crazyfluff.shellfstudy.core.designsystem.subjectdetail

import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.parseWkMarkup
import com.crazyfluff.shellfstudy.shared.designsystem.theme.SubjectTypeColors
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WkMnemonicTextTest {

    @Test
    fun `plain text has no spans`() {
        val result = parseWkMarkup("no markup here")

        assertThat(result.text).isEqualTo("no markup here")
        assertThat(result.spanStyles).isEmpty()
    }

    @Test
    fun `radical tag colors its enclosed text and strips the tag itself`() {
        val result = parseWkMarkup("<radical>drop</radical> means water")

        assertThat(result.text).isEqualTo("drop means water")
        val span = result.spanStyles.single()
        assertThat(result.text.substring(span.start, span.end)).isEqualTo("drop")
        assertThat(span.item.color).isEqualTo(SubjectTypeColors.Radical)
    }

    @Test
    fun `kanji and vocabulary tags use their own type colors`() {
        val kanji = parseWkMarkup("<kanji>水</kanji>")
        assertThat(kanji.spanStyles.single().item.color).isEqualTo(SubjectTypeColors.Kanji)

        val vocab = parseWkMarkup("<vocabulary>水道</vocabulary>")
        assertThat(vocab.spanStyles.single().item.color).isEqualTo(SubjectTypeColors.Vocabulary)

        val kanaVocab = parseWkMarkup("<kana_vocabulary>みず</kana_vocabulary>")
        assertThat(kanaVocab.spanStyles.single().item.color).isEqualTo(SubjectTypeColors.Vocabulary)
    }

    @Test
    fun `reading tag is italic, not colored`() {
        val result = parseWkMarkup("<reading>mizu</reading>")

        val span = result.spanStyles.single()
        assertThat(span.item.fontStyle).isEqualTo(FontStyle.Italic)
        assertThat(span.item.color.isSpecified).isFalse()
    }

    @Test
    fun `ja tag is medium weight, not colored`() {
        val result = parseWkMarkup("<ja>水</ja>")

        val span = result.spanStyles.single()
        assertThat(span.item.fontWeight).isEqualTo(FontWeight.Medium)
    }

    @Test
    fun `unknown tags are stripped silently without leaking raw markup or crashing`() {
        val result = parseWkMarkup("before <mystery>middle</mystery> after")

        assertThat(result.text).isEqualTo("before middle after")
        assertThat(result.spanStyles).isEmpty()
    }

    @Test
    fun `adjacent tags do not bleed styling into each other`() {
        val result = parseWkMarkup("<radical>drop</radical> and <kanji>water</kanji>")

        assertThat(result.spanStyles).hasSize(2)
        val radicalSpan = result.spanStyles.first { result.text.substring(it.start, it.end) == "drop" }
        val kanjiSpan = result.spanStyles.first { result.text.substring(it.start, it.end) == "water" }
        assertThat(radicalSpan.item.color).isEqualTo(SubjectTypeColors.Radical)
        assertThat(kanjiSpan.item.color).isEqualTo(SubjectTypeColors.Kanji)
    }

    @Test
    fun `bare url becomes a clickable link and is not left as plain text`() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val result = parseWkMarkup("check this out: $url")

        assertThat(result.text).isEqualTo("check this out: $url")
        val annotation = result.getLinkAnnotations(0, result.text.length).single()
        assertThat(result.text.substring(annotation.start, annotation.end)).isEqualTo(url)
        assertThat((annotation.item as LinkAnnotation.Url).url).isEqualTo(url)
    }

    @Test
    fun `url alongside semantic tags keeps both the link and the tag styling`() {
        val url = "https://youtu.be/abc123"
        val result = parseWkMarkup("<radical>drop</radical> like this video $url")

        assertThat(result.text).isEqualTo("drop like this video $url")
        val linkAnnotation = result.getLinkAnnotations(0, result.text.length).single()
        assertThat((linkAnnotation.item as LinkAnnotation.Url).url).isEqualTo(url)
        val radicalSpan = result.spanStyles.single()
        assertThat(result.text.substring(radicalSpan.start, radicalSpan.end)).isEqualTo("drop")
    }

    @Test
    fun `html anchor tag becomes a clickable link using its anchor text, with no raw markup leaking`() {
        val href = "https://www.youtube.com/watch?v=XaCrQL_8eMY"
        val result = parseWkMarkup(
            "you're the <a href=\"$href\" target=\"_blank\">Whole. Damn. Meal.</a> apparently"
        )

        assertThat(result.text).isEqualTo("you're the Whole. Damn. Meal. apparently")
        val annotation = result.getLinkAnnotations(0, result.text.length).single()
        assertThat(result.text.substring(annotation.start, annotation.end)).isEqualTo("Whole. Damn. Meal.")
        assertThat((annotation.item as LinkAnnotation.Url).url).isEqualTo(href)
    }
}
