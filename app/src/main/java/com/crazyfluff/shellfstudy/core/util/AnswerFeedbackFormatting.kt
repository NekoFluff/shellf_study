package com.crazyfluff.shellfstudy.core.util

private const val MAX_DISPLAYED_ANSWERS = 3

/** Display text for a comma-joined answer-candidate list, plus whether [text] is a truncated
 *  preview the caller can offer to expand — [hasMore] stays true even once [expanded] was
 *  requested, so a caller can keep the same tap target working to collapse it back down. */
data class AnswerListDisplay(val text: String, val hasMore: Boolean)

/**
 * Formats a comma-joined answer-candidate list for compact display: caps how many are spelled out
 * and summarizes the rest as "+N more" unless [expanded], rather than letting an item with many
 * synonyms/whitelisted alternates wrap across several lines of the small review/lesson feedback
 * area by default.
 */
fun formatAnswerList(joined: String, expanded: Boolean = false): AnswerListDisplay {
    val parts = joined.split(", ").filter { it.isNotBlank() }
    val hasMore = parts.size > MAX_DISPLAYED_ANSWERS
    val text = if (expanded || !hasMore) {
        joined
    } else {
        parts.take(MAX_DISPLAYED_ANSWERS).joinToString(", ") + " +${parts.size - MAX_DISPLAYED_ANSWERS} more"
    }
    return AnswerListDisplay(text, hasMore)
}
