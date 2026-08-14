package com.crazyfluff.shellfstudy.shared.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KanaUtilsTest {

    @Test
    fun toKatakanaConvertsHiraganaToKatakana() {
        assertEquals("ミズ", "みず".toKatakana())
    }

    @Test
    fun toKatakanaLeavesExistingKatakanaUntouched() {
        assertEquals("ミズ", "ミズ".toKatakana())
    }

    @Test
    fun toKatakanaLeavesNonKanaCharactersUntouched() {
        assertEquals("water123", "water123".toKatakana())
    }

    @Test
    fun toKatakanaHandlesMixedHiraganaAndNonKanaInput() {
        assertEquals("オ土産", "お土産".toKatakana())
    }

    @Test
    fun containsKanaIsTrueForHiragana() {
        assertTrue("みず".containsKana())
    }

    @Test
    fun containsKanaIsTrueForKatakana() {
        assertTrue("ミズ".containsKana())
    }

    @Test
    fun containsKanaIsTrueWhenKanaIsMixedWithOtherCharacters() {
        assertTrue("Water みず".containsKana())
    }

    @Test
    fun containsKanaIsFalseForPlainEnglish() {
        assertFalse("Water".containsKana())
    }

    @Test
    fun containsKanaIsFalseForKanjiWithoutAnyKana() {
        assertFalse("水".containsKana())
    }
}
