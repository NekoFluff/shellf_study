package com.crazyfluff.shellfstudy.feature.lastsession

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.fakes.FakePronunciationAudioPlayer
import com.crazyfluff.shellfstudy.fakes.FakeStrokeOrderRepository
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.shared.data.LastSessionKind
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummary
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.StrokeOrderRepository
import com.crazyfluff.shellfstudy.shared.data.model.SessionMissedItemRow
import com.crazyfluff.shellfstudy.shared.database.SubjectEntity
import com.crazyfluff.shellfstudy.shared.feature.lastsession.LastSessionSummaryScreen
import com.crazyfluff.shellfstudy.shared.feature.lastsession.LastSessionSummaryScreenTestTags
import com.crazyfluff.shellfstudy.shared.feature.lastsession.LastSessionSummaryUiState
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.SubjectDetailViewModel
import com.crazyfluff.shellfstudy.shared.network.MeaningData
import com.crazyfluff.shellfstudy.shared.network.ReadingData
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
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

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer

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

    /**
     * The subject-detail sheet embedded in this screen resolves its ViewModel via `koinViewModel()`,
     * so tests that open it need a real (fake-backed) Koin instance — see
     * [closingSheet_unComposesScrimQuickly_ratherThanWaitingForCloseAnimationToSettle] below. Guard against
     * `ShellfStudyApplication`'s own `onCreate` having already started Koin with the real modules
     * for this or a prior Robolectric-backed test in the same JVM.
     */
    @Before
    fun setUpKoin() {
        if (GlobalContext.getOrNull() != null) stopKoin()

        server = MockWebServer()
        server.start()
        val repositories = buildTestRepositories(server.url("/").toString())
        runBlocking {
            repositories.subjectDao.upsertAll(
                listOf(
                    SubjectEntity(
                        id = 1,
                        subjectType = "kanji",
                        level = 1,
                        slug = "水",
                        characters = "水",
                        meanings = listOf(MeaningData(meaning = "Water", primary = true)),
                        readings = listOf(ReadingData(reading = "みず", primary = true)),
                        documentUrl = null,
                        searchTarget = "水 water"
                    )
                )
            )
        }
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob()),
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )

        startKoin {
            modules(
                module {
                    single { repositories.subjectRepository }
                    single { repositories.assignmentRepository }
                    single { repositories.statsRepository }
                    single { SettingsRepository(dataStore) }
                    single<PronunciationAudioPlayer> { FakePronunciationAudioPlayer() }
                    single<StrokeOrderRepository> { FakeStrokeOrderRepository(emptyMap()) }
                    viewModel {
                        SubjectDetailViewModel(get(), get(), get(), get(), get(), get())
                    }
                }
            )
        }
    }

    @After
    fun tearDownKoin() {
        stopKoin()
        server.shutdown()
    }

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

    /**
     * Regression test for a real dropped-tap bug: on this screen, dismissing the subject-detail
     * sheet used to leave its fullscreen scrim click-enabled for the entire close animation (up to
     * several hundred ms), silently swallowing a "Back to dashboard" tap that landed in that window.
     * The fix threads `active` through [SubjectDetailSheetHost] so the scrim/body un-compose as soon
     * as the user's dismiss intent is registered, rather than waiting for the animation to settle.
     *
     * This asserts that mechanism directly: after dismissing, the sheet's interactive content
     * disappears within a handful of frames — dramatically faster than the close animation's own
     * completion (which a separate manual check, forcing `active = true` to simulate the pre-fix
     * behavior, confirmed still leaves it composed well past this point). Verifying the actual tap
     * physically lands on "Back to dashboard" during that narrow window needs real device frame
     * pacing (Robolectric's touch dispatch isn't reliable for overlapping offset-animated layers);
     * that end-to-end check should be done manually per the implementation plan.
     */
    @Test
    fun closingSheet_unComposesScrimQuickly_ratherThanWaitingForCloseAnimationToSettle() {
        val summaryWithMissedItem = sampleSummary.copy(
            missedItems = listOf(SessionMissedItemRow(label = "水", subjectId = 1, subjectType = SubjectType.KANJI))
        )

        composeTestRule.setContent {
            LastSessionSummaryScreen(
                uiState = LastSessionSummaryUiState(isLoading = false, summary = summaryWithMissedItem),
                onBack = {}
            )
        }

        // Open with the clock running normally first, so the sheet's ViewModel (real coroutines on
        // real dispatchers, not the test clock) actually finishes loading — an indeterminate loading
        // spinner left running under a frozen clock never reaches "idle", hanging teardown.
        composeTestRule.onNodeWithText("水").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithContentDescription("Close").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.onNodeWithContentDescription("Close").performClick()
        // Advance one frame at a time and stop as soon as the scrim/body have un-composed — capturing
        // "as soon as the gate closes" rather than a fixed frame count keeps this deterministic
        // regardless of exactly how many frames Compose needs to propagate the state change through.
        var framesWaited = 0
        while (
            composeTestRule.onAllNodesWithContentDescription("Close").fetchSemanticsNodes().isNotEmpty() &&
            framesWaited < 10
        ) {
            composeTestRule.mainClock.advanceTimeByFrame()
            framesWaited++
        }

        assert(framesWaited < 10) {
            "sheet content was still composed $framesWaited frames after dismissal — active gating regressed"
        }
    }
}
