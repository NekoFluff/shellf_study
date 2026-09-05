package com.crazyfluff.shellfstudy.shared.designsystem.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.model.ContextSentence

object ContextSentenceRowTestTags {
    const val SHARE_BUTTON = "context_sentence_share_button"
}

/**
 * One example sentence with a lookup button that hands the Japanese text off to a dictionary
 * (see [rememberShareText]) — an explicit, always-reliable alternative to long-press-selecting the
 * text, which has to compete with this screen's own scroll/swipe gestures and is fiddly on
 * unspaced CJK text. Shared between the subject-detail and lesson screens.
 */
@Composable
fun ContextSentenceRow(sentence: ContextSentence, onShare: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        // Top, not CenterVertically — the button should line up with the Japanese line it acts
        // on, not float centered between it and the English translation below.
        verticalAlignment = Alignment.Top
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            JapaneseText(sentence.japanese, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = sentence.english,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = { onShare(sentence.japanese) },
            modifier = Modifier.testTag(ContextSentenceRowTestTags.SHARE_BUTTON)
        ) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Look up sentence")
        }
    }
}
