package com.crazyfluff.shellfstudy.feature.lastsession

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.LastSessionKind
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummary
import com.crazyfluff.shellfstudy.shared.feature.lastsession.LastSessionSummaryScreen
import com.crazyfluff.shellfstudy.shared.feature.lastsession.LastSessionSummaryScreenTestTags
import com.crazyfluff.shellfstudy.shared.feature.lastsession.LastSessionSummaryUiState
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
class LastSessionSummaryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleSummary = LastSessionSummary(
        kind = LastSessionKind.REVIEW,
        itemsCount = 10,
        correctFirstTry = 8,
        totalElapsedMs = 120_000,
        averageTimePerItemMs = 12_000,
        slowestAnswers = emptyList(),
        missedItems = emptyList(),
        completedAtMillis = 1_000L
    )

    @Test
    fun showsLoadingIndicator_whileLoading() {
        composeTestRule.setContent {
            LastSessionSummaryScreen(uiState = LastSessionSummaryUiState(isLoading = true), onBack = {})
        }

        composeTestRule.onNodeWithTag(LastSessionSummaryScreenTestTags.LOADING_INDICATOR).assertIsDisplayed()
    }

    @Test
    fun showsEmptyState_whenNoSummaryExists() {
        composeTestRule.setContent {
            LastSessionSummaryScreen(uiState = LastSessionSummaryUiState(isLoading = false, summary = null), onBack = {})
        }

        composeTestRule.onNodeWithTag(LastSessionSummaryScreenTestTags.EMPTY_TEXT).assertIsDisplayed()
    }

    @Test
    fun showsReviewSummary_withReviewedLabel_whenKindIsReview() {
        composeTestRule.setContent {
            LastSessionSummaryScreen(
                uiState = LastSessionSummaryUiState(isLoading = false, summary = sampleSummary),
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("Last review session").assertIsDisplayed()
        composeTestRule.onNodeWithText("Items reviewed: 10").assertIsDisplayed()
    }

    @Test
    fun showsLessonSummary_withLearnedLabel_whenKindIsLesson() {
        composeTestRule.setContent {
            LastSessionSummaryScreen(
                uiState = LastSessionSummaryUiState(isLoading = false, summary = sampleSummary.copy(kind = LastSessionKind.LESSON)),
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("Last lesson session").assertIsDisplayed()
        composeTestRule.onNodeWithText("Items learned: 10").assertIsDisplayed()
    }

    @Test
    fun backButton_invokesOnBack() {
        var wentBack = false
        composeTestRule.setContent {
            LastSessionSummaryScreen(
                uiState = LastSessionSummaryUiState(isLoading = false, summary = sampleSummary),
                onBack = { wentBack = true }
            )
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assert(wentBack)
    }

    @Test
    fun doneButton_invokesOnBack() {
        var wentBack = false
        composeTestRule.setContent {
            LastSessionSummaryScreen(
                uiState = LastSessionSummaryUiState(isLoading = false, summary = sampleSummary),
                onBack = { wentBack = true }
            )
        }

        composeTestRule.onNodeWithTag(LastSessionSummaryScreenTestTags.DONE_BUTTON).performScrollTo().performClick()
        assert(wentBack)
    }
}
