package com.crazyfluff.shellfstudy.core.quiz

import com.crazyfluff.shellfstudy.core.data.containsKana
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.crazyfluff.shellfstudy.core.util.CloseEnoughMatcher
import com.crazyfluff.shellfstudy.core.util.RomajiConverter

/** Radicals have no reading, and kana-only vocabulary has no reading distinct from its own
 *  characters (WaniKani's API returns an empty `readings` list for it) — both are meaning-only.
 *  Every other subject type asks both. */
fun questionTypesFor(subjectType: SubjectType): List<QuestionType> =
    if (subjectType == SubjectType.RADICAL || subjectType == SubjectType.KANA_VOCABULARY) {
        listOf(QuestionType.MEANING)
    } else {
        listOf(QuestionType.MEANING, QuestionType.READING)
    }

/** Meaning answers pool the primary meanings with WaniKani's own whitelist synonyms — both are
 *  equally acceptable. Reading answers stay exact-match-only, so no auxiliary readings exist. */
fun candidatesFor(meanings: List<String>, auxiliaryMeanings: List<String>, readings: List<String>, type: QuestionType): List<String> =
    if (type == QuestionType.MEANING) meanings + auxiliaryMeanings else readings

/** Never lets a malformed answer crash grading — falls back to the raw (untranslated) text. */
fun convertReadingSafely(rawAnswer: String): String =
    try {
        RomajiConverter.toHiragana(rawAnswer)
    } catch (e: Exception) {
        rawAnswer
    }

/** Result of grading a submitted answer against the expected question type — shared by the review
 *  and lesson quiz view models so both apply identical grading/rejection rules. */
sealed interface AnswerOutcome {
    /** The answer looked like a genuine attempt at the *other* question type (e.g. a reading typed
     *  into a meaning question) rather than a real miss — rejected outright, not graded. */
    data object TypeMismatch : AnswerOutcome
    data class Graded(val isCorrect: Boolean, val wasCloseMatch: Boolean) : AnswerOutcome
}

/** Grades [rawInput] against [type], first checking whether it's actually a habit slip into the
 *  other question type rather than a genuine miss. */
fun evaluateAnswer(
    rawInput: String,
    type: QuestionType,
    meanings: List<String>,
    auxiliaryMeanings: List<String>,
    readings: List<String>
): AnswerOutcome {
    val candidates = candidatesFor(meanings, auxiliaryMeanings, readings, type)
    return if (type == QuestionType.MEANING) {
        // A small typo is graded as correct but flagged, rather than a flat miss — readings
        // stay exact-match, matching WaniKani's own convention for kana.
        val match = CloseEnoughMatcher.match(rawInput, candidates)
        val readingCandidates = candidatesFor(meanings, auxiliaryMeanings, readings, QuestionType.READING)
        // Typing a reading into a meaning answer is a habit slip, not a genuine miss — reject
        // it outright rather than spending an SRS attempt on it. Kana is one unambiguous
        // tell; a wrong guess that romaji-converts into this item's own reading is the same
        // slip, just typed in Latin letters.
        val looksLikeReading = rawInput.containsKana() ||
            CloseEnoughMatcher.match(convertReadingSafely(rawInput.trim()), readingCandidates).isMatch
        if (!match.isMatch && looksLikeReading) {
            AnswerOutcome.TypeMismatch
        } else {
            AnswerOutcome.Graded(match.isMatch, wasCloseMatch = match.isMatch && !match.isExact)
        }
    } else {
        val normalizedAnswer = convertReadingSafely(rawInput.trim())
        val isCorrect = candidates.any { it.trim().equals(normalizedAnswer, ignoreCase = true) }
        val meaningCandidates = candidatesFor(meanings, auxiliaryMeanings, readings, QuestionType.MEANING)
        // Same idea in reverse: a wrong reading that closely matches this item's own meaning
        // is almost certainly the other question type typed by habit, not a real miss.
        if (!isCorrect && CloseEnoughMatcher.match(rawInput, meaningCandidates).isMatch) {
            AnswerOutcome.TypeMismatch
        } else {
            AnswerOutcome.Graded(isCorrect, wasCloseMatch = false)
        }
    }
}
