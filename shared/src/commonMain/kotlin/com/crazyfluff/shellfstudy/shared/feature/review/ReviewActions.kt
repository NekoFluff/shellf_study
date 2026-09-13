package com.crazyfluff.shellfstudy.shared.feature.review

/**
 * Everything the review screen can ask its state holder to do, as one type — so a phase composable
 * takes one parameter instead of a callback per action.
 *
 * `ReviewViewModel` is the production implementation and is what `ReviewRoute` passes down. The
 * interface exists, rather than the composables naming the concrete ViewModel, so a screen test can
 * substitute a recording stand-in and keep rendering arbitrary `ReviewUiState` values: with a concrete
 * ViewModel a synthetic state and the instance whose methods fire are two different objects, so
 * nothing observable would change when a control is clicked.
 *
 * This mirrors the ViewModel's public action surface one-for-one. That is the point: the screen used
 * to re-encode each of these as a `ReviewScreenEvent` variant, then decode it again in `ReviewRoute`
 * and once more in the screen test's helper — four lists of the same 11 names kept in agreement by
 * hand. A method call cannot be transcribed wrongly.
 *
 * Same shape as [com.crazyfluff.shellfstudy.shared.feature.lesson.LessonActions]; the two features
 * keep separate types because their action sets differ and neither should be able to call the other's.
 */
interface ReviewActions {
    /** Loads the queue, or resumes the persisted session if there is one. */
    fun loadOrResume()

    /** Proceeds with whatever is already cached after a load failure. */
    fun studyOffline()

    fun onAnswerInputChange(value: String)

    fun submitAnswer()

    fun dontKnowAnswer()

    fun onContinue()

    fun undoLastAnswer()

    fun toggleDetails()

    fun closeDetails()

    /** Ends the session early, still showing the summary for what was reviewed. */
    fun wrapUp()

    fun abandonSession()
}
