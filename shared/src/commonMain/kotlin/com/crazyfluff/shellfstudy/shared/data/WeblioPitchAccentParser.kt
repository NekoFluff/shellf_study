package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element

private val PITCH_NUMBER_PATTERN = Regex("［(\\d+)］")
private val END_OF_READING_PATTERN = Regex("[【】［］〔〕]")
private val PART_OF_SPEECH_PATTERN = Regex("（([^）]+)）")
private const val SMALL_PRINT_STYLE = "font-size:75%;"

private fun isKana(c: Char): Boolean =
    c in 'ぁ'..'ゖ' || c in 'ァ'..'ヺ' || c == 'ー'

/**
 * Ported from Smouldering Durtles' `PitchInfoUtil.parseWeblioPage` — same weblio.jp markup
 * conventions (`.NetDicHead` for the reading + pitch-number heading, `.NetDicBody` for
 * part-of-speech variants), same regexes. Fragile to weblio changing their page markup, same as
 * the original. Uses Ksoup rather than jsoup so this stays usable from the iOS target — jsoup is
 * JVM-only.
 */
class WeblioPitchAccentParser {

    fun parse(html: String): List<PitchAccent> {
        val doc = Ksoup.parse(html)
        val heads = doc.getElementsByClass("NetDicHead")
        if (heads.isEmpty()) return emptyList()

        val result = mutableSetOf<PitchAccent>()
        for (head in heads) {
            head.getElementsByTag("span")
                .filter { it.parent() != null && it.attr("style") == SMALL_PRINT_STYLE }
                .filterNot { PITCH_NUMBER_PATTERN.matches(it.text().trim()) }
                .forEach { it.remove() }

            val headText = head.text()
            val pitchNumbers = PITCH_NUMBER_PATTERN.findAll(headText).map { it.groupValues[1].toInt() }.toList()

            val readingEnd = END_OF_READING_PATTERN.find(headText)?.range?.first ?: headText.length
            val reading = headText.substring(0, readingEnd).trim().filter(::isKana)
            if (reading.isEmpty()) continue

            pitchNumbers.forEach { result += PitchAccent(reading, null, it) }

            val body = head.nextElementSibling()
            if (pitchNumbers.isEmpty() && body != null && body.className() == "NetDicBody") {
                result += parseVariants(body, reading)
            }
        }
        return result.toList()
    }

    private fun parseVariants(body: Element, reading: String): List<PitchAccent> {
        val result = mutableListOf<PitchAccent>()
        for (span in body.getElementsByAttribute("data-txt-len")) {
            if (!span.attr("style").contains("background-color:black")) continue
            val parent = span.parent() ?: continue
            val span2 = parent.nextElementSibling() ?: continue

            span2.getElementsByTag("span")
                .filter { it.parent() != null && it.attr("style") == SMALL_PRINT_STYLE }
                .forEach { it.remove() }
            span2.getElementsByTag("div").forEach { it.remove() }

            val variantString = span2.text().trim()
            if (variantString.isEmpty()) continue

            val pitchNumbers = PITCH_NUMBER_PATTERN.findAll(variantString).map { it.groupValues[1].toInt() }.toList()
            val partOfSpeech = PART_OF_SPEECH_PATTERN.find(variantString)?.groupValues?.get(1)?.trim()
            pitchNumbers.forEach { result += PitchAccent(reading, partOfSpeech, it) }
        }
        return result
    }
}
