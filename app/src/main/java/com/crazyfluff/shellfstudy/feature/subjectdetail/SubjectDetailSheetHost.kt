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
 * tiles. Not for Review's mid-quiz [DetailPeekSheet], which is gated by the current question and
 * keeps its own separate state ([com.crazyfluff.shellfstudy.feature.review.ReviewUiState.isDetailsExpanded]).
 */
@Composable
fun SubjectDetailSheetHost(state: SubjectDetailSheetState) {
    state.subjectId?.let { id ->
        SubjectDetailSheet(
            initialSubjectId = id,
            revealMode = DetailRevealMode.FULL,
            isAnswered = true,
            questionType = null,
            onDismiss = { state.dismiss() }
        )
    }
}
