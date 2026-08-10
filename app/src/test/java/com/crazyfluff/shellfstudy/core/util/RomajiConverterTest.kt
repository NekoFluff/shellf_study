package com.crazyfluff.shellfstudy.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RomajiConverterTest {

    @Test
    fun `converts basic vowels`() {
        assertThat(RomajiConverter.toHiragana("aiueo")).isEqualTo("あいうえお")
    }

    @Test
    fun `converts simple words`() {
        assertThat(RomajiConverter.toHiragana("mizu")).isEqualTo("みず")
        assertThat(RomajiConverter.toHiragana("neko")).isEqualTo("ねこ")
        assertThat(RomajiConverter.toHiragana("sakura")).isEqualTo("さくら")
    }

    @Test
    fun `converts youon (palatalized) syllables`() {
        assertThat(RomajiConverter.toHiragana("kyou")).isEqualTo("きょう")
        assertThat(RomajiConverter.toHiragana("shashou")).isEqualTo("しゃしょう")
        assertThat(RomajiConverter.toHiragana("ryokou")).isEqualTo("りょこう")
    }

    @Test
    fun `converts sokuon (doubled consonant) to small tsu`() {
        assertThat(RomajiConverter.toHiragana("kitte")).isEqualTo("きって")
        assertThat(RomajiConverter.toHiragana("gakkou")).isEqualTo("がっこう")
        assertThat(RomajiConverter.toHiragana("chotto")).isEqualTo("ちょっと")
    }

    @Test
    fun `converts n before a consonant to standalone n-kana`() {
        assertThat(RomajiConverter.toHiragana("kantan")).isEqualTo("かんたん")
        assertThat(RomajiConverter.toHiragana("sensei")).isEqualTo("せんせい")
    }

    @Test
    fun `converts double n to standalone n-kana followed by the next syllable`() {
        // Phonetic conversion only — the real greeting is spelled with the historical is/wa
        // exception (こんにちは), which needs dictionary lookup, not phoneme-by-phoneme rules.
        // Not a concern for WaniKani readings, which are single kanji/vocab words, not particles.
        assertThat(RomajiConverter.toHiragana("konnichiwa")).isEqualTo("こんにちわ")
    }

    @Test
    fun `leaves an incomplete trailing consonant unconverted`() {
        assertThat(RomajiConverter.toHiragana("k")).isEqualTo("k")
        assertThat(RomajiConverter.toHiragana("mizuk")).isEqualTo("みずk")
    }

    @Test
    fun `passes already-hiragana text through unchanged`() {
        assertThat(RomajiConverter.toHiragana("みず")).isEqualTo("みず")
    }

    @Test
    fun `handles shi chi tsu fu alternate spellings`() {
        assertThat(RomajiConverter.toHiragana("shi")).isEqualTo("し")
        assertThat(RomajiConverter.toHiragana("si")).isEqualTo("し")
        assertThat(RomajiConverter.toHiragana("chi")).isEqualTo("ち")
        assertThat(RomajiConverter.toHiragana("tsu")).isEqualTo("つ")
        assertThat(RomajiConverter.toHiragana("fu")).isEqualTo("ふ")
    }

    @Test
    fun `converts voiced (dakuten) and semi-voiced (handakuten) rows`() {
        assertThat(RomajiConverter.toHiragana("gohan")).isEqualTo("ごはん")
        assertThat(RomajiConverter.toHiragana("zenbu")).isEqualTo("ぜんぶ")
        assertThat(RomajiConverter.toHiragana("happa")).isEqualTo("はっぱ")
    }

    @Test
    fun `empty input returns empty output`() {
        assertThat(RomajiConverter.toHiragana("")).isEqualTo("")
    }
}
