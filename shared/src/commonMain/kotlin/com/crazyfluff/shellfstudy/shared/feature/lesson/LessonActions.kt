package com.crazyfluff.shellfstudy.shared.feature.lesson

import com.crazyfluff.shellfstudy.shared.network.SubjectType

/**
 * Everything the lesson screen can ask its state holder to do, as one type — so a phase composable
 * takes one parameter instead of a callback per action.
 *
 * `LessonViewModel` is the production implementation and is what `LessonRoute` passes down. The
 * interface exists, rather than the composables naming the concrete ViewModel, so a screen test can
 * substitute a recording stand-in and keep rendering arbitrary `LessonUiState` values: with a
 * concrete ViewModel a synthetic state and the instance whose methods fire are two different
 * objects, so nothing observable would change when a control is clicked.
 *
 * This deliberately mirrors the ViewModel's public action surface one-for-one. That is the point:
 * the screen used to re-encode each of these as a `LessonScreenEvent` variant, then decode it again
 * in `LessonRoute` and once more in the screen test's helper — four lists of the same 24 names that
 * had to be kept in agreement by hand. A method call cannot be transcribed wrongly.
 */
interface LessonActions {
    /** Loads (or retries) the lesson queue. */
    fun load()

    /** Proceeds with whatever is already cached after a load failure. */
    fun studyOffline()

    fun toggleLessonSelection(assignmentId: Long)

    fun toggleLessonTypeSelection(type: SubjectType)

    fun setLessonSort(sort: LessonSort)

    fun selectFirst(count: Int)

    fun selectAll()

    fun selectNone()

    fun startSelectedLessons()

    fun onStudyCardSwiped(index: Int)

    fun nextStudyCard()

    fun previousStudyCard()

    fun onAnswerInputChange(value: String)

    fun submitAnswer()

    fun dontKnowAnswer()

    fun undoLastAnswer()

    fun revealAnswer()

    fun onContinue()

    fun toggleDetails()

    fun closeDetails()

    /** The batch checkpoint's primary action — walks into the next batch's flashcards. */
    fun continueSession()

    /** The batch checkpoint's "Finishing for now" — keeps the session and leaves the screen. */
    fun finishForNow()

    fun abandonSession()
}
