package com.crazyfluff.shellfstudy.shared.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppForegroundTracker : DefaultLifecycleObserver {
    private val _isForeground = MutableStateFlow(true)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    override fun onStart(owner: LifecycleOwner) {
        markForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        markBackground()
    }

    /** Entry point for platforms without a [LifecycleOwner] to observe (iOS uses this directly
     *  from its own app-active notification bridge instead of `onStart`). */
    fun markForeground() {
        _isForeground.value = true
    }

    fun markBackground() {
        _isForeground.value = false
    }
}
