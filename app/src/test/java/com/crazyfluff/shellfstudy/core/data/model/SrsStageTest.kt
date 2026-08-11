package com.crazyfluff.shellfstudy.core.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SrsStageTest {

    @Test
    fun `fromRaw maps known values to their stage`() {
        assertThat(SrsStage.fromRaw(0)).isEqualTo(SrsStage.LOCKED)
        assertThat(SrsStage.fromRaw(5)).isEqualTo(SrsStage.GURU_1)
        assertThat(SrsStage.fromRaw(9)).isEqualTo(SrsStage.BURNED)
    }

    @Test
    fun `fromRaw falls back to LOCKED for an unknown value`() {
        assertThat(SrsStage.fromRaw(-1)).isEqualTo(SrsStage.LOCKED)
        assertThat(SrsStage.fromRaw(99)).isEqualTo(SrsStage.LOCKED)
    }

    @Test
    fun `isRankUp is true when the new stage is higher`() {
        val rankChange = RankChange(from = SrsStage.APPRENTICE_1, to = SrsStage.APPRENTICE_2)

        assertThat(rankChange.isRankUp).isTrue()
    }

    @Test
    fun `isRankUp is false when the stage is unchanged`() {
        val rankChange = RankChange(from = SrsStage.GURU_1, to = SrsStage.GURU_1)

        assertThat(rankChange.isRankUp).isFalse()
    }

    @Test
    fun `isRankUp is false when the new stage is lower`() {
        val rankChange = RankChange(from = SrsStage.GURU_1, to = SrsStage.APPRENTICE_4)

        assertThat(rankChange.isRankUp).isFalse()
    }
}
