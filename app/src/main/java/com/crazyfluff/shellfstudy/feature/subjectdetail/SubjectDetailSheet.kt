package com.crazyfluff.shellfstudy.feature.subjectdetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.DetailQuestionType
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.DetailRevealMode
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.SubjectDetailContent
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.SubjectDetailTestTags

/**
 * Presents the shared subject detail view as a [ModalBottomSheet]. Related-subject tiles drill
 * further into the sheet (back button + system back pop the internal stack) rather than dismissing
 * it — see [SubjectDetailViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectDetailSheet(
    initialSubjectId: Long,
    revealMode: DetailRevealMode,
    isAnswered: Boolean,
    questionType: DetailQuestionType?,
    onDismiss: () -> Unit,
    viewModel: SubjectDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberReluctantDismissSheetState()

    LaunchedEffect(initialSubjectId) {
        viewModel.open(initialSubjectId)
    }

    // ModalBottomSheet only calls onDismissRequest once its hide animation finishes, but its
    // full-screen scrim keeps swallowing taps for that entire window. sheetState.targetValue flips
    // to Hidden as soon as the drag/fling commits to dismissing (well before the animation settles),
    // so react to that instead — it removes this composable (and its Dialog/scrim) immediately,
    // rather than leaving it around to steal a fast tap on whatever is underneath (e.g. the search
    // bar) and reopen it.
    LaunchedEffect(sheetState) {
        var hasAppeared = false
        snapshotFlow { sheetState.targetValue }.collect { target ->
            if (target != SheetValue.Hidden) {
                hasAppeared = true
            } else if (hasAppeared) {
                onDismiss()
            }
        }
    }

    BackHandler(enabled = uiState.backStack.isNotEmpty()) {
        viewModel.goBack()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(SubjectDetailTestTags.SHEET_ROOT)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            if (uiState.backStack.isNotEmpty()) {
                IconButton(onClick = { viewModel.goBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }

        val detail = uiState.detail
        if (uiState.isLoading || detail == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            SubjectDetailContent(
                detail = detail,
                relatedSubjects = uiState.relatedSubjects,
                revealMode = revealMode,
                isAnswered = isAnswered,
                questionType = questionType,
                onRelatedSubjectClick = viewModel::navigateToRelated,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp)
            )
        }
    }
}

/**
 * Material3's default swipe-to-dismiss thresholds (56dp positional / 125dp-per-second fling) are
 * easy to trigger by accident — e.g. overshooting while scrolling long content back up to the top.
 * [rememberModalBottomSheetState] doesn't expose threshold tuning, so this builds [SheetState]
 * directly (its constructor does) with much larger thresholds, requiring a real deliberate drag —
 * or a fast intentional fling — to dismiss. The close button, back gesture, and tapping outside the
 * sheet are untouched by this and stay instant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberReluctantDismissSheetState(): SheetState {
    val density = LocalDensity.current
    val positionalThresholdPx = { with(density) { 200.dp.toPx() } }
    val velocityThresholdPx = { with(density) { 1000.dp.toPx() } }
    return rememberSaveable(
        saver = SheetState.Saver(true, positionalThresholdPx, velocityThresholdPx, { true }, false)
    ) {
        SheetState(true, positionalThresholdPx, velocityThresholdPx, SheetValue.Hidden)
    }
}
