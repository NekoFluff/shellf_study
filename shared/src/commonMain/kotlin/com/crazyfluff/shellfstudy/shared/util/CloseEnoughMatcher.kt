package com.crazyfluff.shellfstudy.shared.util

object CloseEnoughMatcher {

    data class MatchResult(val isMatch: Boolean, val isExact: Boolean, val matchedCandidate: String? = null)

    private val NO_MATCH = MatchResult(isMatch = false, isExact = false)

    private val WHITESPACE = Regex("\\s+")
    private val DIGIT_GROUP = Regex("\\d+")

    /** [allowCloseEnough] gates only the edit-distance leniency below — an exact match (after
     *  [clean]'s case/punctuation/whitespace cleanup) always counts as a match either way, since
     *  that's basic normalization rather than typo tolerance. Callers doing type-mismatch detection
     *  (rather than judging answer correctness) should leave this at its default.
     *
     *  [rejected] are WaniKani's blacklisted meanings — decoys like an antonym one letter away from
     *  the real meaning. An exact hit on one is never a match, and a typo is only forgiven when it is
     *  strictly closer to an accepted candidate than to every rejected one, so a near-miss of a decoy
     *  can't slip through as "close". A tie goes to the decoy, which is exactly what it's there for.
     *
     *  Numbers are never fuzzy: when the answer has digits, only candidates with the same digit
     *  groups are considered, so "4 days" is not a typo of "5 days" (as on WaniKani). */
    fun match(
        answer: String,
        candidates: List<String>,
        rejected: List<String> = emptyList(),
        allowCloseEnough: Boolean = true
    ): MatchResult {
        val cleanedAnswer = clean(answer)
        if (cleanedAnswer.isEmpty() || candidates.isEmpty()) return NO_MATCH

        val cleanedCandidates = candidates.map { it to clean(it) }.filter { it.second.isNotEmpty() }
        cleanedCandidates.firstOrNull { it.second == cleanedAnswer }?.let { (candidate, _) ->
            return MatchResult(isMatch = true, isExact = true, matchedCandidate = candidate)
        }
        val cleanedRejected = rejected.map(::clean).filter { it.isNotEmpty() }
        if (cleanedAnswer in cleanedRejected || !allowCloseEnough) return NO_MATCH

        val answerDigits = digitGroups(cleanedAnswer)
        val fuzzyCandidates = if (answerDigits.isEmpty()) {
            cleanedCandidates
        } else {
            cleanedCandidates.filter { digitGroups(it.second) == answerDigits }
        }

        var best: MatchResult = NO_MATCH
        var bestDistance = Int.MAX_VALUE
        for ((candidate, cleanedCandidate) in fuzzyCandidates) {
            val threshold = thresholdFor(cleanedCandidate.length)
            val distance = optimalStringAlignmentDistance(cleanedAnswer, cleanedCandidate, maxDistance = threshold)
            if (distance <= threshold && distance < bestDistance) {
                bestDistance = distance
                best = MatchResult(isMatch = true, isExact = false, matchedCandidate = candidate)
            }
        }
        if (!best.isMatch) return NO_MATCH
        // Only a rejected entry at least as close as the best accepted one matters, so its distance
        // never needs computing past bestDistance.
        val closerToRejected = cleanedRejected.any {
            optimalStringAlignmentDistance(cleanedAnswer, it, maxDistance = bestDistance) <= bestDistance
        }
        return if (closerToRejected) NO_MATCH else best
    }

    /** Folds what the keyboard may type differently from WaniKani's data — full-width digits, curly
     *  quotes, typographic dashes — then, as WaniKani does, treats a hyphen as a space and drops
     *  `. , ' / :`, so "o'clock"/"oclock" and "well-known"/"well known" are the same answer rather
     *  than a spent typo. */
    private fun clean(text: String): String =
        buildString(text.length) {
            for (c in text) {
                when (c) {
                    in '０'..'９' -> append('0' + (c - '０'))
                    in '‐'..'―', '−', '-' -> append(' ')
                    '.', ',', '\'', '/', ':', '‘', '’', '‚', '‛' -> Unit
                    else -> append(c)
                }
            }
        }.trim().lowercase().replace(WHITESPACE, " ")

    private fun digitGroups(cleaned: String): List<String> =
        DIGIT_GROUP.findAll(cleaned).map { it.value }.sorted().toList()

    private fun thresholdFor(referenceLength: Int): Int = when {
        referenceLength <= 3 -> 0
        referenceLength <= 5 -> 1
        referenceLength <= 7 -> 2
        else -> referenceLength / 7 + 2
    }

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
                    d[i - 1][j] + 1,
                    d[i][j - 1] + 1,
                    d[i - 1][j - 1] + cost
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    value = minOf(value, d[i - 2][j - 2] + 1)
                }
                d[i][j] = value
            }
        }
        return d[a.length][b.length]
    }
}
