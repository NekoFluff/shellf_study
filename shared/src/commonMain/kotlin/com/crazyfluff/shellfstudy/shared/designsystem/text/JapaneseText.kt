package com.crazyfluff.shellfstudy.shared.designsystem.text

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.crazyfluff.shellfstudy.shared.designsystem.theme.LocalJapaneseFontFamily

/** A [Text] that renders in [LocalJapaneseFontFamily] instead of the ambient (Latin) typography
 *  font — use this wherever a plain string of Japanese content (kanji, kana, readings) is
 *  displayed on its own, so it doesn't inherit Plus Jakarta Sans's implicit CJK fallback. */
@Composable
fun JapaneseText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style.copy(fontFamily = LocalJapaneseFontFamily.current),
        maxLines = maxLines,
        overflow = overflow
    )
}
