package com.crazyfluff.shellfstudy.shared.data.model

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class DomainModelsTest {

    @Test
    fun `requiredCount rounds up to 90 percent`() {
        assertEquals(23, LevelUpProgress(kanjiGuruedOrHigher = 0, kanjiTotal = 25).requiredCount) // ceil(22.5)
    }

    @Test
    fun `isLevelUpReady is false when total is zero`() {
        assertFalse(LevelUpProgress(kanjiGuruedOrHigher = 0, kanjiTotal = 0).isLevelUpReady)
    }

    @Test
    fun `isLevelUpReady is true at exactly the required count`() {
        assertTrue(LevelUpProgress(kanjiGuruedOrHigher = 23, kanjiTotal = 25).isLevelUpReady)
    }

    @Test
    fun `isLevelUpReady is false one below the required count`() {
        assertFalse(LevelUpProgress(kanjiGuruedOrHigher = 22, kanjiTotal = 25).isLevelUpReady)
    }
}
