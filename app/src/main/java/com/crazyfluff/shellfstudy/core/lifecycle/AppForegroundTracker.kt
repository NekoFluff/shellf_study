package com.crazyfluff.shellfstudy.core.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the app process currently has any Activity started — used to pause session timers
 *  (see [com.crazyfluff.shellfstudy.feature.review.ReviewViewModel],
 *  [com.crazyfluff.shellfstudy.feature.lesson.LessonViewModel]) while the user isn't actually
 *  looking at the app, e.g. backgrounded via the home button or app switcher, without every screen
 *  needing its own lifecycle observer. Registered once against `ProcessLifecycleOwner` in
 *  `ShellfStudyApplication.onCreate()`. */
@Singleton
class AppForegroundTracker @Inject constructor() : DefaultLifecycleObserver {
    private val _isForeground = MutableStateFlow(true)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    override fun onStart(owner: LifecycleOwner) {
        _isForeground.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        _isForeground.value = false
    }
}
