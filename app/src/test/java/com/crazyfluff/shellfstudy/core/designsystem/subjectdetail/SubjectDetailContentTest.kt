package com.crazyfluff.shellfstudy.core.designsystem.subjectdetail

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.model.ContextSentence
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.DetailQuestionType
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.DetailRevealMode
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.SubjectDetailContent
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.SubjectDetailTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.SubjectStatsTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.text.ContextSentenceRowTestTags
import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import com.crazyfluff.shellfstudy.shared.data.model.StrokeOrderStroke
import com.crazyfluff.shellfstudy.shared.data.model.SubjectAssignmentStats
import com.crazyfluff.shellfstudy.shared.data.model.SubjectDetail
import com.crazyfluff.shellfstudy.shared.data.model.SubjectReviewStats
import com.crazyfluff.shellfstudy.shared.data.model.SubjectSummary
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import com.crazyfluff.shellfstudy.shared.designsystem.strokeorder.StrokeOrderTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.strokeorder.StrokeOrderUiState
import com.crazyfluff.shellfstudy.shared.designsystem.writing.WritingPracticeTestTags
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SubjectDetailContentTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val detail = SubjectDetail(
        subjectId = 440,
        subjectType = SubjectType.KANJI,
        characters = "水",
        characterImageUrl = null,
        level = 3,
        meanings = listOf("Water"),
        auxiliaryMeanings = emptyList(),
        readings = listOf("みず"),
        documentUrl = null,
        meaningMnemonic = "Looks like flowing water.",
        meaningHint = null,
        readingMnemonic = "Sounds like mee-zoo.",
        readingHint = null,
        partsOfSpeech = emptyList(),
        contextSentences = emptyList(),
        componentSubjectIds = listOf(1),
        amalgamationSubjectIds = emptyList(),
        visuallySimilarSubjectIds = emptyList()
    )

    private val componentTile = SubjectSummary(
        subjectId = 1,
        subjectType = SubjectType.RADICAL,
        characters = "氵",
        level = 1,
        meanings = listOf("Water radical"),
        readings = emptyList()
    )

    @Test
    fun auxiliaryMeaningsUnderCap_showsAllWithoutTruncation() {
        val vocabDetail = detail.copy(auxiliaryMeanings = listOf("Aqua", "H2O"))
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = vocabDetail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {}
            )
        }

        composeTestRule.onNodeWithText("Aqua, H2O").assertIsDisplayed()
    }

    @Test
    fun auxiliaryMeaningsOverCap_tapExpandsThenCollapses() {
        val vocabDetail = detail.copy(auxiliaryMeanings = listOf("A", "B", "C", "D", "E"))
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = vocabDetail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {}
            )
        }

        composeTestRule.onNodeWithText("A, B, C +2 more").assertIsDisplayed()

        composeTestRule.onNodeWithTag(SubjectDetailTestTags.AUXILIARY_MEANINGS_TEXT).performClick()
        composeTestRule.onNodeWithText("A, B, C, D, E").assertIsDisplayed()

        composeTestRule.onNodeWithTag(SubjectDetailTestTags.AUXILIARY_MEANINGS_TEXT).performClick()
        composeTestRule.onNodeWithText("A, B, C +2 more").assertIsDisplayed()
    }

    @Test
    fun fullMode_showsBothMeaningAndReading() {
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = detail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {}
            )
        }

        composeTestRule.onNodeWithText("Water").assertIsDisplayed()
        composeTestRule.onNodeWithText("みず").assertIsDisplayed()
    }

    @Test
    fun hideUntilAnswered_revealsOnlyTheFieldJustAnswered_reading() {
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = detail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.HIDE_UNTIL_ANSWERED,
                isAnswered = true,
                questionType = DetailQuestionType.READING,
                onRelatedSubjectClick = {}
            )
        }

        // Reading is what was just answered, so it — and its mnemonic — reveal; meaning (the
        // field not being tested right now) stays hidden.
        composeTestRule.onAllNodesWithText("Water").assertCountEquals(0)
        composeTestRule.onNodeWithText("みず").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sounds like mee-zoo.").assertIsDisplayed()
    }

    @Test
    fun hideUntilAnswered_revealsOnlyTheFieldJustAnswered_meaning() {
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = detail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.HIDE_UNTIL_ANSWERED,
                isAnswered = true,
                questionType = DetailQuestionType.MEANING,
                onRelatedSubjectClick = {}
            )
        }

        // Meaning is what was just answered, so it reveals; reading stays hidden.
        composeTestRule.onNodeWithText("Water").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("みず").assertCountEquals(0)
    }

    @Test
    fun hideUntilAnswered_beforeAnswering_hidesEverything() {
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = detail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.HIDE_UNTIL_ANSWERED,
                isAnswered = false,
                questionType = DetailQuestionType.READING,
                onRelatedSubjectClick = {}
            )
        }

        composeTestRule.onAllNodesWithText("Water").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("みず").assertCountEquals(0)
    }

    @Test
    fun kanjiWithReadingTypes_showsOnyomiAndKunyomiAsSeparateLabeledRows() {
        val kanjiDetail = detail.copy(
            readings = listOf("スイ", "みず"),
            onyomiReadings = listOf("スイ"),
            kunyomiReadings = listOf("みず")
        )
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = kanjiDetail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {}
            )
        }

        composeTestRule.onNodeWithText("On'yomi").assertIsDisplayed()
        composeTestRule.onNodeWithText("スイ").assertIsDisplayed()
        // Reading now sits below the meaning card (including its mnemonic prose), so on a
        // Robolectric-sized window these rows can land past the fold — scroll them into view first,
        // same as a real user would.
        composeTestRule.onNodeWithText("Kun'yomi").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("みず").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun vocabularyReadings_stayFlat_noOnyomiKunyomiLabels() {
        val vocabDetail = detail.copy(subjectType = SubjectType.VOCABULARY, readings = listOf("みず"))
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = vocabDetail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {}
            )
        }

        composeTestRule.onNodeWithText("みず").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("On'yomi").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Kun'yomi").assertCountEquals(0)
    }

    @Test
    fun vocabularyWithPitchAccentData_showsTheDiagramWhenEnabled() {
        val vocabDetail = detail.copy(
            subjectType = SubjectType.VOCABULARY,
            readings = listOf("みず"),
            pitchAccents = listOf(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0))
        )
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = vocabDetail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {},
                showPitchAccent = true
            )
        }

        composeTestRule.onNodeWithTag(PitchAccentTestTags.DIAGRAM).assertIsDisplayed()
        composeTestRule.onNodeWithText("みず").assertIsDisplayed()
    }

    @Test
    fun vocabularyWithPitchAccentData_hidesTheDiagramWhenSettingDisabled() {
        val vocabDetail = detail.copy(
            subjectType = SubjectType.VOCABULARY,
            readings = listOf("みず"),
            pitchAccents = listOf(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0))
        )
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = vocabDetail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {},
                showPitchAccent = false
            )
        }

        composeTestRule.onAllNodesWithTag(PitchAccentTestTags.DIAGRAM).assertCountEquals(0)
        composeTestRule.onNodeWithText("みず").assertIsDisplayed()
    }

    @Test
    fun vocabularyWithNoPitchAccentData_staysPlainTextEvenWhenEnabled() {
        val vocabDetail = detail.copy(subjectType = SubjectType.VOCABULARY, readings = listOf("みず"), pitchAccents = emptyList())
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = vocabDetail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {},
                showPitchAccent = true
            )
        }

        composeTestRule.onAllNodesWithTag(PitchAccentTestTags.DIAGRAM).assertCountEquals(0)
        composeTestRule.onNodeWithText("みず").assertIsDisplayed()
    }

    @Test
    fun vocabularyWithPronunciationAudio_showsPlayButtonThatInvokesCallbackWithTheReading() {
        var playedReading: String? = null
        val vocabDetail = detail.copy(
            subjectType = SubjectType.VOCABULARY,
            readings = listOf("みず"),
            pronunciationAudios = listOf(
                PronunciationAudio(
                    url = "https://example.com/mizu.mp3",
                    contentType = "audio/mpeg",
                    pronunciation = "みず",
                    gender = null,
                    voiceActorId = null,
                    voiceActorName = null,
                    voiceDescription = null
                )
            )
        )
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = vocabDetail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {},
                onPlayReading = { playedReading = it }
            )
        }

        composeTestRule.onNodeWithContentDescription("Play pronunciation for みず").performClick()
        assertThat(playedReading).isEqualTo("みず")
    }

    @Test
    fun vocabularyWithPitchAccentAndAudio_showsBothTheDiagramAndThePlayButton() {
        var playedReading: String? = null
        val vocabDetail = detail.copy(
            subjectType = SubjectType.VOCABULARY,
            readings = listOf("みず"),
            pitchAccents = listOf(PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0)),
            pronunciationAudios = listOf(
                PronunciationAudio(
                    url = "https://example.com/mizu.mp3",
                    contentType = "audio/mpeg",
                    pronunciation = "みず",
                    gender = null,
                    voiceActorId = null,
                    voiceActorName = null,
                    voiceDescription = null
                )
            )
        )
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = vocabDetail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {},
                showPitchAccent = true,
                onPlayReading = { playedReading = it }
            )
        }

        composeTestRule.onNodeWithTag(PitchAccentTestTags.DIAGRAM).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Play pronunciation for みず").performClick()
        assertThat(playedReading).isEqualTo("みず")
    }

    @Test
    fun vocabularyWithNoPronunciationAudio_showsNoPlayButton() {
        val vocabDetail = detail.copy(subjectType = SubjectType.VOCABULARY, readings = listOf("みず"), pronunciationAudios = emptyList())
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = vocabDetail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {},
                onPlayReading = {}
            )
        }

        composeTestRule.onAllNodesWithContentDescription("Play pronunciation for みず").assertCountEquals(0)
    }

    @Test
    fun strokeOrderAvailable_showsTheSection() {
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = detail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {},
                strokeOrder = StrokeOrderUiState.Available(
                    listOf(StrokeOrderStroke(pathData = "M10,10L90,90", labelX = 5f, labelY = 5f))
                )
            )
        }

        composeTestRule.onNodeWithTag(StrokeOrderTestTags.SECTION).assertIsDisplayed()
        composeTestRule.onNodeWithTag(StrokeOrderTestTags.DIAGRAM).assertIsDisplayed()
    }

    @Test
    fun strokeOrderAvailable_autoPlayFalse_stillShowsTheSection() {
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = detail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {},
                strokeOrder = StrokeOrderUiState.Available(
                    listOf(StrokeOrderStroke(pathData = "M10,10L90,90", labelX = 5f, labelY = 5f))
                ),
                autoPlayStrokeOrder = false
            )
        }

        composeTestRule.onNodeWithTag(StrokeOrderTestTags.SECTION).assertIsDisplayed()
        composeTestRule.onNodeWithTag(StrokeOrderTestTags.DIAGRAM).assertIsDisplayed()
    }

    @Test
    fun strokeOrderUnavailable_hidesTheSection() {
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = detail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {},
                strokeOrder = StrokeOrderUiState.Unavailable
            )
        }

        composeTestRule.onAllNodesWithTag(StrokeOrderTestTags.SECTION).assertCountEquals(0)
    }

    @Test
    fun writingPracticeAvailable_showsTheSection() {
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = detail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {},
                strokeOrder = StrokeOrderUiState.Available(
                    listOf(StrokeOrderStroke(pathData = "M10,10L90,90", labelX = 5f, labelY = 5f))
                )
            )
        }

        composeTestRule.onNodeWithTag(WritingPracticeTestTags.SECTION).assertIsDisplayed()
    }

    @Test
    fun writingPracticeUnavailable_hidesTheSection() {
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = detail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {},
                strokeOrder = StrokeOrderUiState.Unavailable
            )
        }

        composeTestRule.onAllNodesWithTag(WritingPracticeTestTags.SECTION).assertCountEquals(0)
    }

    @Test
    fun relatedSubjectTile_invokesCallbackWithItsId() {
        var clicked: Long? = null
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = detail,
                relatedSubjects = mapOf(1L to componentTile),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = { clicked = it }
            )
        }

        composeTestRule.onNodeWithText("Water radical").performClick()
        assert(clicked == 1L)
    }

    private val lessonedAssignmentStats = SubjectAssignmentStats(
        srsStage = SrsStage.GURU_1,
        nextReviewAt = Clock.System.now() + 3.hours,
        unlockedAt = Instant.parse("2026-01-02T00:00:00.000000Z"),
        startedAt = Instant.parse("2026-01-03T00:00:00.000000Z"),
        passedAt = Instant.parse("2026-01-20T00:00:00.000000Z"),
        burnedAt = null
    )

    @Test
    fun noAssignmentStats_hidesTheStatsSection() {
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = detail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {}
            )
        }

        composeTestRule.onAllNodesWithTag(SubjectStatsTestTags.SECTION).assertCountEquals(0)
    }

    @Test
    fun assignmentStatsPresent_showsMilestonesWithNotYetForBurned() {
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = detail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {},
                assignmentStats = lessonedAssignmentStats
            )
        }

        composeTestRule.onNodeWithTag(SubjectStatsTestTags.SECTION).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Unlocked").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Passed").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Burned").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Not yet").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun nextReviewInThePast_showsAvailableNow() {
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = detail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {},
                assignmentStats = lessonedAssignmentStats.copy(nextReviewAt = Clock.System.now() - 1.hours)
            )
        }

        composeTestRule.onNodeWithText("Available now").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun reviewStatsPresent_showsAccuracyAndStreaksPerQuestionType() {
        val reviewStats = SubjectReviewStats(
            meaningCorrect = 9, meaningIncorrect = 1, meaningCurrentStreak = 3, meaningMaxStreak = 5,
            readingCorrect = 4, readingIncorrect = 1, readingCurrentStreak = 2, readingMaxStreak = 6,
            lastReviewedAt = Instant.parse("2026-01-25T12:00:00.000000Z")
        )
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = detail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {},
                assignmentStats = lessonedAssignmentStats,
                reviewStats = reviewStats
            )
        }

        composeTestRule.onNodeWithText("90%").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("80%").assertIsDisplayed()
        composeTestRule.onNodeWithText("Streak 3 (best 5)").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Last reviewed").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun noReviewStatsYet_showsPlaceholderInsteadOfMisleadingZeroPercent() {
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = detail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {},
                assignmentStats = lessonedAssignmentStats,
                reviewStats = null
            )
        }

        composeTestRule.onAllNodesWithText("No reviews yet").assertCountEquals(2)
        composeTestRule.onAllNodesWithText("Last reviewed").assertCountEquals(0)
    }

    @Test
    fun initialScrollOffset_jumpsContentDownOnFirstComposition() {
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = detail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {},
                assignmentStats = lessonedAssignmentStats,
                initialScrollOffset = Int.MAX_VALUE / 2
            )
        }

        // With no offset (assignmentStatsPresent_showsMilestonesWithNotYetForBurned above), this
        // node needs performScrollTo() to be visible — here it should already be in view.
        composeTestRule.onNodeWithText("Unlocked").assertIsDisplayed()
    }

    @Test
    fun scrollingContent_reportsPositionViaOnScrollPositionChanged() {
        var reportedOffset = 0
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = detail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {},
                assignmentStats = lessonedAssignmentStats,
                onScrollPositionChanged = { reportedOffset = it }
            )
        }

        composeTestRule.onNodeWithTag(SubjectStatsTestTags.SECTION).performScrollTo()

        assertThat(reportedOffset).isGreaterThan(0)
    }

    @Test
    fun contextSentenceLookupButton_launchesAkebiDirectly_whenInstalled() {
        // Explicit lookup button (see ContextSentenceRow) instead of relying on long-press text
        // selection, which has to compete with this screen's own scroll gesture and is fiddly on
        // unspaced CJK text — this fires the exact ACTION_PROCESS_TEXT intent Akebi already
        // registers for, directly, with no chooser and no selection gesture.
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.craxic.akebifree"
                name = "com.craxic.akebifree.ProcessTextActivity"
            }
        }
        shadowOf(composeTestRule.activity.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").setPackage("com.craxic.akebifree"),
            resolveInfo
        )
        val vocabDetail = detail.copy(
            subjectType = SubjectType.VOCABULARY,
            readings = listOf("みず"),
            contextSentences = listOf(ContextSentence(japanese = "水を飲みます。", english = "I drink water."))
        )
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = vocabDetail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {}
            )
        }

        composeTestRule.onNodeWithTag(ContextSentenceRowTestTags.SHARE_BUTTON).performScrollTo().performClick()

        val started = shadowOf(composeTestRule.activity).nextStartedActivity
        assertThat(started.action).isEqualTo(Intent.ACTION_PROCESS_TEXT)
        assertThat(started.`package`).isEqualTo("com.craxic.akebifree")
        assertThat(started.type).isEqualTo("text/plain")
        assertThat(started.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)).isEqualTo("水を飲みます。")
        assertThat(started.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)).isTrue()
    }

    @Test
    fun contextSentenceLookupButton_opensPlayStoreListing_whenAkebiNotInstalled() {
        // No Akebi resolver registered — Robolectric resolves nothing by default, exercising the
        // "Akebi not installed" fallback path. Register just enough resolvability for the
        // web-based Play Store fallback (the market:// one is left unresolvable, same as
        // Robolectric's default) so the fallback chain lands somewhere deterministic to assert on.
        val playStoreResolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.android.browser"
                name = "com.android.browser.BrowserActivity"
            }
        }
        shadowOf(composeTestRule.activity.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.craxic.akebifree")),
            playStoreResolveInfo
        )
        val vocabDetail = detail.copy(
            subjectType = SubjectType.VOCABULARY,
            readings = listOf("みず"),
            contextSentences = listOf(ContextSentence(japanese = "水を飲みます。", english = "I drink water."))
        )
        composeTestRule.setContent {
            SubjectDetailContent(
                detail = vocabDetail,
                relatedSubjects = emptyMap(),
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onRelatedSubjectClick = {}
            )
        }

        composeTestRule.onNodeWithTag(ContextSentenceRowTestTags.SHARE_BUTTON).performScrollTo().performClick()

        val started = shadowOf(composeTestRule.activity).nextStartedActivity
        assertThat(started.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(started.data.toString()).contains("com.craxic.akebifree")
    }
}
