package com.crazyfluff.shellfstudy.core.util

/**
 * Fuzzy matching for meaning answers, so a single typo doesn't fail an otherwise-correct answer.
 * Adapted in spirit (not ported verbatim) from how Smouldering Durtles handles "close enough":
 * Optimal String Alignment distance (Levenshtein plus adjacent-transposition as a single edit)
 * against a length-scaled threshold, so short answers still require an exact match while longer
 * ones tolerate a couple of typos. Reading answers deliberately don't use this — WaniKani's own
 * convention, which Smouldering Durtles also follows, is exact-match-only for kana.
 */
object CloseEnoughMatcher {

    /** [matchedCandidate] is the original (uncleaned) candidate string the answer matched against,
     *  useful for a "accepted as close to 'X'" message; null when there's no match. */
    data class MatchResult(val isMatch: Boolean, val isExact: Boolean, val matchedCandidate: String? = null)

    private val NO_MATCH = MatchResult(isMatch = false, isExact = false)

    /**
     * Compares [answer] against every string in [candidates], cleaned (trimmed, lowercased,
     * whitespace-collapsed) before comparing. An exact clean match against any candidate short-
     * circuits immediately; otherwise returns the closest within-threshold near-match, if any.
     */
    fun match(answer: String, candidates: List<String>): MatchResult {
        val cleanedAnswer = clean(answer)
        if (cleanedAnswer.isEmpty() || candidates.isEmpty()) return NO_MATCH

        var best: MatchResult = NO_MATCH
        var bestDistance = Int.MAX_VALUE
        for (candidate in candidates) {
            val cleanedCandidate = clean(candidate)
            if (cleanedCandidate.isEmpty()) continue
            if (cleanedCandidate == cleanedAnswer) {
                return MatchResult(isMatch = true, isExact = true, matchedCandidate = candidate)
            }
            val threshold = thresholdFor(cleanedCandidate.length)
            val distance = optimalStringAlignmentDistance(cleanedAnswer, cleanedCandidate, maxDistance = threshold)
            if (distance <= threshold && distance < bestDistance) {
                bestDistance = distance
                best = MatchResult(isMatch = true, isExact = false, matchedCandidate = candidate)
            }
        }
        return best
    }

    private fun clean(text: String): String =
        text.trim().lowercase().replace(Regex("\\s+"), " ")

    /** Length 1-3: exact only. 4-5: 1 edit. 6-7: 2 edits. 8+: length/7 + 2. */
    private fun thresholdFor(referenceLength: Int): Int = when {
        referenceLength <= 3 -> 0
        referenceLength <= 5 -> 1
        referenceLength <= 7 -> 2
        else -> referenceLength / 7 + 2
    }

    /**
     * Levenshtein distance plus adjacent-transposition as a single edit (Optimal String Alignment —
     * unlike full Damerau-Levenshtein, a substring can only be transposed once, the standard,
     * simpler variant for this kind of typo tolerance). Both compared strings are short English
     * words/phrases, so a plain DP table is plenty fast; [maxDistance] only short-circuits the
     * cases where the length difference alone already rules out a match.
     */
    private fun optimalStringAlignmentDistance(a: String, b: String, maxDistance: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > maxDistance) return maxDistance + 1

        val rows = a.length + 1
        val cols = b.length + 1
        val d = Array(rows) { IntArray(cols) }
        for (i in 0 until rows) d[i][0] = i
        for (j in 0 until cols) d[0][j] = j

        for (i in 1 until rows) {
            for (j in 1 until cols) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = minOf(
                    d[i - 1][j] + 1, // deletion
                    d[i][j - 1] + 1, // insertion
                    d[i - 1][j - 1] + cost // substitution
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    value = minOf(value, d[i - 2][j - 2] + 1) // adjacent transposition
                }
                d[i][j] = value
            }
        }
        return d[a.length][b.length]
    }
}
