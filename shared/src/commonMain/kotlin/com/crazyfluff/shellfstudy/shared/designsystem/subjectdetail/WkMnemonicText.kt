package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.crazyfluff.shellfstudy.shared.designsystem.theme.SubjectTypeColors
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.themeAwareColor
import com.crazyfluff.shellfstudy.shared.network.SubjectType

private val markupRegex = Regex(
    "<a\\s+href=\"([^\"]*)\"[^>]*>" + // group 1: <a href="..."> opening anchor tag
        "|<(/?)([a-zA-Z_]+)>" + // group 2: closing slash, group 3: WK semantic tag name
        "|(https?://[^\\s<>\"]+)" // group 4: bare URL
)
private val DefaultLinkColor = Color(0xFF0066CC)

/**
 * WaniKani mnemonics embed semantic tags (`<radical>`, `<kanji>`, `<vocabulary>`,
 * `<kana_vocabulary>`, `<reading>`, `<ja>`) around terms they reference, and occasionally a link —
 * either a bare URL or an `<a href="...">...</a>` anchor. This parses those flat, non-nesting tags
 * into an [AnnotatedString] with per-type colored spans, mirroring how WaniKani's own apps render
 * mnemonic text, and turns links into clickable [LinkAnnotation.Url] spans. Unrecognized tags are
 * stripped silently — never leaked as raw text.
 *
 * Colors default to the plain (non-theme-aware) brand palette so this stays a pure function
 * outside of composition (see WkMnemonicTextTest); [WkMnemonicText] passes theme-resolved colors
 * explicitly so e-ink mode renders these spans in grayscale.
 */
fun parseWkMarkup(
    text: String,
    radicalColor: Color = SubjectTypeColors.Radical,
    kanjiColor: Color = SubjectTypeColors.Kanji,
    vocabularyColor: Color = SubjectTypeColors.Vocabulary,
    linkColor: Color = DefaultLinkColor
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    val openTags = ArrayDeque<String>()
    val linkStyle = TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))

    for (match in markupRegex.findAll(text)) {
        append(text.substring(cursor, match.range.first))
        cursor = match.range.last + 1

        val anchorHref = match.groupValues[1]
        if (anchorHref.isNotEmpty()) {
            pushLink(LinkAnnotation.Url(url = anchorHref, styles = linkStyle))
            openTags.addLast("a")
            continue
        }

        val url = match.groupValues[4]
        if (url.isNotEmpty()) {
            pushLink(LinkAnnotation.Url(url = url, styles = linkStyle))
            append(url)
            pop()
            continue
        }

        val isClosing = match.groupValues[2] == "/"
        val tag = match.groupValues[3]
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
        vocabularyColor = subjectColor(SubjectType.VOCABULARY),
        linkColor = themeAwareColor(DefaultLinkColor, MaterialTheme.colorScheme.primary)
    )
    Text(text = annotated, modifier = modifier, style = style)
}
