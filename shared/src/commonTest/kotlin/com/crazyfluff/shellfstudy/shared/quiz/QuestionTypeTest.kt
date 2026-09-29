package com.crazyfluff.shellfstudy.shared.quiz

import com.crazyfluff.shellfstudy.shared.data.PersistedQuestion
import com.crazyfluff.shellfstudy.shared.data.model.ReviewItem
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The resume paths rebuild a session's queue from strings written by an earlier build of the app.
 * `QuestionType.valueOf` throws on a name it doesn't know, and every call site sits inside the
 * ViewModel's launch on app start — so a renamed constant, a hand-edited snapshot or a partial
 * migration would have crashed on launch instead of rebuilding the session. These pin the tolerant
 * parse that replaced it, and the null that tells callers to fall back.
 */
class QuestionTypeTest {

    @Test
    fun persistedName_roundTripsForEveryQuestionType() {
        QuestionType.entries.forEach { type ->
            assertEquals(type, QuestionType.fromPersisted(type.name))
        }
    }

    @Test
    fun unknownPersistedName_isNullRatherThanAThrow() {
        assertNull(QuestionType.fromPersisted("LISTENING"))
        assertNull(QuestionType.fromPersisted(""))
        assertNull(QuestionType.fromPersisted("meaning"))
    }

    @Test
    fun pendingQuestion_isRebuiltFromItsPersistedEntry() {
        val item = reviewItem(assignmentId = 7)

        val restored = PersistedQuestion(assignmentId = 7, questionType = "READING")
            .toPendingQuestionOrNull(mapOf(7L to item))

        assertEquals(PendingQuestion(item, QuestionType.READING), restored)
    }

    @Test
    fun entryForAnItemThatIsNoLongerAvailable_isUnrecoverable() {
        assertNull(PersistedQuestion(assignmentId = 7, questionType = "READING").toPendingQuestionOrNull(emptyMap<Long, ReviewItem>()))
    }

    @Test
    fun entryWithAnUnknownQuestionType_isUnrecoverable() {
        val item = reviewItem(assignmentId = 7)

        assertNull(
            PersistedQuestion(assignmentId = 7, questionType = "LISTENING")
                .toPendingQuestionOrNull(mapOf(7L to item))
        )
    }

    private fun reviewItem(assignmentId: Long) = ReviewItem(
        assignmentId = assignmentId,
        subjectId = 1,
        subjectType = SubjectType.RADICAL,
        characters = "一",
        level = 1,
        srsStage = 1,
        meanings = listOf("One"),
        readings = emptyList()
    )
}
