package com.crazyfluff.shellfstudy.shared.designsystem.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
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
 * One example sentence with a share button that hands the Japanese text off to the OS share sheet
 * (see [rememberShareText]) — an explicit, always-reliable alternative to long-press-selecting the
 * text, which has to compete with this screen's own scroll/swipe gestures and is fiddly on
 * unspaced CJK text. Shared between the subject-detail and lesson screens.
 */
@Composable
fun ContextSentenceRow(sentence: ContextSentence, onShare: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(sentence.japanese, style = MaterialTheme.typography.bodyMedium)
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
            Icon(Icons.Filled.Share, contentDescription = "Share sentence")
        }
    }
}
