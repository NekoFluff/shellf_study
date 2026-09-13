package com.crazyfluff.shellfstudy.shared.feature.subjectdetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.DetailRevealMode

/** Which subject (if any) the shared "browse" [SubjectDetailSheet] is currently showing. */
@Stable
class SubjectDetailSheetState internal constructor() {
    var subjectId by mutableStateOf<Long?>(null)
        private set

    fun show(subjectId: Long) {
        this.subjectId = subjectId
    }

    fun dismiss() {
        subjectId = null
    }
}

@Composable
fun rememberSubjectDetailSheetState(): SubjectDetailSheetState = remember { SubjectDetailSheetState() }

/**
 * Hosts the shared "look something up" [SubjectDetailSheet] (always [DetailRevealMode.FULL],
 * answered, ungated) for any screen that just wants tapping a subject to show its detail.
 * Stays mounted once the first subject has ever been shown so browsing several subjects reuses
 * the same [SubjectDetailSheet] instance instead of paying first-mount cost on every open.
 *
 * `active` is tied directly to `state.subjectId != null` (there's no separate "peek" state here
 * the way Review/Lesson have via quiz feedback) so the sheet's scrim is never left hit-testable
 * over the host screen's own content during the close animation — see the session-complete
 * "Back to dashboard" dropped-tap bug in [SubjectDetailSheet]'s doc comment.
 */
@Composable
fun SubjectDetailSheetHost(state: SubjectDetailSheetState) {
    var lastShownSubjectId by remember { mutableStateOf<Long?>(null) }
    state.subjectId?.let { lastShownSubjectId = it }

    lastShownSubjectId?.let { id ->
        val dismiss = { state.dismiss() }
        val expanded = state.subjectId != null
        SubjectDetailSheet(
            subjectId = id,
            expanded = expanded,
            onToggle = dismiss,
            onDismiss = dismiss,
            revealMode = DetailRevealMode.FULL,
            isAnswered = true,
            questionType = null,
            dismissesFully = true,
            active = expanded
        )
    }
}

/**
 * Opens the shared browse [SubjectDetailSheet] on a subject.
 *
 * Unlike `LocalShareText` and `LocalNotificationPermissionRequest`, this has no meaningful fallback:
 * "no opener" can only mean "a tap that does nothing". That is the safe direction for a test that
 * composes one card in isolation, but it is also a silent failure if a *production* composition ever
 * sits outside the root — which is exactly how the Android build lost its subject taps once. So the
 * default no-op is only acceptable because `ArchitectureConventionsTest` proves this is provided in
 * `ShellfStudyApp` and that there is one app root; if a second composition root ever appears, make
 * this nullable and hide the tap affordance instead of defaulting to a dead one.
 *
 * Provided once by `ShellfStudyApp`, which also mounts the single [SubjectDetailSheetHost]. Before
 * this, four screens each remembered their own [SubjectDetailSheetState] and mounted their own host —
 * four identical copies of something with no per-screen state, since the browse host is always
 * [DetailRevealMode.FULL] and answered — and each threaded an `onSubjectClick` closure down through
 * every composable on the way to a tappable subject.
 *
 * The no-op default is what a composition outside the app root gets: a focused test that does not
 * care where a tap leads. A test that asserts on opening a subject provides its own, which is also
 * the first time this has been assertable at all.
 */
val LocalOpenSubjectDetail = staticCompositionLocalOf<(Long) -> Unit> { { } }
