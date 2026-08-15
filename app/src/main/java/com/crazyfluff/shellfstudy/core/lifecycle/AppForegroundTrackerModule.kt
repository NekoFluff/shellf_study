package com.crazyfluff.shellfstudy.core.lifecycle

import com.crazyfluff.shellfstudy.shared.lifecycle.AppForegroundTracker
import org.koin.dsl.module

val appForegroundTrackerModule = module {
    single { AppForegroundTracker() }
}
