package com.crazyfluff.shellfstudy.feature.subjectdetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.DetailRevealMode

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
 * answered, ungated) for any screen that just wants tapping a subject to show its detail —
 * Dashboard's search bar and Level Progress card, Review's search bar, Lesson's related-subject
 * tiles. Stays mounted (collapsed off the bottom of the screen, per `dismissesFully = true`) once
 * the first subject has ever been shown — mirroring Review's always-mounted mid-quiz sheet — so
 * browsing several subjects in one visit reuses the same [SubjectDetailSheet] instance (its
 * `remember`ed drag state, Surface, etc.) instead of paying first-mount composition cost on every
 * open. [SubjectDetailSheet] still plays its usual slide-up open animation each time (see its own
 * doc comment), and once a close (drag, tap outside, back, or the X button) settles it collapsed,
 * that's reported back here as a toggle away from expanded.
 */
@Composable
fun SubjectDetailSheetHost(state: SubjectDetailSheetState) {
    var lastShownSubjectId by remember { mutableStateOf<Long?>(null) }
    state.subjectId?.let { lastShownSubjectId = it }

    lastShownSubjectId?.let { id ->
        // A gesture-driven settle mismatch and a discrete close both mean exactly the same thing
        // here ("hide it") — unlike Review's mid-quiz sheet, this state has no separate "toggle open
        // via gesture" case to distinguish onToggle from onDismiss.
        val dismiss = { state.dismiss() }
        SubjectDetailSheet(
            subjectId = id,
            expanded = state.subjectId != null,
            onToggle = dismiss,
            onDismiss = dismiss,
            revealMode = DetailRevealMode.FULL,
            isAnswered = true,
            questionType = null,
            dismissesFully = true
        )
    }
}
