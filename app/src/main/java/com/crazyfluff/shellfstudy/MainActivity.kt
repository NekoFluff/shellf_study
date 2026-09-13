package com.crazyfluff.shellfstudy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
}
