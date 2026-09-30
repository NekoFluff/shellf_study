package com.crazyfluff.shellfstudy.shared.feature.review

import com.crazyfluff.shellfstudy.shared.data.model.ReviewItem
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewPrioritizerTest {

    private fun item(
        assignmentId: Long,
        level: Int,
        srsStage: Int,
        subjectType: SubjectType
    ) = ReviewItem(
        assignmentId = assignmentId,
        subjectId = assignmentId * 10,
        subjectType = subjectType,
        characters = "字",
        level = level,
        srsStage = srsStage,
        meanings = listOf("Meaning"),
        readings = listOf("よみ")
    )

    @Test
    fun `this level's not-yet-Guru radicals and kanji are the priority items and nothing else is`() {
        val gatingKanji = item(1, level = 3, srsStage = 3, subjectType = SubjectType.KANJI)
        val gatingRadical = item(2, level = 3, srsStage = 1, subjectType = SubjectType.RADICAL)
        val earlyVocab = item(3, level = 3, srsStage = 1, subjectType = SubjectType.VOCABULARY)
        val otherLevelKanji = item(4, level = 2, srsStage = 1, subjectType = SubjectType.KANJI)
        val otherLevelRadical = item(5, level = 2, srsStage = 1, subjectType = SubjectType.RADICAL)
        val guruKanji = item(6, level = 3, srsStage = 5, subjectType = SubjectType.KANJI)
        val guruRadical = item(7, level = 3, srsStage = 5, subjectType = SubjectType.RADICAL)

        // The kanji count toward the level-up bar and the radicals unlock them, so both are special
        // while they're below Guru on the current level. Vocabulary never gates a level, and neither
        // does anything already at Guru or from an earlier level.
        val ids = ReviewPrioritizer.priorityIds(
            listOf(gatingKanji, gatingRadical, earlyVocab, otherLevelKanji, otherLevelRadical, guruKanji, guruRadical),
            currentLevel = 3
        )
        assertEquals(setOf(1L, 2L), ids)
    }

    @Test
    fun `with no current level cached nothing is a priority item`() {
        // A null level means no level progression has synced yet. Nothing is special; the queue
        // stays on its plain path.
        val ids = ReviewPrioritizer.priorityIds(
            listOf(item(1, level = 3, srsStage = 3, subjectType = SubjectType.KANJI)),
            currentLevel = null
        )
        assertTrue(ids.isEmpty())
    }
}
