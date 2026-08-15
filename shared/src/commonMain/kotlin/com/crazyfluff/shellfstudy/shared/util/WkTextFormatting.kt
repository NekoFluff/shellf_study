package com.crazyfluff.shellfstudy.shared.util

private val markupTagRegex = Regex("</?[a-zA-Z][a-zA-Z0-9]*>")

/**
 * WaniKani mnemonic text embeds semantic markup tags (e.g. <radical>, <kanji>, <ja>) meant for
 * styling in their own apps; strip them down to plain text since this app doesn't style by tag.
 */
fun stripWkMarkup(text: String): String = text.replace(markupTagRegex, "")
