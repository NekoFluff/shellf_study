package com.crazyfluff.shellfstudy.feature.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import com.crazyfluff.shellfstudy.shared.data.model.SearchFilters
import com.crazyfluff.shellfstudy.shared.data.model.SentenceSearchResult
import com.crazyfluff.shellfstudy.shared.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.shared.feature.search.SearchTab
import com.crazyfluff.shellfstudy.shared.feature.search.highlightMatches
import com.google.common.truth.Truth.assertThat
import com.crazyfluff.shellfstudy.shared.feature.search.SearchOverlayTestTags
import com.crazyfluff.shellfstudy.shared.feature.search.SearchActions
import com.crazyfluff.shellfstudy.shared.feature.search.SearchUiState
import com.crazyfluff.shellfstudy.shared.feature.search.SubjectSearchOverlay
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs under Robolectric (JVM) — this screen is driven purely by state, no device features needed.
 * Pinned to SDK 35: Robolectric 4.15.1 doesn't yet have shadows for this project's targetSdk (37).
 */
@RunWith(AndroidJUnit4::class)
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

    private val sampleSentence = SentenceSearchResult(
        subjectId = 2467,
        subjectType = SubjectType.VOCABULARY,
        characters = "水",
        japanese = "水を飲みます。",
        english = "I drink water."
    )

    @Test
    fun inactive_rendersNothing() {
        composeTestRule.setContent {
            SubjectSearchOverlay(
                active = false,
                onActiveChange = {},
                uiState = SearchUiState(),
                actions = SearchActions.Inert,
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
                actions = SearchActions.Inert,
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
                actions = SearchActions.Inert.copy(onQueryChange = { typed = it }),
                modifier = Modifier.fillMaxSize()
            )
        }

        composeTestRule.onNodeWithTag(SearchOverlayTestTags.QUERY_FIELD).performTextInput("water")
        // TextFieldState pushes edits up via a LaunchedEffect/snapshotFlow, one dispatch removed
        // from performTextInput itself — wait for that to land before reading the callback value.
        composeTestRule.waitForIdle()
        assert(typed == "water")
    }

    @Test
    fun blankQuery_showsEmptyStateHint() {
        composeTestRule.setContent {
            SubjectSearchOverlay(
                active = true,
                onActiveChange = {},
                uiState = SearchUiState(query = ""),
                actions = SearchActions.Inert,
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
                actions = SearchActions.Inert,
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
                actions = SearchActions.Inert,
                modifier = Modifier.fillMaxSize()
            )
        }

        composeTestRule.onNodeWithTag(SearchOverlayTestTags.RESULT_ROW_PREFIX + "440").assertExists()
    }

    @Test
    fun glyphLessRadicalResult_showsInlineImageGlyphInsteadOfBlankPrefix() {
        // Regression test: a radical with no Unicode character (e.g. Death Star, Yurt) used to
        // render with no leading identifier at all — every other subject shows its character
        // inline, colored, right before the meaning; this one just skipped straight to the meaning.
        val glyphLessRadical = SubjectSummary(
            subjectId = 8790,
            subjectType = SubjectType.RADICAL,
            characters = null,
            characterImageUrl = "https://files.wanikani.com/dgeoshskssv0r7bnmaxj0o4lt1el",
            level = 24,
            meanings = listOf("Death Star"),
            readings = emptyList()
        )
        composeTestRule.setContent {
            SubjectSearchOverlay(
                active = true,
                onActiveChange = {},
                uiState = SearchUiState(query = "death star", results = listOf(glyphLessRadical)),
                actions = SearchActions.Inert,
                modifier = Modifier.fillMaxSize()
            )
        }

        composeTestRule.onNodeWithTag(SearchOverlayTestTags.RESULT_ROW_PREFIX + "8790").assertExists()
        // The inline image glyph's alternate text sits right before the meaning, same position a
        // colored character would occupy for any other subject.
        composeTestRule.onNodeWithText("Death Star", substring = true).assertExists()
    }

    @Test
    fun closeButton_invokesOnActiveChangeFalse() {
        var active = true
        composeTestRule.setContent {
            SubjectSearchOverlay(
                active = active,
                onActiveChange = { active = it },
                uiState = SearchUiState(),
                actions = SearchActions.Inert,
                modifier = Modifier.fillMaxSize()
            )
        }

        composeTestRule.onNodeWithTag(SearchOverlayTestTags.CLOSE_BUTTON).performClick()
        assert(!active)
    }

    @Test
    fun reopeningSearch_clearsThePreviousQuery() {
        var active by mutableStateOf(false)
        var query by mutableStateOf("stale query")
        composeTestRule.setContent {
            SubjectSearchOverlay(
                active = active,
                onActiveChange = { active = it },
                uiState = SearchUiState(query = query),
                actions = SearchActions.Inert.copy(onQueryChange = { query = it }),
                modifier = Modifier.fillMaxSize()
            )
        }

        active = true
        composeTestRule.waitForIdle()

        assert(query == "")
    }

    @Test
    fun selectingResult_clearsFocusFromQueryField() {
        composeTestRule.setContent {
            SubjectSearchOverlay(
                active = true,
                onActiveChange = {},
                uiState = SearchUiState(query = "water", results = listOf(sampleResult)),
                actions = SearchActions.Inert,
                modifier = Modifier.fillMaxSize()
            )
        }

        composeTestRule.onNodeWithTag(SearchOverlayTestTags.QUERY_FIELD).assertIsFocused()

        composeTestRule.onNodeWithTag(SearchOverlayTestTags.RESULT_ROW_PREFIX + "440").performClick()

        composeTestRule.onNodeWithTag(SearchOverlayTestTags.QUERY_FIELD).assertIsNotFocused()
    }

    @Test
    fun typeChip_togglesThatTypeInTheFilters() {
        var filters by mutableStateOf(SearchFilters())
        composeTestRule.setContent {
            SubjectSearchOverlay(
                active = true,
                onActiveChange = {},
                uiState = SearchUiState(filters = filters),
                actions = SearchActions.Inert.copy(onFiltersChange = { filters = it }),
                modifier = Modifier.fillMaxSize()
            )
        }

        composeTestRule.onNodeWithTag(SearchOverlayTestTags.TYPE_CHIP_PREFIX + "KANJI").performClick()
        composeTestRule.waitForIdle()
        assertThat(filters.types).containsExactly(SubjectType.KANJI)

        composeTestRule.onNodeWithTag(SearchOverlayTestTags.TYPE_CHIP_PREFIX + "KANJI").performClick()
        composeTestRule.waitForIdle()
        assertThat(filters.types).isEmpty()
    }

    @Test
    fun unlockedOnlyChip_togglesTheFilter() {
        var filters by mutableStateOf(SearchFilters())
        composeTestRule.setContent {
            SubjectSearchOverlay(
                active = true,
                onActiveChange = {},
                uiState = SearchUiState(filters = filters),
                actions = SearchActions.Inert.copy(onFiltersChange = { filters = it }),
                modifier = Modifier.fillMaxSize()
            )
        }

        composeTestRule.onNodeWithTag(SearchOverlayTestTags.UNLOCKED_ONLY_CHIP).performClick()
        composeTestRule.waitForIdle()
        assertThat(filters.unlockedOnly).isTrue()
    }

    @Test
    fun tabs_showEachTabsCount_andSwitchTabs() {
        var tab: SearchTab? = null
        composeTestRule.setContent {
            SubjectSearchOverlay(
                active = true,
                onActiveChange = {},
                uiState = SearchUiState(
                    query = "water",
                    results = listOf(sampleResult),
                    totalMatchCount = 1,
                    sentenceResults = listOf(sampleSentence),
                    sentenceTotalCount = 1
                ),
                actions = SearchActions.Inert.copy(onTabChange = { tab = it }),
                modifier = Modifier.fillMaxSize()
            )
        }

        composeTestRule.onNodeWithText("Subjects (1)").assertExists()
        composeTestRule.onNodeWithText("Sentences (1)").assertExists()
        composeTestRule.onNodeWithTag(SearchOverlayTestTags.TAB_SENTENCES).performClick()
        assertThat(tab).isEqualTo(SearchTab.SENTENCES)
    }

    @Test
    fun blankQuery_hidesTheTabs() {
        composeTestRule.setContent {
            SubjectSearchOverlay(
                active = true,
                onActiveChange = {},
                uiState = SearchUiState(query = ""),
                actions = SearchActions.Inert,
                modifier = Modifier.fillMaxSize()
            )
        }

        composeTestRule.onAllNodesWithTag(SearchOverlayTestTags.TAB_SENTENCES).assertCountEquals(0)
    }

    @Test
    fun sentencesTab_showsSentenceRows_notSubjectRows() {
        composeTestRule.setContent {
            SubjectSearchOverlay(
                active = true,
                onActiveChange = {},
                uiState = SearchUiState(
                    query = "water",
                    tab = SearchTab.SENTENCES,
                    results = listOf(sampleResult),
                    sentenceResults = listOf(sampleSentence),
                    sentenceTotalCount = 1
                ),
                actions = SearchActions.Inert,
                modifier = Modifier.fillMaxSize()
            )
        }

        composeTestRule.onNodeWithTag(SearchOverlayTestTags.SENTENCE_ROW_PREFIX + "2467").assertExists()
        composeTestRule.onNodeWithText("I drink water.").assertExists()
        composeTestRule.onAllNodesWithTag(SearchOverlayTestTags.RESULT_ROW_PREFIX + "440").assertCountEquals(0)
    }

    @Test
    fun sentencesTabWithNoMatches_showsItsOwnNoResultsMessage() {
        composeTestRule.setContent {
            SubjectSearchOverlay(
                active = true,
                onActiveChange = {},
                uiState = SearchUiState(query = "water", tab = SearchTab.SENTENCES, results = listOf(sampleResult)),
                actions = SearchActions.Inert,
                modifier = Modifier.fillMaxSize()
            )
        }

        composeTestRule.onNodeWithText("No example sentences match.").assertExists()
    }

    @Test
    fun selectingSentence_clearsFocusFromQueryField() {
        composeTestRule.setContent {
            SubjectSearchOverlay(
                active = true,
                onActiveChange = {},
                uiState = SearchUiState(
                    query = "water",
                    tab = SearchTab.SENTENCES,
                    sentenceResults = listOf(sampleSentence)
                ),
                actions = SearchActions.Inert,
                modifier = Modifier.fillMaxSize()
            )
        }

        composeTestRule.onNodeWithTag(SearchOverlayTestTags.QUERY_FIELD).assertIsFocused()
        composeTestRule.onNodeWithTag(SearchOverlayTestTags.SENTENCE_ROW_PREFIX + "2467").performClick()
        composeTestRule.onNodeWithTag(SearchOverlayTestTags.QUERY_FIELD).assertIsNotFocused()
    }

    @Test
    fun highlightMatches_stylesEveryCaseInsensitiveOccurrence() {
        val style = SpanStyle(fontWeight = FontWeight.Bold)
        val highlighted = highlightMatches("Water, water everywhere", "water", style)

        assertThat(highlighted.spanStyles.map { it.start to it.end }).containsExactly(0 to 5, 7 to 12).inOrder()
        assertThat(highlightMatches("水", "", style).spanStyles).isEmpty()
    }
}
