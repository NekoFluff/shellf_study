package com.crazyfluff.shellfstudy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crazyfluff.shellfstudy.shared.designsystem.performance.JankStatsTracker
import com.crazyfluff.shellfstudy.shared.ShellfStudyApp
import com.crazyfluff.shellfstudy.shared.data.PitchAccentBundledSource
import com.crazyfluff.shellfstudy.shared.data.StrokeOrderRepository
import com.crazyfluff.shellfstudy.shared.di.APPLICATION_SCOPE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import com.crazyfluff.shellfstudy.shared.notifications.NotificationDeepLink

class MainActivity : ComponentActivity() {

    // Tracks a notification tap's target screen. android:launchMode is the default ("standard"),
    // so a tap while the Activity is already running (the poster sets FLAG_ACTIVITY_CLEAR_TOP or
    // FLAG_ACTIVITY_SINGLE_TOP) routes through onNewIntent below rather than a fresh onCreate.
    private var pendingDestination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The jank harness is off unless this build is debuggable, so no release behaviour changes.
        // The system-property override exists so a release build can be profiled without a code
        // change — setprops cannot be written by an app, only read, which is what makes it a safe gate.
        if (BuildConfig.DEBUG || System.getProperty(JANK_STATS_PROPERTY) == "1") {
            JankStatsTracker.enable()
        }
        pendingDestination = intent?.getStringExtra(NotificationDeepLink.EXTRA_DESTINATION)
        preloadDictionaries()
        setContent {
            // The content is entirely ShellfStudyApp — the same composable iOS's MainViewController
            // uses. This method used to mirror it by hand, and the two drifted; see that function's
            // doc comment. Only the notification deep link, which is Android-specific, is wired here.
            ShellfStudyApp(
                pendingDestination = pendingDestination,
                onPendingDestinationConsumed = { pendingDestination = null }
            )
        }
    }

    /**
     * Parses the bundled dictionaries ahead of first use: the ~8MB stroke-order one before the first
     * subject detail sheet opens (a ~600ms parse otherwise paid inline, see StrokeOrderRepository), and
     * the ~1MB pitch-accent one before the first reading answer is graded (see PitchAccentBundledSource).
     *
     * Here rather than in Application.onCreate, which also runs for every process started only to run
     * a background worker — hourly sync, review notifications, the outbox — none of which read either.
     * Both preloads are no-ops once loaded, so a recreated Activity costs nothing.
     */
    private fun preloadDictionaries() {
        val applicationScope: CoroutineScope = get(APPLICATION_SCOPE)
        val strokeOrderRepository: StrokeOrderRepository = get()
        val pitchAccentBundledSource: PitchAccentBundledSource = get()
        applicationScope.launch { strokeOrderRepository.preload() }
        applicationScope.launch { pitchAccentBundledSource.preload() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDestination = intent.getStringExtra(NotificationDeepLink.EXTRA_DESTINATION)
    }

    // Started in onResume rather than onCreate: JankStats reads window.peekDecorView() when it is
    // constructed, and before the first resume the decor view may not be attached yet.
    override fun onResume() {
        super.onResume()
        JankStatsTracker.start(this)
    }

    override fun onDestroy() {
        // One tracker per live window; without this a recreated Activity would find the tracker still
        // installed and refuse to re-register, silently recording nothing.
        if (isFinishing) JankStatsTracker.stop()
        super.onDestroy()
    }

    private companion object {
        /** Read-only inside the app, so it cannot be turned on from the app itself. */
        const val JANK_STATS_PROPERTY = "debug.shellfstudy.jankstats"
    }
}
