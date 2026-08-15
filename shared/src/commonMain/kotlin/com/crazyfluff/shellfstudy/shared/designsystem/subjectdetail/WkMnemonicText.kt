package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.crazyfluff.shellfstudy.shared.designsystem.theme.SubjectTypeColors
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.shared.network.SubjectType

private val markupTagRegex = Regex("<(/?)([a-zA-Z_]+)>")

/**
 * WaniKani mnemonics embed semantic tags (`<radical>`, `<kanji>`, `<vocabulary>`,
 * `<kana_vocabulary>`, `<reading>`, `<ja>`) around terms they reference. This parses those flat,
 * non-nesting tags into an [AnnotatedString] with per-type colored spans, mirroring how WaniKani's
 * own apps render mnemonic text. Unrecognized tags are stripped silently — never leaked as raw text.
 *
 * Colors default to the plain (non-theme-aware) brand palette so this stays a pure function
 * outside of composition (see WkMnemonicTextTest); [WkMnemonicText] passes theme-resolved colors
 * explicitly so e-ink mode renders these spans in grayscale.
 */
fun parseWkMarkup(
    text: String,
    radicalColor: Color = SubjectTypeColors.Radical,
    kanjiColor: Color = SubjectTypeColors.Kanji,
    vocabularyColor: Color = SubjectTypeColors.Vocabulary
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    val openTags = ArrayDeque<String>()

    for (match in markupTagRegex.findAll(text)) {
        append(text.substring(cursor, match.range.first))
        cursor = match.range.last + 1

        val isClosing = match.groupValues[1] == "/"
        val tag = match.groupValues[2]
        if (isClosing) {
            if (openTags.isNotEmpty() && openTags.last() == tag) {
                openTags.removeLast()
                pop()
            }
        } else {
            styleForTag(tag, radicalColor, kanjiColor, vocabularyColor)?.let { style ->
                pushStyle(style)
                openTags.addLast(tag)
            }
        }
    }
    append(text.substring(cursor))
}

private fun styleForTag(tag: String, radicalColor: Color, kanjiColor: Color, vocabularyColor: Color): SpanStyle? =
    when (tag) {
        "radical" -> SpanStyle(color = radicalColor)
        "kanji" -> SpanStyle(color = kanjiColor)
        "vocabulary", "kana_vocabulary" -> SpanStyle(color = vocabularyColor)
        "reading" -> SpanStyle(fontStyle = FontStyle.Italic)
        "ja" -> SpanStyle(fontWeight = FontWeight.Medium)
        else -> null
    }

@Composable
fun WkMnemonicText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current
) {
    val annotated = parseWkMarkup(
        text = text,
        radicalColor = subjectColor(SubjectType.RADICAL),
        kanjiColor = subjectColor(SubjectType.KANJI),
        vocabularyColor = subjectColor(SubjectType.VOCABULARY)
    )
    Text(text = annotated, modifier = modifier, style = style)
}
