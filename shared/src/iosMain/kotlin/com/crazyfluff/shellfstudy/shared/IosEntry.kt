package com.crazyfluff.shellfstudy.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.crazyfluff.shellfstudy.shared.di.iosAppModules
import com.crazyfluff.shellfstudy.shared.lifecycle.AppForegroundTracker
import com.crazyfluff.shellfstudy.shared.lifecycle.wireIosAppLifecycle
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    ShellfStudyApp()
}

fun initKoin() {
    val koinApplication = startKoin {
        modules(iosAppModules)
    }
    wireIosAppLifecycle(koinApplication.koin.get<AppForegroundTracker>())
}
