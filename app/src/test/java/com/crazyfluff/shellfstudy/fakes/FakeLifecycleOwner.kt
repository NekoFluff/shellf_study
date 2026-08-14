package com.crazyfluff.shellfstudy.fakes

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/** A [LifecycleOwner] whose [lifecycle] is never actually read — [AppForegroundTracker]'s
 *  [androidx.lifecycle.DefaultLifecycleObserver] callbacks ignore their `owner` parameter
 *  entirely, so tests driving it directly (bypassing a real `ProcessLifecycleOwner`) just need
 *  something to pass in. */
object FakeLifecycleOwner : LifecycleOwner {
    override val lifecycle: Lifecycle get() = throw UnsupportedOperationException("never read")
}
