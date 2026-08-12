package com.crazyfluff.shellfstudy.core.util

/**
 * Converts romaji (Latin-alphabet) input to hiragana as the user types, the same way the
 * WaniKani web/mobile clients do for reading answers. Designed to be re-run on the *whole*
 * current input on every keystroke: already-converted hiragana characters don't match any
 * romaji rule and simply pass through untouched, so only the trailing, not-yet-complete romaji
 * portion is affected.
 */
object RomajiConverter {

    private val VOWELS = setOf('a', 'i', 'u', 'e', 'o')
    private val DOUBLING_CONSONANTS = setOf('k', 's', 't', 'p', 'g', 'z', 'd', 'b', 'c', 'j', 'f', 'h', 'm', 'r', 'y', 'w')

    // Longest matches first within each length group; grouped by length below for clarity.
    private val THREE_CHAR_RULES = mapOf(
        "kya" to "きゃ", "kyu" to "きゅ", "kyo" to "きょ",
        "sha" to "しゃ", "shu" to "しゅ", "sho" to "しょ",
        "sya" to "しゃ", "syu" to "しゅ", "syo" to "しょ",
        "cha" to "ちゃ", "chu" to "ちゅ", "cho" to "ちょ",
        "tya" to "ちゃ", "tyu" to "ちゅ", "tyo" to "ちょ",
        "nya" to "にゃ", "nyu" to "にゅ", "nyo" to "にょ",
        "hya" to "ひゃ", "hyu" to "ひゅ", "hyo" to "ひょ",
        "mya" to "みゃ", "myu" to "みゅ", "myo" to "みょ",
        "rya" to "りゃ", "ryu" to "りゅ", "ryo" to "りょ",
        "gya" to "ぎゃ", "gyu" to "ぎゅ", "gyo" to "ぎょ",
        "gwa" to "ぐぁ",
        "zya" to "じゃ", "zyu" to "じゅ", "zyo" to "じょ",
        "jya" to "じゃ", "jyu" to "じゅ", "jyo" to "じょ",
        "bya" to "びゃ", "byu" to "びゅ", "byo" to "びょ",
        "pya" to "ぴゃ", "pyu" to "ぴゅ", "pyo" to "ぴょ",
        "dya" to "ぢゃ", "dyu" to "ぢゅ", "dyo" to "ぢょ",
        "tsu" to "つ", "shi" to "し", "chi" to "ち"
    )

    private val TWO_CHAR_RULES = mapOf(
        "ka" to "か", "ki" to "き", "ku" to "く", "ke" to "け", "ko" to "こ",
        "sa" to "さ", "si" to "し", "su" to "す", "se" to "せ", "so" to "そ",
        "ta" to "た", "ti" to "ち", "tu" to "つ", "te" to "て", "to" to "と",
        "na" to "な", "ni" to "に", "nu" to "ぬ", "ne" to "ね", "no" to "の",
        "ha" to "は", "hi" to "ひ", "fu" to "ふ", "hu" to "ふ", "he" to "へ", "ho" to "ほ",
        "ma" to "ま", "mi" to "み", "mu" to "む", "me" to "め", "mo" to "も",
        "ra" to "ら", "ri" to "り", "ru" to "る", "re" to "れ", "ro" to "ろ",
        "ga" to "が", "gi" to "ぎ", "gu" to "ぐ", "ge" to "げ", "go" to "ご",
        "za" to "ざ", "ji" to "じ", "zi" to "じ", "zu" to "ず", "ze" to "ぜ", "zo" to "ぞ",
        "ja" to "じゃ", "ju" to "じゅ", "jo" to "じょ",
        "da" to "だ", "di" to "ぢ", "du" to "づ", "de" to "で", "do" to "ど",
        "ba" to "ば", "bi" to "び", "bu" to "ぶ", "be" to "べ", "bo" to "ぼ",
        "pa" to "ぱ", "pi" to "ぴ", "pu" to "ぷ", "pe" to "ぺ", "po" to "ぽ",
        "ya" to "や", "yu" to "ゆ", "yo" to "よ",
        "wa" to "わ", "wo" to "を", "wi" to "ゐ", "we" to "ゑ"
    )

    private val ONE_CHAR_RULES = mapOf(
        "a" to "あ", "i" to "い", "u" to "う", "e" to "え", "o" to "お",
        "n" to "ん"
    )

