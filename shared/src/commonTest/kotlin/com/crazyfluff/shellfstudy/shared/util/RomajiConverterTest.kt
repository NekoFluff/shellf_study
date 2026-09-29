package com.crazyfluff.shellfstudy.shared.util

import com.crazyfluff.shellfstudy.shared.util.RomajiConverter
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class RomajiConverterTest {

    @Test
    fun `converts basic vowels`() {
        assertEquals("あいうえお", RomajiConverter.toHiragana("aiueo"))
    }

    @Test
    fun `converts simple words`() {
        assertEquals("みず", RomajiConverter.toHiragana("mizu"))
        assertEquals("ねこ", RomajiConverter.toHiragana("neko"))
        assertEquals("さくら", RomajiConverter.toHiragana("sakura"))
    }

    @Test
    fun `converts youon - palatalized - syllables`() {
        assertEquals("きょう", RomajiConverter.toHiragana("kyou"))
        assertEquals("しゃしょう", RomajiConverter.toHiragana("shashou"))
        assertEquals("りょこう", RomajiConverter.toHiragana("ryokou"))
    }

    @Test
    fun `converts sokuon - the doubled consonant - to small tsu`() {
        assertEquals("きって", RomajiConverter.toHiragana("kitte"))
        assertEquals("がっこう", RomajiConverter.toHiragana("gakkou"))
        assertEquals("ちょっと", RomajiConverter.toHiragana("chotto"))
    }

    @Test
    fun `converts n before a consonant to standalone n-kana`() {
        assertEquals("かんたん", RomajiConverter.toHiragana("kantan"))
        assertEquals("せんせい", RomajiConverter.toHiragana("sensei"))
    }

    @Test
    fun `double n before a vowel or y forces standalone n-kana instead of merging into the next syllable`() {
        // Deliberate tradeoff: doubling "n" is the other standard escape for ん directly before a
        // vowel/y (alongside "n'"), so "nn" no longer reads as ん followed by a な/に/ぬ/ね/の-row
        // syllable — even for real words that happen to contain that pattern naturally, like 三人
        // ("sannin") or こんにちは ("konnichiwa"). Use "n'i"/"sanni'n"-style apostrophes if a
        // genuine ん+[na/ni/nu/ne/no] sequence is ever needed.
        assertEquals("がんい", RomajiConverter.toHiragana("ganni"))
        assertEquals("こんいちわ", RomajiConverter.toHiragana("konnichiwa"))
        assertEquals("さんいん", RomajiConverter.toHiragana("sannin"))
    }

    @Test
    fun `leaves an incomplete trailing consonant unconverted`() {
        assertEquals("k", RomajiConverter.toHiragana("k"))
        assertEquals("みずk", RomajiConverter.toHiragana("mizuk"))
    }

    @Test
    fun `passes already-hiragana text through unchanged`() {
        assertEquals("みず", RomajiConverter.toHiragana("みず"))
    }

    @Test
    fun `a submitted answer mixing hiragana with a trailing romaji n resolves the n`() {
        // e.g. a user on a hiragana IME keyboard typing こうさて then falling back to romaji "n"
        // for the final ん. isComplete defaults to true for a submitted answer, so the trailing
        // "n" unambiguously resolves same as it would for a pure-romaji "kousaten".
        assertEquals("こうさてん", RomajiConverter.toHiragana("こうさてn"))
    }

    @Test
    fun `handles shi chi tsu fu alternate spellings`() {
        assertEquals("し", RomajiConverter.toHiragana("shi"))
        assertEquals("し", RomajiConverter.toHiragana("si"))
        assertEquals("ち", RomajiConverter.toHiragana("chi"))
        assertEquals("つ", RomajiConverter.toHiragana("tsu"))
        assertEquals("つ", RomajiConverter.toHiragana("tu"))
        assertEquals("ふ", RomajiConverter.toHiragana("fu"))
    }

    @Test
    fun `handles ja ju jo alternate spellings`() {
        assertEquals("じゃ", RomajiConverter.toHiragana("ja"))
        assertEquals("じゅ", RomajiConverter.toHiragana("ju"))
        assertEquals("じょ", RomajiConverter.toHiragana("jo"))
        assertEquals("じゃ", RomajiConverter.toHiragana("jya"))
        assertEquals("じゃ", RomajiConverter.toHiragana("zya"))
    }

    @Test
    fun `converts the voiced dakuten and semi-voiced handakuten rows`() {
        assertEquals("ごはん", RomajiConverter.toHiragana("gohan"))
        assertEquals("ぜんぶ", RomajiConverter.toHiragana("zenbu"))
        assertEquals("はっぱ", RomajiConverter.toHiragana("happa"))
    }

    @Test
    fun `empty input returns empty output`() {
        assertEquals("", RomajiConverter.toHiragana(""))
    }

    @Test
    fun `apostrophe after n forces standalone n-kana before a vowel or y`() {
        // "ni" alone always greedily reads as に, so ん directly before い is otherwise
        // unreachable — this is the standard IME escape hatch (e.g. 権威, typed "ken'i").
        assertEquals("けんい", RomajiConverter.toHiragana("ken'i"))
        assertEquals("んい", RomajiConverter.toHiragana("n'i"))
        assertEquals("んや", RomajiConverter.toHiragana("n'ya"))
        // Without the apostrophe, the same input reads as a merged syllable instead.
        assertEquals("に", RomajiConverter.toHiragana("ni"))
    }

    @Test
    fun `a trailing n with nothing after it yet is left unconverted mid-typing`() {
        // isComplete = false is what the live-typing preview (RomajiVisualTransformation) uses —
        // a trailing "n" is genuinely ambiguous while more input might still arrive (a vowel next
        // would turn it into な/に/ぬ/ね/の instead), so it's shown as a bare "n" rather than an
        // eager, possibly-wrong ん that has to visibly flip once the next key lands.
        assertEquals("さn", RomajiConverter.convert("san", isComplete = false).output)
        assertEquals("がn", RomajiConverter.convert("gan", isComplete = false).output)
        // A single "n" already followed by a real character never needs to wait — it can only
        // possibly merge into な/に/ぬ/ね/の if that next character is itself a vowel/y, and both
        // cases already resolve correctly regardless of isComplete.
        assertEquals("かんじ", RomajiConverter.convert("kanji", isComplete = false).output)
        assertEquals("かに", RomajiConverter.convert("kani", isComplete = false).output)
    }

    @Test
    fun `a trailing doubled n with nothing after it yet resolves to a single n-kana`() {
        // Unlike a lone trailing "n", a trailing "nn" can never merge into な/に/ぬ/ね/の — a
        // following vowel/y would still force ん (see the doubled-n-before-a-vowel test above), so
        // there's nothing to wait for. It should resolve to ん immediately rather than showing a
        // dangling raw "n" after an already-resolved ん.
        assertEquals("ん", RomajiConverter.convert("nn", isComplete = false).output)
        assertEquals("こん", RomajiConverter.convert("konn", isComplete = false).output)
        assertEquals("ん", RomajiConverter.toHiragana("nn"))
    }

    @Test
    fun `a trailing n resolves once the rest of the word arrives even mid-typing`() {
        // Once a real disambiguating character follows, isComplete no longer matters — this is
        // the same "wait for the second n" input completing normally as more keys are typed.
        assertEquals("がんい", RomajiConverter.convert("ganni", isComplete = false).output)
        // "sannin" itself ends in a trailing "n" with nothing after it yet, so — same as any
        // other mid-typing trailing n — that final one is still withheld pending isComplete;
        // only the earlier, now-disambiguated "nn" (followed by "i") resolves.
        assertEquals("さんいn", RomajiConverter.convert("sannin", isComplete = false).output)
    }

    @Test
    fun `a finished trailing n reads as standalone n-kana`() {
        // isComplete defaults to true — what grading (toHiragana on a submitted answer) uses.
        // There's nothing left to arrive, so a trailing "n" unambiguously means ん.
        assertEquals("さん", RomajiConverter.toHiragana("san"))
        assertEquals("ごはん", RomajiConverter.toHiragana("gohan"))
    }

    @Test
    fun `convert reports boundaries that round-trip to the same output as toHiragana`() {
        val conversion = RomajiConverter.convert("kyoutokitte")
        assertEquals(RomajiConverter.toHiragana("kyoutokitte"), conversion.output)
        assertEquals(0, conversion.rawBoundaries.first())
        assertEquals(0, conversion.hiraganaBoundaries.first())
        assertEquals("kyoutokitte".length, conversion.rawBoundaries.last())
        assertEquals(conversion.output.length, conversion.hiraganaBoundaries.last())
        // Both boundary arrays are non-decreasing and the same length (one per conversion step).
        assertEquals(conversion.hiraganaBoundaries.size, conversion.rawBoundaries.size)
        for (i in 1 until conversion.rawBoundaries.size) {
            assertTrue(conversion.rawBoundaries[i] >= conversion.rawBoundaries[i - 1])
            assertTrue(conversion.hiraganaBoundaries[i] >= conversion.hiraganaBoundaries[i - 1])
        }
    }
}
