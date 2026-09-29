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
    fun `this level's not-yet-Guru kanji tiers ahead of everything else`() {
        val select = ReviewPrioritizer.tierSelector(currentLevel = 3)
        val earlyVocab = item(1, level = 3, srsStage = 1, subjectType = SubjectType.VOCABULARY)
        val otherLevelKanji = item(2, level = 2, srsStage = 1, subjectType = SubjectType.KANJI)
        val guruKanji = item(3, level = 3, srsStage = 5, subjectType = SubjectType.KANJI)
        val gatingKanji = item(4, level = 3, srsStage = 3, subjectType = SubjectType.KANJI)
        val gatingRadical = item(5, level = 3, srsStage = 1, subjectType = SubjectType.RADICAL)
        val lateVocab = item(6, level = 3, srsStage = 1, subjectType = SubjectType.VOCABULARY)

        // Only the Apprentice-level kanji of the current level is special: a Guru-level kanji no
        // longer gates anything, an earlier level's kanji can't gate this level's progress, and
        // radicals/vocabulary never count toward the 90%-of-kanji requirement at all.
        val priorityTier = select(gatingKanji)
        assertTrue(priorityTier < select(earlyVocab))
        assertTrue(priorityTier < select(otherLevelKanji))
        assertTrue(priorityTier < select(guruKanji))
        assertTrue(priorityTier < select(gatingRadical))
        assertTrue(priorityTier < select(lateVocab))
    }

    @Test
    fun `everything outside the level-up tier shares one tier, so due order survives among them`() {
        val select = ReviewPrioritizer.tierSelector(currentLevel = 3)
        assertEquals(
            select(item(1, level = 3, srsStage = 1, subjectType = SubjectType.VOCABULARY)),
            select(item(2, level = 2, srsStage = 1, subjectType = SubjectType.KANJI))
        )
        assertEquals(
            select(item(1, level = 3, srsStage = 1, subjectType = SubjectType.VOCABULARY)),
            select(item(3, level = 3, srsStage = 5, subjectType = SubjectType.KANJI))
        )
    }

    @Test
    fun `with no current level cached every item shares one tier instead of none matching`() {
        // A null level means no level progression has synced yet. Treating it as "level 0" would
        // match nothing and make the setting look broken; the whole queue is simply one tier.
        val select = ReviewPrioritizer.tierSelector(currentLevel = null)
        assertEquals(
            select(item(1, level = 3, srsStage = 3, subjectType = SubjectType.KANJI)),
            select(item(2, level = 3, srsStage = 1, subjectType = SubjectType.VOCABULARY))
        )
    }
}
