package com.crazyfluff.shellfstudy.shared.designsystem.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.crazyfluff.shellfstudy.shared.rootViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

@Composable
actual fun rememberShareText(): (String) -> Unit = remember {
    { text: String ->
        // Guards against a rapid double-tap: presentViewController sets presentedViewController
        // synchronously (the animation itself is what's async), so a second tap landing before
        // that first sheet is dismissed would otherwise try to present again on top of it instead
        // of no-oping. Checked on rootViewController specifically — it stays non-null for as long
        // as any descendant in the presented chain is showing, unlike topmostViewController's
        // resolved result, which by construction always has a nil presentedViewController of its
        // own.
        if (rootViewController?.presentedViewController == null) {
            topmostViewController(rootViewController)?.let { presenter -> presentShareSheet(presenter, text) }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun presentShareSheet(presenter: UIViewController, text: String) {
    val activityController = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
    // iPad presents UIActivityViewController as a popover and crashes without an anchor — the
    // presenting view itself is a reasonable default since the trigger button's own position isn't
    // available all the way out here (rememberShareText only hands back a plain (String) -> Unit).
    activityController.popoverPresentationController?.let { popover ->
        popover.sourceView = presenter.view
        popover.sourceRect = presenter.view.bounds
    }
    presenter.presentViewController(activityController, animated = true, completion = null)
}

private tailrec fun topmostViewController(from: UIViewController?): UIViewController? {
    val presented = from?.presentedViewController ?: return from
    return topmostViewController(presented)
}
