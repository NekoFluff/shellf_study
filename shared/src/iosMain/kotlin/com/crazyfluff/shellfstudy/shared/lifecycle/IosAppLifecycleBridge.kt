package com.crazyfluff.shellfstudy.shared.lifecycle

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

/**
 * iOS has no `ProcessLifecycleOwner`, so [AppForegroundTracker] can't be attached as a
 * `DefaultLifecycleObserver` the way Android's `ShellfStudyApplication` does. This forwards the
 * equivalent app-active/app-backgrounded notifications into the same shared tracker instead.
 */
@OptIn(ExperimentalForeignApi::class)
fun wireIosAppLifecycle(appForegroundTracker: AppForegroundTracker) {
    NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationDidBecomeActiveNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue
    ) { appForegroundTracker.markForeground() }

    NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationDidEnterBackgroundNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue
    ) { appForegroundTracker.markBackground() }
}
