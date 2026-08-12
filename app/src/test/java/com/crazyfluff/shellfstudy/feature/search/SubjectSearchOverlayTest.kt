package com.crazyfluff.shellfstudy.feature.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.core.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.core.network.SubjectType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Runs under Robolectric (JVM) — this screen is driven purely by state, no device features needed.
 * Pinned to SDK 35: Robolectric 4.15.1 doesn't yet have shadows for this project's targetSdk (37).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SubjectSearchOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleResult = SubjectSummary(
        subjectId = 440,
        subjectType = SubjectType.KANJI,
        characters = "水",
        level = 3,
        meanings = listOf("Water"),
        readings = listOf("みず")
    )

    @Test
    fun inactive_rendersNothing() {
        composeTestRule.setContent {
            SubjectSearchOverlay(
                active = false,
                onActiveChange = {},
                uiState = SearchUiState(),
                onQueryChange = {},
                modifier = Modifier.fillMaxSize()
            )
        }

        composeTestRule.onAllNodesWithTag(SearchOverlayTestTags.QUERY_FIELD).assertCountEquals(0)
    }

    @Test
    fun active_showsQueryField() {
        composeTestRule.setContent {
            SubjectSearchOverlay(
                active = true,
                onActiveChange = {},
                uiState = SearchUiState(),
                onQueryChange = {},
                modifier = Modifier.fillMaxSize()
            )
        }

        composeTestRule.onNodeWithTag(SearchOverlayTestTags.QUERY_FIELD).assertIsDisplayed()
    }

    @Test
    fun typingQuery_invokesCallback() {
        var typed = ""
        composeTestRule.setContent {
            SubjectSearchOverlay(
                active = true,
                onActiveChange = {},
                uiState = SearchUiState(),
                onQueryChange = { typed = it },
                modifier = Modifier.fillMaxSize()
            )
        }

        composeTestRule.onNodeWithTag(SearchOverlayTestTags.QUERY_FIELD).performTextInput("water")
        assert(typed == "water")
    }

    @Test
    fun blankQuery_showsEmptyStateHint() {
        composeTestRule.setContent {
            SubjectSearchOverlay(
                active = true,
                onActiveChange = {},
                uiState = SearchUiState(query = ""),
                onQueryChange = {},
                modifier = Modifier.fillMaxSize()
            )
        }

        // The results slot renders inside the SearchBar's own Popup, whose reported bounds can be
        // unreliable in a bare compose-rule test host (no full Activity window) — assertExists is
        // the correct check here, per Compose's own guidance for Popup/Dialog content.
        composeTestRule.onNodeWithTag(SearchOverlayTestTags.EMPTY_STATE).assertExists()
    }

    @Test
    fun queryWithNoResults_showsNoResultsMessage() {
        composeTestRule.setContent {
            SubjectSearchOverlay(
                active = true,
                onActiveChange = {},
                uiState = SearchUiState(query = "xyz", results = emptyList()),
                onQueryChange = {},
                modifier = Modifier.fillMaxSize()
            )
        }

        composeTestRule.onNodeWithTag(SearchOverlayTestTags.NO_RESULTS).assertExists()
    }

    @Test
    fun queryWithResults_showsResultRow() {
        composeTestRule.setContent {
            SubjectSearchOverlay(
                active = true,
                onActiveChange = {},
                uiState = SearchUiState(query = "water", results = listOf(sampleResult)),
                onQueryChange = {},
                modifier = Modifier.fillMaxSize()
            )
        }

        composeTestRule.onNodeWithTag(SearchOverlayTestTags.RESULT_ROW_PREFIX + "440").assertExists()
    }

    @Test
    fun closeButton_invokesOnActiveChangeFalse() {
        var active = true
        composeTestRule.setContent {
            SubjectSearchOverlay(
                active = active,
                onActiveChange = { active = it },
                uiState = SearchUiState(),
                onQueryChange = {},
                modifier = Modifier.fillMaxSize()
            )
        }

        composeTestRule.onNodeWithTag(SearchOverlayTestTags.CLOSE_BUTTON).performClick()
        assert(!active)
    }
}
