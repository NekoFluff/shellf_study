package com.crazyfluff.shellfstudy

import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins MainActivity to a single instance.
 *
 * Under the default "standard" launch mode, a task started by a notification tap (an intent that isn't
 * MAIN/LAUNCHER) got a fresh MainActivity stacked on it by every later launcher tap. Each hidden copy
 * kept its review ViewModel alive, and because the session clock follows the process-wide foreground
 * state, all of them logged study time at once (8.8h recorded for ~5h studied) and each saved its own
 * stale session over the live one, so resuming brought back an old session. Robolectric doesn't model
 * task stacking, so this guards the manifest attribute that prevents it.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLaunchModeTest {

    @Test
    fun `MainActivity is singleTask so launcher and notification taps reuse the one instance`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)

        assertThat(info.launchMode).isEqualTo(ActivityInfo.LAUNCH_SINGLE_TASK)
    }
}
