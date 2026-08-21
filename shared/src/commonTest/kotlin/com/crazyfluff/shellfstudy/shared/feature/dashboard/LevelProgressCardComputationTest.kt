package com.crazyfluff.shellfstudy.shared.feature.dashboard

import com.crazyfluff.shellfstudy.shared.data.model.LevelItem
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.data.model.SubjectTypeProgress
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import kotlin.test.Test
import kotlin.test.assertEquals

class LevelProgressCardComputationTest {

    private fun item(passed: Boolean, srsStage: SrsStage, id: Long = 1L) = LevelItem(
        subjectId = id,
        subjectType = SubjectType.RADICAL,
        characters = "x",
        display = "x",
        passed = passed,
        srsStage = srsStage
    )

    @Test
    fun barSegmentCounts_matchLiveSrsStage_whenNoDemotions() {
        val entry = SubjectTypeProgress(
            SubjectType.RADICAL,
            items = listOf(
                item(passed = true, srsStage = SrsStage.GURU_1, id = 1),
                item(passed = false, srsStage = SrsStage.APPRENTICE_2, id = 2),
                item(passed = false, srsStage = SrsStage.LOCKED, id = 3)
            )
        )

        val (done, inProgress, locked) = barSegmentCounts(entry)

        assertEquals(1, done)
        assertEquals(1, inProgress)
        assertEquals(1, locked)
    }

    @Test
    fun barSegmentCounts_keepsDemotedItemInDoneSegment_matchingPassedCount() {
        // Item was once Guru+ (passed = true) but has since been demoted back to Apprentice by a
        // failed review. The bar must still count it as "done", agreeing with entry.passedCount
        // (the label above the bar) and LevelItem.passed (the chip fill below it) — not the live
        // srsStage, which would otherwise show it as "in progress" while everything else around
        // it still calls it passed.
        val entry = SubjectTypeProgress(
            SubjectType.RADICAL,
            items = listOf(
                item(passed = true, srsStage = SrsStage.APPRENTICE_1, id = 1),
                item(passed = false, srsStage = SrsStage.APPRENTICE_3, id = 2)
            )
        )

        val (done, inProgress, locked) = barSegmentCounts(entry)

        assertEquals(1, done)
        assertEquals(entry.passedCount, done)
        assertEquals(1, inProgress)
        assertEquals(0, locked)
    }

    @Test
    fun barSegmentCounts_totalsAlwaysSumToTotalCount() {
        val entry = SubjectTypeProgress(
            SubjectType.KANJI,
            items = listOf(
                item(passed = true, srsStage = SrsStage.BURNED, id = 1),
                item(passed = true, srsStage = SrsStage.APPRENTICE_1, id = 2),
                item(passed = false, srsStage = SrsStage.MASTER, id = 3),
                item(passed = false, srsStage = SrsStage.LOCKED, id = 4)
            )
        )

        val (done, inProgress, locked) = barSegmentCounts(entry)

        assertEquals(entry.totalCount, done + inProgress + locked)
    }
}
