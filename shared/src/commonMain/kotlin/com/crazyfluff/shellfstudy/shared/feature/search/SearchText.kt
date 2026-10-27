package com.crazyfluff.shellfstudy.shared.feature.search

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/** [text] with every case-insensitive occurrence of [query] in [style] — the same matching the
 *  sentence search itself does, so the span shown is the one that matched. */
fun highlightMatches(text: String, query: String, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(text)
    if (query.isEmpty()) return@buildAnnotatedString
    var from = text.indexOf(query, ignoreCase = true)
    while (from >= 0) {
        addStyle(style, from, from + query.length)
        from = text.indexOf(query, startIndex = from + query.length, ignoreCase = true)
    }
}

/** [count] with thousands separators — "19,500". Common code has no locale-aware number format, and
 *  every other number in the app is shown in English. */
fun formatCount(count: Int): String =
    count.toString().reversed().chunked(DIGITS_PER_GROUP).joinToString(",").reversed()

private const val DIGITS_PER_GROUP = 3
