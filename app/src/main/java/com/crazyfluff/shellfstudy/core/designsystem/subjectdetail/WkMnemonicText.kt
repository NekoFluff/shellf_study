package com.crazyfluff.shellfstudy.core.designsystem.subjectdetail

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.crazyfluff.shellfstudy.core.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.core.network.SubjectType

private val markupTagRegex = Regex("<(/?)([a-zA-Z_]+)>")

/**
 * WaniKani mnemonics embed semantic tags (`<radical>`, `<kanji>`, `<vocabulary>`,
 * `<kana_vocabulary>`, `<reading>`, `<ja>`) around terms they reference. This parses those flat,
 * non-nesting tags into an [AnnotatedString] with per-type colored spans, mirroring how WaniKani's
 * own apps render mnemonic text. Unrecognized tags are stripped silently — never leaked as raw text.
 */
fun parseWkMarkup(text: String): AnnotatedString = buildAnnotatedString {
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
            styleForTag(tag)?.let { style ->
                pushStyle(style)
                openTags.addLast(tag)
            }
        }
    }
    append(text.substring(cursor))
}

private fun styleForTag(tag: String): SpanStyle? = when (tag) {
    "radical" -> SpanStyle(color = subjectColor(SubjectType.RADICAL))
    "kanji" -> SpanStyle(color = subjectColor(SubjectType.KANJI))
    "vocabulary", "kana_vocabulary" -> SpanStyle(color = subjectColor(SubjectType.VOCABULARY))
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
    Text(text = parseWkMarkup(text), modifier = modifier, style = style)
}
