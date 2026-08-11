package com.crazyfluff.shellfstudy.feature.subjectdetail

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [SubjectDetailSheet] itself needs a Hilt-injected ViewModel to render, and the fakes used by
 * SubjectDetailViewModelTest live in src/test, not src/androidTest (the source sets aren't
 * shared) — so this guards the actual root cause of the back-button bug directly instead: a
 * BackHandler registered *inside* a ModalBottomSheet's content lambda must intercept system back
 * over the sheet's own dismiss-on-back behavior, because the sheet renders in its own dialog
 * window with its own OnBackPressedDispatcher (see SubjectDetailSheet.kt's BackHandler placement).
 * System back is real OS/window behavior with no Compose-native test equivalent — verified here
 * with Espresso.pressBack(), same as [com.crazyfluff.shellfstudy.MainActivityFlowTest].
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class SubjectDetailSheetBackHandlerTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun systemBack_withStackNonEmpty_popsInsteadOfDismissing() {
        var popped = false
        var dismissed = false
        composeTestRule.setContent {
            var hasHistory by remember { mutableStateOf(true) }
            ModalBottomSheet(onDismissRequest = { dismissed = true }, sheetState = rememberModalBottomSheetState()) {
                BackHandler(enabled = hasHistory) {
                    popped = true
                    hasHistory = false
                }
            }
        }

        Espresso.pressBack()
        composeTestRule.waitForIdle()

        assert(popped)
        assert(!dismissed)
    }

    @Test
    fun systemBack_withStackEmpty_fallsThroughToDismiss() {
        var popped = false
        var dismissed = false
        composeTestRule.setContent {
            ModalBottomSheet(onDismissRequest = { dismissed = true }, sheetState = rememberModalBottomSheetState()) {
                BackHandler(enabled = false) {
                    popped = true
                }
            }
        }

        Espresso.pressBack()
        composeTestRule.waitForIdle()

        assert(!popped)
        assert(dismissed)
    }
}
