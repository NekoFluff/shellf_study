package com.crazyfluff.shellfstudy.shared.quiz

import com.crazyfluff.shellfstudy.shared.data.containsKana
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.crazyfluff.shellfstudy.shared.util.CloseEnoughMatcher
import com.crazyfluff.shellfstudy.shared.util.RomajiConverter

fun questionTypesFor(subjectType: SubjectType): List<QuestionType> =
    if (subjectType == SubjectType.RADICAL || subjectType == SubjectType.KANA_VOCABULARY) {
        listOf(QuestionType.MEANING)
    } else {
        listOf(QuestionType.MEANING, QuestionType.READING)
    }

/** True for the two subject types pitch-accent data is meaningfully keyed by — pitch accent is a
 *  word-level concept, and the bundled/Weblio source is keyed by whole dictionary headwords, not
 *  single kanji, so a kanji/radical reading question never has anything to look up. Shared by
 *  Lesson's pitch-accent prefetch and both Lesson/Review's answer-reveal hint gating, so the
 *  scoping rule lives in exactly one place. */
fun isPitchAccentEligible(subjectType: SubjectType): Boolean =
    subjectType == SubjectType.VOCABULARY || subjectType == SubjectType.KANA_VOCABULARY

fun candidatesFor(meanings: List<String>, auxiliaryMeanings: List<String>, readings: List<String>, type: QuestionType): List<String> =
    if (type == QuestionType.MEANING) meanings + auxiliaryMeanings else readings

fun convertReadingSafely(rawAnswer: String): String =
    try {
        RomajiConverter.toHiragana(rawAnswer)
    } catch (e: Exception) {
        rawAnswer
    }

sealed interface AnswerOutcome {
    data object TypeMismatch : AnswerOutcome
    data class Graded(val isCorrect: Boolean, val wasCloseMatch: Boolean) : AnswerOutcome
}

fun evaluateAnswer(
    rawInput: String,
    type: QuestionType,
    meanings: List<String>,
    auxiliaryMeanings: List<String>,
    readings: List<String>,
    closeEnoughEnabled: Boolean = true
): AnswerOutcome {
    val candidates = candidatesFor(meanings, auxiliaryMeanings, readings, type)
    return if (type == QuestionType.MEANING) {
        val match = CloseEnoughMatcher.match(rawInput, candidates, allowCloseEnough = closeEnoughEnabled)
        val readingCandidates = candidatesFor(meanings, auxiliaryMeanings, readings, QuestionType.READING)
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
        if (!isCorrect && CloseEnoughMatcher.match(rawInput, meaningCandidates).isMatch) {
            AnswerOutcome.TypeMismatch
        } else {
            AnswerOutcome.Graded(isCorrect, wasCloseMatch = false)
        }
    }
}
