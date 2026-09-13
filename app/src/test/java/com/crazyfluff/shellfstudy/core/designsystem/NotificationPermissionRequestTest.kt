package com.crazyfluff.shellfstudy.core.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.designsystem.LocalNotificationPermissionRequest
import com.crazyfluff.shellfstudy.shared.designsystem.rememberPermissionRequest
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Covers the override half of the notification-permission seam: that a provided factory is what the
 * app actually asks, which is otherwise unassertable — the ask ends in a platform launcher.
 *
 * The composition *not crashing* is itself part of the assertion. `createComposeRule` supplies no
 * `ActivityResultRegistryOwner`, so if `rememberPermissionRequest` instantiated the platform
 * implementation even when an override is present, this test would fail rather than pass quietly.
 *
 * This stands in for driving `SettingsRoute`'s switch, which would need the whole Koin graph for one
 * line of glue; the glue lives in `rememberPermissionRequest`, so that is where it is pinned.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NotificationPermissionRequestTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `a provided request factory is what the app asks with`() {
        var asks = 0
        var reportedGrant: Boolean? = null
        lateinit var trigger: () -> Unit

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalNotificationPermissionRequest provides { onResult ->
                    {
                        asks++
                        onResult(true)
                    }
                }
            ) {
                trigger = rememberPermissionRequest { granted -> reportedGrant = granted }
            }
        }

        composeTestRule.runOnIdle { trigger() }

        assertThat(asks).isEqualTo(1)
        assertThat(reportedGrant).isTrue()
    }

    @Test
    fun `the factory is asked for a trigger per composition rather than reusing one`() {
        // The trigger is bound to the caller's result callback, so the local holds a factory rather
        // than a trigger. Two different callers must get two different triggers wired to their own
        // callbacks — a single shared trigger would silently route the result to whichever caller
        // registered first.
        val reported = mutableListOf<String>()
        lateinit var first: () -> Unit
        lateinit var second: () -> Unit

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalNotificationPermissionRequest provides { onResult -> { onResult(true) } }
            ) {
                first = rememberPermissionRequest { reported += "first" }
                second = rememberPermissionRequest { reported += "second" }
            }
        }

        composeTestRule.runOnIdle {
            first()
            second()
        }

        assertThat(reported).containsExactly("first", "second").inOrder()
    }
}
