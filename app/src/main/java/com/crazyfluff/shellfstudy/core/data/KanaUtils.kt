package com.crazyfluff.shellfstudy.core.data

private const val HIRAGANA_START = 'ぁ'
private const val HIRAGANA_END = 'ゖ'
private const val HIRAGANA_TO_KATAKANA_OFFSET = 0x60

/** Converts hiragana characters to katakana, leaving everything else (including existing katakana) untouched. */
fun String.toKatakana(): String = map { c ->
    if (c in HIRAGANA_START..HIRAGANA_END) c + HIRAGANA_TO_KATAKANA_OFFSET else c
}.joinToString("")
