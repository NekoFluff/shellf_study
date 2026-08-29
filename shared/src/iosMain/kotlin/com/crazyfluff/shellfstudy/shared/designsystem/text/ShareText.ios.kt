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
    { text: String -> topmostViewController(rootViewController)?.let { presenter -> presentShareSheet(presenter, text) } }
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
