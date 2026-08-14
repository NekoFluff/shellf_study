package com.crazyfluff.shellfstudy.shared.data

private const val HIRAGANA_START = 'ぁ'
private const val HIRAGANA_END = 'ゖ'
private const val HIRAGANA_TO_KATAKANA_OFFSET = 0x60
private const val KATAKANA_START = 'ァ'
private const val KATAKANA_END = 'ヺ'

/** Converts hiragana characters to katakana, leaving everything else (including existing katakana) untouched. */
fun String.toKatakana(): String = map { c ->
    if (c in HIRAGANA_START..HIRAGANA_END) c + HIRAGANA_TO_KATAKANA_OFFSET else c
}.joinToString("")

/** True if any character is hiragana or katakana — meanings are always plain English, so this
 *  unambiguously flags a reading typed into a meaning answer. */
fun String.containsKana(): Boolean = any { c -> c in HIRAGANA_START..HIRAGANA_END || c in KATAKANA_START..KATAKANA_END }
