package com.crazyfluff.shellfstudy.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.crazyfluff.shellfstudy.shared.di.iosAppModules
import com.crazyfluff.shellfstudy.shared.lifecycle.AppForegroundTracker
import com.crazyfluff.shellfstudy.shared.lifecycle.wireIosAppLifecycle
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

// Stashed so platform actuals that need to present something modally (e.g. the share sheet — see
// ShareText.ios.kt) have a UIViewController to present over, since Swift never hands this back to
// Kotlin after MainViewController() returns it.
internal var rootViewController: UIViewController? = null
    private set

fun MainViewController(): UIViewController = ComposeUIViewController {
    ShellfStudyApp()
}.also { rootViewController = it }

fun initKoin() {
    val koinApplication = startKoin {
        modules(iosAppModules)
    }
    wireIosAppLifecycle(koinApplication.koin.get<AppForegroundTracker>())
}
