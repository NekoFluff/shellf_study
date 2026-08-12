package com.crazyfluff.shellfstudy.core.quiz

import com.crazyfluff.shellfstudy.core.network.SubjectType
import com.crazyfluff.shellfstudy.core.util.RomajiConverter

/** Radicals have no reading, so they're meaning-only; every other subject type asks both. */
fun questionTypesFor(subjectType: SubjectType): List<QuestionType> =
    if (subjectType == SubjectType.RADICAL) {
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
