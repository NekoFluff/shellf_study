package com.crazyfluff.shellfstudy.core.designsystem.subjectdetail

import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.crazyfluff.shellfstudy.core.designsystem.theme.SubjectTypeColors
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
}
