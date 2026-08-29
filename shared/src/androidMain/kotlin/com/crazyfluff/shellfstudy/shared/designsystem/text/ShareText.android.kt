package com.crazyfluff.shellfstudy.shared.designsystem.text

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberShareText(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text: String -> context.startActivity(buildShareChooserIntent(text)) }
    }
}

private fun buildShareChooserIntent(text: String): Intent {
    val sendIntent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    return Intent.createChooser(sendIntent, null)
}
