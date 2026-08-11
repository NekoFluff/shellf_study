package com.crazyfluff.shellfstudy.feature.search

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.espresso.NoActivityResumedException
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * System back is real OS/window behavior that Compose has no native equivalent for testing —
 * verified here with Espresso.pressBack(), same as [com.crazyfluff.shellfstudy.MainActivityFlowTest].
 */
@RunWith(AndroidJUnit4::class)
class SubjectSearchOverlayBackHandlerTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun systemBack_whileActive_closesOverlayInsteadOfLeavingScreen() {
        var active = true
        composeTestRule.setContent {
            SubjectSearchOverlay(
                active = active,
                onActiveChange = { active = it },
                uiState = SearchUiState(),
                onQueryChange = {}
            )
        }

        Espresso.pressBack()

        composeTestRule.waitForIdle()
        assert(!active)
    }

    @Test
    fun systemBack_whileInactive_isNotConsumedByTheOverlay() {
        var active = false
        composeTestRule.setContent {
            SubjectSearchOverlay(
                active = active,
                onActiveChange = { active = it },
                uiState = SearchUiState(),
                onQueryChange = {}
            )
        }

        // With active == false the overlay's BackHandler must be disabled, so pressBack() falls
        // through past it to the bare host activity's default handling — which, with nothing else
        // on the back stack, exits the activity. NoActivityResumedException is Espresso's normal
        // signal for that case, same as MainActivityFlowTest's root-screen back-press assertion;
        // any other exception here would mean a real crash and should still fail the test.
        val fellThroughToActivity = try {
            Espresso.pressBack()
            false
        } catch (expected: NoActivityResumedException) {
            true
        }

        assert(fellThroughToActivity)
    }
}
