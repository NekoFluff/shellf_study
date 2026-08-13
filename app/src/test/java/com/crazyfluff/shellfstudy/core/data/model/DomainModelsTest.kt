package com.crazyfluff.shellfstudy.core.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DomainModelsTest {

    @Test
    fun `requiredCount rounds up to 90 percent`() {
        assertThat(LevelUpProgress(kanjiGuruedOrHigher = 0, kanjiTotal = 25).requiredCount).isEqualTo(23) // ceil(22.5)
    }

    @Test
    fun `isLevelUpReady is false when total is zero`() {
        assertThat(LevelUpProgress(kanjiGuruedOrHigher = 0, kanjiTotal = 0).isLevelUpReady).isFalse()
    }

    @Test
    fun `isLevelUpReady is true at exactly the required count`() {
        assertThat(LevelUpProgress(kanjiGuruedOrHigher = 23, kanjiTotal = 25).isLevelUpReady).isTrue()
    }

    @Test
    fun `isLevelUpReady is false one below the required count`() {
        assertThat(LevelUpProgress(kanjiGuruedOrHigher = 22, kanjiTotal = 25).isLevelUpReady).isFalse()
    }
}
