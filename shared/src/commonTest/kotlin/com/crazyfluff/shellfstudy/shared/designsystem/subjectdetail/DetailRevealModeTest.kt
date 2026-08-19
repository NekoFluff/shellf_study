package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetailRevealModeTest {

    @Test
    fun canOfferForceReveal_trueOnlyWhenHiddenAndNotAlreadyForced() {
        assertTrue(canOfferForceReveal(DetailRevealMode.HIDE_UNTIL_ANSWERED, hasBackStack = false, forceRevealAll = false))
        assertFalse(canOfferForceReveal(DetailRevealMode.FULL, hasBackStack = false, forceRevealAll = false))
        assertFalse(canOfferForceReveal(DetailRevealMode.HIDE_UNTIL_ANSWERED, hasBackStack = true, forceRevealAll = false))
        assertFalse(canOfferForceReveal(DetailRevealMode.HIDE_UNTIL_ANSWERED, hasBackStack = false, forceRevealAll = true))
    }

    @Test
    fun resolveEffectiveRevealMode_forcesFullWhenBackStackOrOverrideActive() {
        assertEquals(
            DetailRevealMode.FULL,
            resolveEffectiveRevealMode(DetailRevealMode.HIDE_UNTIL_ANSWERED, hasBackStack = true, forceRevealAll = false)
        )
        assertEquals(
            DetailRevealMode.FULL,
            resolveEffectiveRevealMode(DetailRevealMode.HIDE_UNTIL_ANSWERED, hasBackStack = false, forceRevealAll = true)
        )
        assertEquals(
            DetailRevealMode.HIDE_UNTIL_ANSWERED,
            resolveEffectiveRevealMode(DetailRevealMode.HIDE_UNTIL_ANSWERED, hasBackStack = false, forceRevealAll = false)
        )
        assertEquals(
            DetailRevealMode.FULL,
            resolveEffectiveRevealMode(DetailRevealMode.FULL, hasBackStack = false, forceRevealAll = false)
        )
    }
}
