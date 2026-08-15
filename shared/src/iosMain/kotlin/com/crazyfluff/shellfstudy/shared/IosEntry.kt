package com.crazyfluff.shellfstudy.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.crazyfluff.shellfstudy.shared.di.iosAppModules
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    ShellfStudyApp()
}

fun initKoin() {
    startKoin {
        modules(iosAppModules)
    }
}
