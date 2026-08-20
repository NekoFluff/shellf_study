package com.crazyfluff.shellfstudy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.crazyfluff.shellfstudy.shared.ThemeViewModel
import com.crazyfluff.shellfstudy.shared.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.shared.notifications.NotificationDeepLink
import com.crazyfluff.shellfstudy.shared.navigation.ShellfStudyNavHost
import com.crazyfluff.shellfstudy.shared.data.ThemeMode
import org.koin.androidx.compose.koinViewModel

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
            val themeViewModel: ThemeViewModel = koinViewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.EINK -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            ShellfStudyTheme(themeMode = themeMode, darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ShellfStudyNavHost(
                        pendingDestination = pendingDestination,
                        onPendingDestinationConsumed = { pendingDestination = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDestination = intent.getStringExtra(NotificationDeepLink.EXTRA_DESTINATION)
    }
}
