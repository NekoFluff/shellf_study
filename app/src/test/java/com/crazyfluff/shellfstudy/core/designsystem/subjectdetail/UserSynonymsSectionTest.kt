package com.crazyfluff.shellfstudy.core.designsystem.subjectdetail

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.fakes.FakeUserSynonymsStore
import com.crazyfluff.shellfstudy.shared.data.model.SubjectDetail
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.DetailQuestionType
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.DetailRevealMode
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.LocalUserSynonyms
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.SubjectDetailContent
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.UserSynonymsTestTags
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The synonyms row under a subject's meaning, as every subject view shows it (via the detail content). */
@RunWith(AndroidJUnit4::class)
class UserSynonymsSectionTest {

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
        meaningMnemonic = null,
        meaningHint = null,
        readingMnemonic = null,
        readingHint = null,
        partsOfSpeech = emptyList(),
        contextSentences = emptyList(),
        componentSubjectIds = emptyList(),
        amalgamationSubjectIds = emptyList(),
        visuallySimilarSubjectIds = emptyList()
    )

    private fun show(
        store: FakeUserSynonymsStore?,
        revealMode: DetailRevealMode = DetailRevealMode.FULL,
        isAnswered: Boolean = true
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalUserSynonyms provides store) {
                SubjectDetailContent(
                    detail = detail,
                    relatedSubjects = emptyMap(),
                    revealMode = revealMode,
                    isAnswered = isAnswered,
                    questionType = DetailQuestionType.MEANING,
                    onRelatedSubjectClick = {}
                )
            }
        }
    }

    @Test
    fun existingSynonymsShowAsChipsUnderTheMeaning() {
        show(FakeUserSynonymsStore(mapOf(440L to listOf("aqua", "liquid"))))

        composeTestRule.onNodeWithTag(UserSynonymsTestTags.chip("aqua")).assertIsDisplayed()
        composeTestRule.onNodeWithTag(UserSynonymsTestTags.chip("liquid")).assertIsDisplayed()
    }

    @Test
    fun theAddChipOpensADialogThatSavesTheSynonym() {
        val store = FakeUserSynonymsStore()
        show(store)

        composeTestRule.onNodeWithTag(UserSynonymsTestTags.ADD_CHIP).performClick()
        composeTestRule.onNodeWithTag(UserSynonymsTestTags.FIELD).performTextInput("big water")
        composeTestRule.onNodeWithTag(UserSynonymsTestTags.CONFIRM).performClick()
        composeTestRule.waitForIdle()

        assertThat(store.synonyms.value[440L]).containsExactly("big water")
        composeTestRule.onNodeWithTag(UserSynonymsTestTags.FIELD).assertDoesNotExist()
        composeTestRule.onNodeWithTag(UserSynonymsTestTags.chip("big water")).assertIsDisplayed()
    }

    @Test
    fun aRefusedAddStaysInTheDialogWithTheReason() {
        val store = FakeUserSynonymsStore().apply { failWith = "Your API token can't edit synonyms." }
        show(store)

        composeTestRule.onNodeWithTag(UserSynonymsTestTags.ADD_CHIP).performClick()
        composeTestRule.onNodeWithTag(UserSynonymsTestTags.FIELD).performTextInput("big water")
        composeTestRule.onNodeWithTag(UserSynonymsTestTags.CONFIRM).performClick()

        composeTestRule.onNodeWithText("Your API token can't edit synonyms.").assertIsDisplayed()
        composeTestRule.onNodeWithTag(UserSynonymsTestTags.FIELD).assertIsDisplayed()
    }

    @Test
    fun theCrossRemovesASynonym() {
        val store = FakeUserSynonymsStore(mapOf(440L to listOf("aqua", "liquid")))
        show(store)

        composeTestRule.onNodeWithTag(UserSynonymsTestTags.remove("aqua")).performClick()
        composeTestRule.waitForIdle()

        assertThat(store.synonyms.value[440L]).containsExactly("liquid")
        composeTestRule.onNodeWithTag(UserSynonymsTestTags.chip("aqua")).assertDoesNotExist()
    }

    @Test
    fun aRefusedRemovalSaysWhyUnderTheRow() {
        val store = FakeUserSynonymsStore(mapOf(440L to listOf("aqua"))).apply { failWith = "Network error." }
        show(store)

        composeTestRule.onNodeWithTag(UserSynonymsTestTags.remove("aqua")).performClick()

        composeTestRule.onNodeWithTag(UserSynonymsTestTags.ERROR).assertIsDisplayed()
        composeTestRule.onNodeWithTag(UserSynonymsTestTags.chip("aqua")).assertIsDisplayed()
    }

    /** Synonyms are meanings: while the meaning question is unanswered they would give it away. */
    @Test
    fun hiddenWhileTheMeaningIsStillBeingAsked() {
        show(
            FakeUserSynonymsStore(mapOf(440L to listOf("aqua"))),
            revealMode = DetailRevealMode.HIDE_UNTIL_ANSWERED,
            isAnswered = false
        )

        composeTestRule.onNodeWithTag(UserSynonymsTestTags.ROW).assertDoesNotExist()
    }

    @Test
    fun noRowWithoutAStore() {
        show(store = null)

        composeTestRule.onNodeWithTag(UserSynonymsTestTags.ROW).assertDoesNotExist()
    }
}
