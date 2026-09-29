package com.crazyfluff.shellfstudy.shared.data.model

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class SrsStageTest {

    @Test
    fun `fromRaw maps known values to their stage`() {
        assertEquals(SrsStage.LOCKED, SrsStage.fromRaw(0))
        assertEquals(SrsStage.GURU_1, SrsStage.fromRaw(5))
        assertEquals(SrsStage.BURNED, SrsStage.fromRaw(9))
    }

    @Test
    fun `fromRaw falls back to LOCKED for an unknown value`() {
        assertEquals(SrsStage.LOCKED, SrsStage.fromRaw(-1))
        assertEquals(SrsStage.LOCKED, SrsStage.fromRaw(99))
    }

    @Test
    fun `isRankUp is true when the new stage is higher`() {
        val rankChange = RankChange(from = SrsStage.APPRENTICE_1, to = SrsStage.APPRENTICE_2)

        assertTrue(rankChange.isRankUp)
    }

    @Test
    fun `isRankUp is false when the stage is unchanged`() {
        val rankChange = RankChange(from = SrsStage.GURU_1, to = SrsStage.GURU_1)

        assertFalse(rankChange.isRankUp)
    }

    @Test
    fun `isRankUp is false when the new stage is lower`() {
        val rankChange = RankChange(from = SrsStage.GURU_1, to = SrsStage.APPRENTICE_4)

        assertFalse(rankChange.isRankUp)
    }
}
