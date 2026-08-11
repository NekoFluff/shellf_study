package com.crazyfluff.shellfstudy.core.designsystem.subjectdetail

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.core.data.model.SubjectDetail
import com.crazyfluff.shellfstudy.core.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.core.network.SubjectType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SubjectDetailContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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
    fun hideUntilAnswered_hidesTheFieldCurrentlyBeingTested() {
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

        // Reading is being tested, so it — and its mnemonic — stay hidden; meaning is free to show.
        composeTestRule.onNodeWithText("Water").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("みず").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Sounds like mee-zoo.").assertCountEquals(0)
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
        composeTestRule.onNodeWithText("Kun'yomi").assertIsDisplayed()
        composeTestRule.onNodeWithText("みず").assertIsDisplayed()
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
}
