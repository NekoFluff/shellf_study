package com.crazyfluff.shellfstudy.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.audio.OfflineAudioManager
import com.crazyfluff.shellfstudy.shared.di.APPLICATION_SCOPE
import com.crazyfluff.shellfstudy.shared.di.iosAppModules
import com.crazyfluff.shellfstudy.shared.lifecycle.AppForegroundTracker
import com.crazyfluff.shellfstudy.shared.lifecycle.startIosConnectivityMonitor
import com.crazyfluff.shellfstudy.shared.lifecycle.wireIosAppLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
    val koin = koinApplication.koin
    wireIosAppLifecycle(koin.get<AppForegroundTracker>())
    startIosConnectivityMonitor { previous, current ->
        if (!current.online) return@startIosConnectivityMonitor
        if (!previous.online) koin.get<OutboxRepository>().requestSyncNow()
        // Back online, or off a metered network: either may be what an offline audio download
        // that stopped (or never started under "Wi-Fi only") was waiting for.
        koin.get<CoroutineScope>(APPLICATION_SCOPE).launch { koin.get<OfflineAudioManager>().resume() }
    }
}
