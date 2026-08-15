package com.crazyfluff.shellfstudy.shared.util

object CloseEnoughMatcher {

    data class MatchResult(val isMatch: Boolean, val isExact: Boolean, val matchedCandidate: String? = null)

    private val NO_MATCH = MatchResult(isMatch = false, isExact = false)

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
