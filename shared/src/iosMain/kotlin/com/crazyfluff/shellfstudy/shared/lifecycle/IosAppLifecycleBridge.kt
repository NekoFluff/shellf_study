package com.crazyfluff.shellfstudy.shared.lifecycle

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification

/**
 * iOS has no `ProcessLifecycleOwner`, so [AppForegroundTracker] can't be attached as a
 * `DefaultLifecycleObserver` the way Android's `ShellfStudyApplication` does. This forwards the
 * equivalent app-active/app-inactive notifications into the same shared tracker instead.
 */
@OptIn(ExperimentalForeignApi::class)
fun wireIosAppLifecycle(appForegroundTracker: AppForegroundTracker) {
    NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationDidBecomeActiveNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue
    ) { appForegroundTracker.markForeground() }

    // Resigning active rather than entering the background: the app switcher only makes the app
    // inactive, so a swipe-kill from there never reaches DidEnterBackground, and the running study-time
    // stretch (written when it ends) would be lost with the process. Control Center, the notification
    // shade and an incoming call pause the clock too, which is right: none of that is studying.
    NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationWillResignActiveNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue
    ) { appForegroundTracker.markBackground() }
}
