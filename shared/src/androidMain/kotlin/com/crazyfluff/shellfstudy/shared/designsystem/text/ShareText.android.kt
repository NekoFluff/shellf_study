package com.crazyfluff.shellfstudy.shared.designsystem.text

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val AKEBI_PACKAGE = "com.craxic.akebifree"

@Composable
actual fun rememberShareText(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text: String -> openInAkebiOrPlayStore(context, text) }
    }
}

/** Launches Akebi directly via the exact ACTION_PROCESS_TEXT intent it already registers
 *  for (the same one behind [AkebiSelectableContainer]'s long-press "Look up in Akebi"
 *  menu item) — no chooser, no text-selection gesture needed. Falls back to Akebi's Play
 *  Store listing if it isn't installed. */
private fun openInAkebiOrPlayStore(context: Context, text: String) {
    val intent = buildAkebiProcessTextIntent(text)
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        openAkebiPlayStoreListing(context)
    }
}

private fun buildAkebiProcessTextIntent(text: String): Intent =
    Intent(Intent.ACTION_PROCESS_TEXT)
        .setType("text/plain")
        .setPackage(AKEBI_PACKAGE)
        .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)

private fun openAkebiPlayStoreListing(context: Context) {
    val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$AKEBI_PACKAGE"))
    if (marketIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(marketIntent)
        return
    }
    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$AKEBI_PACKAGE"))
    if (webIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(webIntent)
    }
    // Else: no Play Store and no browser at all — deliberately no-op rather than crash.
}
