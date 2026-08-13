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
    fun `double n before a vowel or y forces standalone n-kana instead of merging into the next syllable`() {
        // Deliberate tradeoff: doubling "n" is the other standard escape for ん directly before a
        // vowel/y (alongside "n'"), so "nn" no longer reads as ん followed by a な/に/ぬ/ね/の-row
        // syllable — even for real words that happen to contain that pattern naturally, like 三人
        // ("sannin") or こんにちは ("konnichiwa"). Use "n'i"/"sanni'n"-style apostrophes if a
        // genuine ん+[na/ni/nu/ne/no] sequence is ever needed.
        assertThat(RomajiConverter.toHiragana("ganni")).isEqualTo("がんい")
        assertThat(RomajiConverter.toHiragana("konnichiwa")).isEqualTo("こんいちわ")
        assertThat(RomajiConverter.toHiragana("sannin")).isEqualTo("さんいん")
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
    fun `a submitted answer mixing hiragana with a trailing romaji n resolves the n`() {
        // e.g. a user on a hiragana IME keyboard typing こうさて then falling back to romaji "n"
        // for the final ん. isComplete defaults to true for a submitted answer, so the trailing
        // "n" unambiguously resolves same as it would for a pure-romaji "kousaten".
        assertThat(RomajiConverter.toHiragana("こうさてn")).isEqualTo("こうさてん")
    }

    @Test
    fun `handles shi chi tsu fu alternate spellings`() {
        assertThat(RomajiConverter.toHiragana("shi")).isEqualTo("し")
        assertThat(RomajiConverter.toHiragana("si")).isEqualTo("し")
        assertThat(RomajiConverter.toHiragana("chi")).isEqualTo("ち")
        assertThat(RomajiConverter.toHiragana("tsu")).isEqualTo("つ")
        assertThat(RomajiConverter.toHiragana("tu")).isEqualTo("つ")
        assertThat(RomajiConverter.toHiragana("fu")).isEqualTo("ふ")
    }

    @Test
    fun `handles ja ju jo alternate spellings`() {
        assertThat(RomajiConverter.toHiragana("ja")).isEqualTo("じゃ")
        assertThat(RomajiConverter.toHiragana("ju")).isEqualTo("じゅ")
        assertThat(RomajiConverter.toHiragana("jo")).isEqualTo("じょ")
        assertThat(RomajiConverter.toHiragana("jya")).isEqualTo("じゃ")
        assertThat(RomajiConverter.toHiragana("zya")).isEqualTo("じゃ")
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

    @Test
    fun `apostrophe after n forces standalone n-kana before a vowel or y`() {
        // "ni" alone always greedily reads as に, so ん directly before い is otherwise
        // unreachable — this is the standard IME escape hatch (e.g. 権威, typed "ken'i").
        assertThat(RomajiConverter.toHiragana("ken'i")).isEqualTo("けんい")
        assertThat(RomajiConverter.toHiragana("n'i")).isEqualTo("んい")
        assertThat(RomajiConverter.toHiragana("n'ya")).isEqualTo("んや")
        // Without the apostrophe, the same input reads as a merged syllable instead.
        assertThat(RomajiConverter.toHiragana("ni")).isEqualTo("に")
    }

    @Test
    fun `a trailing n with nothing after it yet is left unconverted mid-typing`() {
        // isComplete = false is what the live-typing preview (RomajiVisualTransformation) uses —
        // a trailing "n" is genuinely ambiguous while more input might still arrive (a vowel next
        // would turn it into な/に/ぬ/ね/の instead), so it's shown as a bare "n" rather than an
        // eager, possibly-wrong ん that has to visibly flip once the next key lands.
        assertThat(RomajiConverter.convert("san", isComplete = false).output).isEqualTo("さn")
        assertThat(RomajiConverter.convert("gan", isComplete = false).output).isEqualTo("がn")
        // A single "n" already followed by a real character never needs to wait — it can only
        // possibly merge into な/に/ぬ/ね/の if that next character is itself a vowel/y, and both
        // cases already resolve correctly regardless of isComplete.
        assertThat(RomajiConverter.convert("kanji", isComplete = false).output).isEqualTo("かんじ")
        assertThat(RomajiConverter.convert("kani", isComplete = false).output).isEqualTo("かに")
    }

    @Test
    fun `a trailing doubled n with nothing after it yet resolves to a single n-kana`() {
        // Unlike a lone trailing "n", a trailing "nn" can never merge into な/に/ぬ/ね/の — a
        // following vowel/y would still force ん (see the doubled-n-before-a-vowel test above), so
        // there's nothing to wait for. It should resolve to ん immediately rather than showing a
        // dangling raw "n" after an already-resolved ん.
        assertThat(RomajiConverter.convert("nn", isComplete = false).output).isEqualTo("ん")
        assertThat(RomajiConverter.convert("konn", isComplete = false).output).isEqualTo("こん")
        assertThat(RomajiConverter.toHiragana("nn")).isEqualTo("ん")
    }

    @Test
    fun `a trailing n resolves once the rest of the word arrives, even mid-typing`() {
        // Once a real disambiguating character follows, isComplete no longer matters — this is
        // the same "wait for the second n" input completing normally as more keys are typed.
        assertThat(RomajiConverter.convert("ganni", isComplete = false).output).isEqualTo("がんい")
        // "sannin" itself ends in a trailing "n" with nothing after it yet, so — same as any
        // other mid-typing trailing n — that final one is still withheld pending isComplete;
        // only the earlier, now-disambiguated "nn" (followed by "i") resolves.
        assertThat(RomajiConverter.convert("sannin", isComplete = false).output).isEqualTo("さんいn")
    }

    @Test
    fun `a finished trailing n reads as standalone n-kana`() {
        // isComplete defaults to true — what grading (toHiragana on a submitted answer) uses.
        // There's nothing left to arrive, so a trailing "n" unambiguously means ん.
        assertThat(RomajiConverter.toHiragana("san")).isEqualTo("さん")
        assertThat(RomajiConverter.toHiragana("gohan")).isEqualTo("ごはん")
    }

    @Test
    fun `convert reports boundaries that round-trip to the same output as toHiragana`() {
        val conversion = RomajiConverter.convert("kyoutokitte")
        assertThat(conversion.output).isEqualTo(RomajiConverter.toHiragana("kyoutokitte"))
        assertThat(conversion.rawBoundaries.first()).isEqualTo(0)
        assertThat(conversion.hiraganaBoundaries.first()).isEqualTo(0)
        assertThat(conversion.rawBoundaries.last()).isEqualTo("kyoutokitte".length)
        assertThat(conversion.hiraganaBoundaries.last()).isEqualTo(conversion.output.length)
        // Both boundary arrays are non-decreasing and the same length (one per conversion step).
        assertThat(conversion.rawBoundaries.size).isEqualTo(conversion.hiraganaBoundaries.size)
        for (i in 1 until conversion.rawBoundaries.size) {
            assertThat(conversion.rawBoundaries[i]).isAtLeast(conversion.rawBoundaries[i - 1])
            assertThat(conversion.hiraganaBoundaries[i]).isAtLeast(conversion.hiraganaBoundaries[i - 1])
        }
    }
}
