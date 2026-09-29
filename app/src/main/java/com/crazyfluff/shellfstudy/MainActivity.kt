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
