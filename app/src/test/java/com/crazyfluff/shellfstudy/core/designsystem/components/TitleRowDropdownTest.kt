package com.crazyfluff.shellfstudy.core.designsystem.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.designsystem.components.TitleRowDropdown
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Covers the one behaviour both of its hosts rely on — the dashboard's leaderboard and review-forecast
 * cards used to carry separate copies of this trigger, and one had already drifted to a `TextButton`.
 * Asserting it once here is what keeps the two hosts identical; their own tests keep covering that the
 * right option list is wired to the right card.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TitleRowDropdownTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private enum class Window(val label: String) {
        WEEK("Week"),
        MONTH("Month"),
        YEAR("Year")
    }

    @Test
    fun `shows the selected label as the trigger`() {
        setDropdown(selected = Window.WEEK)

        composeTestRule.onNodeWithText("Week").assertIsDisplayed()
    }

    @Test
    fun `the trigger is described for screen readers`() {
        setDropdown(selected = Window.WEEK)

        composeTestRule.onNodeWithContentDescription("Change the window").assertIsDisplayed()
    }

    @Test
    fun `opening the menu offers every option`() {
        setDropdown(selected = Window.WEEK)

        composeTestRule.onNodeWithContentDescription("Change the window").performClick()

        // The selected label appears twice once open — as the trigger and as its own menu item.
        composeTestRule.onAllNodesWithText(Window.WEEK.label).assertCountEquals(2)
        composeTestRule.onAllNodesWithText(Window.MONTH.label).assertCountEquals(1)
        composeTestRule.onAllNodesWithText(Window.YEAR.label).assertCountEquals(1)
    }

    @Test
    fun `choosing an option reports it and closes the menu`() {
        var selected: Window? = null
        setDropdown(selected = Window.WEEK, onSelect = { selected = it })

        composeTestRule.onNodeWithContentDescription("Change the window").performClick()
        composeTestRule.onNodeWithText("Year").performClick()

        assertThat(selected).isEqualTo(Window.YEAR)
        // Collapsed again: the trigger still shows its own (unchanged) label, and the menu is gone.
        composeTestRule.onNodeWithText("Week").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Month").assertCountEquals(0)
    }

    private fun setDropdown(selected: Window, onSelect: (Window) -> Unit = {}) {
        composeTestRule.setContent {
            TitleRowDropdown(
                selected = selected,
                options = Window.entries,
                labelOf = { it.label },
                onSelect = onSelect,
                contentDescription = "Change the window"
            )
        }
    }
}