    /**
     * Result of a conversion pass: the hiragana [output], plus parallel cumulative-length
     * boundary arrays recording how each conversion step's raw input span maps to its hiragana
     * output span — e.g. `rawBoundaries[k]`/`hiraganaBoundaries[k]` is the raw/hiragana length
     * consumed/produced after the first `k` steps. Lets a [androidx.compose.ui.text.input.OffsetMapping]
     * translate cursor/selection offsets between the two without guessing at a many-to-many
     * mapping, since every offset the user could tap into falls on or between these boundaries.
     */
    data class Conversion(val output: String, val rawBoundaries: IntArray, val hiraganaBoundaries: IntArray)

    /**
     * @param isComplete Whether [input] is the final, finished string (e.g. a submitted answer)
     * rather than still being typed. Only affects a trailing "n" with nothing after it: when
     * `false` (the live-typing preview), it's left as a bare "n" rather than eagerly guessing ん,
     * since a vowel could still arrive next and turn it into な/に/ぬ/ね/の instead — see
     * [com.crazyfluff.shellfstudy.core.designsystem.text.RomajiVisualTransformation]. When `true`
     * (the default, used for grading a submitted answer) there's nothing left to arrive, so a
     * trailing "n" unambiguously means ん (e.g. "san" -> さん).
     */
    fun convert(input: String, isComplete: Boolean = true): Conversion {
        val result = StringBuilder()
        val rawBoundaries = mutableListOf(0)
        val hiraganaBoundaries = mutableListOf(0)
        var i = 0
        while (i < input.length) {
            val remaining = input.length - i

            val threeChar = if (remaining >= 3) input.substring(i, i + 3) else null
            val twoChar = if (remaining >= 2) input.substring(i, i + 2) else null
            val current = input[i]
            val next = if (remaining >= 2) input[i + 1] else null

            when {
                threeChar != null && THREE_CHAR_RULES.containsKey(threeChar) -> {
                    result.append(THREE_CHAR_RULES.getValue(threeChar))
                    i += 3
                }

                twoChar != null && TWO_CHAR_RULES.containsKey(twoChar) -> {
                    result.append(TWO_CHAR_RULES.getValue(twoChar))
                    i += 2
                }

                // Doubled consonant -> small tsu (e.g. "kitte" -> き + っ + て).
                next != null && current == next && current.lowercaseChar() in DOUBLING_CONSONANTS && current != 'n' -> {
                    result.append('っ')
                    i += 1
                }

                // "n'" explicitly forces ん and drops the apostrophe, so a following vowel or "y"
                // stays its own mora instead of merging into な/に/ぬ/ね/の/にゃ... — the standard
                // IME escape hatch for words like 権威 (けんい), typed "ken'i". Without this,
                // "ni" always greedily reads as に (via the two-char rule above) and ん + い is
                // otherwise unreachable no matter how many n's precede it.
                current == 'n' && next == '\'' -> {
                    result.append('ん')
                    i += 2
                }

                // Doubled "n" is the other standard escape for ん directly before a vowel or "y"
                // (alongside "n'"), e.g. "ganni" -> がんい. This deliberately shadows the more
                // common case where "nn" is just ん naturally followed by a な/に/ぬ/ね/の-row
                // syllable (三人 "sannin" would now read さんいん instead of さんにん) — an
                // accepted tradeoff so double-n reliably means "force ん" rather than being
                // ambiguous with the next syllable.
                current == 'n' && next == 'n' && remaining >= 3 && (input[i + 2] in VOWELS || input[i + 2] == 'y') -> {
                    result.append('ん')
                    i += 2
                }

                // A lone "n" converts to ん once we know it isn't the start of "na/ni/nu/ne/no/nya...":
                // when followed by a known consonant or another "n" (that doesn't itself force
                // ん-before-vowel above).
                current == 'n' && next != null && next !in VOWELS && next != 'y' -> {
                    result.append('ん')
                    i += 1
                }

                // A trailing "n" with nothing typed after it yet — see [isComplete]'s doc.
                current == 'n' && next == null && isComplete -> {
                    result.append('ん')
                    i += 1
                }

                else -> {
                    val oneChar = ONE_CHAR_RULES[current.toString()]
                    if (oneChar != null && current != 'n') {
                        result.append(oneChar)
                        i += 1
                    } else {
                        // Not (yet) a complete romaji syllable, or already hiragana/other text — pass through.
                        result.append(current)
                        i += 1
                    }
                }
            }

            rawBoundaries.add(i)
            hiraganaBoundaries.add(result.length)
        }
        return Conversion(result.toString(), rawBoundaries.toIntArray(), hiraganaBoundaries.toIntArray())
    }

    fun toHiragana(input: String): String = convert(input).output
}
