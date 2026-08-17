package com.crazyfluff.shellfstudy.shared.feature.lesson

import com.crazyfluff.shellfstudy.shared.data.model.LessonItem
import com.crazyfluff.shellfstudy.shared.data.model.LevelUpProgress
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import kotlin.test.Test
import kotlin.test.assertEquals

class LessonPrioritizerTest {

    private fun item(
        assignmentId: Long,
        subjectType: SubjectType,
        level: Int = 1,
        lessonPosition: Int = 0
    ) = LessonItem(
        assignmentId = assignmentId,
        subjectId = assignmentId,
        subjectType = subjectType,
        characters = null,
        level = level,
        lessonPosition = lessonPosition,
        meanings = listOf("test"),
        readings = emptyList(),
        meaningMnemonic = null,
        readingMnemonic = null
    )

    private val notReady = LevelUpProgress(kanjiGuruedOrHigher = 0, kanjiTotal = 10)
    private val ready = LevelUpProgress(kanjiGuruedOrHigher = 9, kanjiTotal = 10)

    private fun ids(items: List<LessonItem>) = items.map { it.assignmentId }

    @Test
    fun radicalsAlwaysComeFirstRegardlessOfInputOrder() {
        val vocab = item(1, SubjectType.VOCABULARY, lessonPosition = 1)
        val kanji = item(2, SubjectType.KANJI, lessonPosition = 1)
        val radical = item(3, SubjectType.RADICAL, lessonPosition = 99)

        val result = LessonPrioritizer.prioritize(listOf(vocab, kanji, radical), notReady, isStrained = false)

        assertEquals(3L, result.first().assignmentId)
    }

    @Test
    fun kanjiInterleavesWithFillerAtOneInThreeWhenNotStrained() {
        val kanji = (1..3L).map { item(it, SubjectType.KANJI, lessonPosition = it.toInt()) }
        val vocab = (4..9L).map { item(it, SubjectType.VOCABULARY, lessonPosition = it.toInt()) }

        val result = LessonPrioritizer.prioritize(kanji + vocab, notReady, isStrained = false)

        assertEquals(
            listOf(
                SubjectType.KANJI, SubjectType.VOCABULARY, SubjectType.VOCABULARY,
                SubjectType.KANJI, SubjectType.VOCABULARY, SubjectType.VOCABULARY,
                SubjectType.KANJI, SubjectType.VOCABULARY, SubjectType.VOCABULARY
            ),
            result.map { it.subjectType }
        )
    }

    @Test
    fun strainedBatchesLoosenTheKanjiCadenceToOneInFive() {
        val kanji = (1..2L).map { item(it, SubjectType.KANJI, lessonPosition = it.toInt()) }
        val vocab = (3..10L).map { item(it, SubjectType.VOCABULARY, lessonPosition = it.toInt()) }

        val result = LessonPrioritizer.prioritize(kanji + vocab, notReady, isStrained = true)

        assertEquals(
            listOf(1L, 3L, 4L, 5L, 6L, 2L, 7L, 8L, 9L, 10L),
            ids(result)
        )
    }

    @Test
    fun onceLevelUpIsSecuredRemainingKanjiStopJumpingAheadOfVocab() {
        val kanji = item(1, SubjectType.KANJI, lessonPosition = 5)
        val earlyVocab = item(2, SubjectType.VOCABULARY, lessonPosition = 1)
        val laterVocab = item(3, SubjectType.VOCABULARY, lessonPosition = 2)

        val notReadyResult = LessonPrioritizer.prioritize(listOf(kanji, earlyVocab, laterVocab), notReady, isStrained = false)
        val readyResult = LessonPrioritizer.prioritize(listOf(kanji, earlyVocab, laterVocab), ready, isStrained = false)

        // Not ready: kanji is pulled to the front of the interleave despite its later lesson position.
        assertEquals(listOf(1L, 2L, 3L), ids(notReadyResult))
        // Ready: kanji falls back into plain lesson-position order alongside vocab, no longer fast-tracked.
        assertEquals(listOf(2L, 3L, 1L), ids(readyResult))
    }

    @Test
    fun emptyQueueReturnsEmptyList() {
        assertEquals(emptyList(), LessonPrioritizer.prioritize(emptyList(), notReady, isStrained = false))
    }

    @Test
    fun shorterThanBatchSizeReturnsEverythingAvailable() {
        val items = listOf(item(1, SubjectType.RADICAL), item(2, SubjectType.KANJI))
        val result = LessonPrioritizer.prioritize(items, notReady, isStrained = false)
        assertEquals(2, result.size)
    }
}
