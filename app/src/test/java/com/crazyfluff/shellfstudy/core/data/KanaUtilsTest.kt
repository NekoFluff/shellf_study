package com.crazyfluff.shellfstudy.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KanaUtilsTest {

    @Test
    fun `toKatakana converts hiragana to katakana`() {
        assertThat("みず".toKatakana()).isEqualTo("ミズ")
    }

    @Test
    fun `toKatakana leaves existing katakana untouched`() {
        assertThat("ミズ".toKatakana()).isEqualTo("ミズ")
    }

    @Test
    fun `toKatakana leaves non-kana characters untouched`() {
        assertThat("water123".toKatakana()).isEqualTo("water123")
    }

    @Test
    fun `toKatakana handles mixed hiragana and non-kana input`() {
        assertThat("お土産".toKatakana()).isEqualTo("オ土産")
    }

    @Test
    fun `containsKana is true for hiragana`() {
        assertThat("みず".containsKana()).isTrue()
    }

    @Test
    fun `containsKana is true for katakana`() {
        assertThat("ミズ".containsKana()).isTrue()
    }

    @Test
    fun `containsKana is true when kana is mixed with other characters`() {
        assertThat("Water みず".containsKana()).isTrue()
    }

    @Test
    fun `containsKana is false for plain English`() {
        assertThat("Water".containsKana()).isFalse()
    }

    @Test
    fun `containsKana is false for kanji without any kana`() {
        assertThat("水".containsKana()).isFalse()
    }
}
