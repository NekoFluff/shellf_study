package com.crazyfluff.shellfstudy.feature.subjectdetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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

    // Stops audio the moment the sheet leaves composition, regardless of which screen hosts it.
    DisposableEffect(Unit) {
        onDispose { viewModel.stopPlayback() }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(SubjectDetailTestTags.SHEET_ROOT)
    ) {
        // Must live inside the sheet's content, not before ModalBottomSheet(...): the sheet
        // renders in its own dialog window with its own OnBackPressedDispatcher, so a BackHandler
        // registered outside never sees back-button events while the sheet has focus — the
        // sheet's own predictive-back-to-dismiss handling intercepts them first instead.
        BackHandler(enabled = uiState.backStack.isNotEmpty()) {
            viewModel.goBack()
        }

        // Drilling into a related radical/kanji/vocab is meant to show everything about it
        // regardless of what the original triggering question was gating — the restriction only
        // makes sense for the root subject actually being quizzed. A "Show all" override lifts the
        // same restriction on the root subject itself, for a user who just wants to peek.
        val canShowAll = revealMode == DetailRevealMode.HIDE_UNTIL_ANSWERED &&
            uiState.backStack.isEmpty() &&
            !uiState.forceRevealAll
        val effectiveRevealMode = if (uiState.forceRevealAll || uiState.backStack.isNotEmpty()) {
            DetailRevealMode.FULL
        } else {
            revealMode
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            if (uiState.backStack.isNotEmpty()) {
                IconButton(onClick = { viewModel.goBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (canShowAll) {
                TextButton(onClick = viewModel::toggleForceReveal) {
                    Text("Show all")
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }

        val detail = uiState.detail
        if (uiState.isLoading || detail == null) {
            // A fixed minimum height here keeps the sheet's measured content roughly stable across
            // the loading -> loaded swap. Without it, ModalBottomSheet (whose anchors are derived
            // from measured content height) recomputes those anchors mid-open-animation as this tiny
            // spinner is replaced by the full detail content, which is what produced a visible stall
            // partway up the screen the first time the sheet opened.
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 400.dp).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            SubjectDetailContent(
                detail = detail,
                relatedSubjects = uiState.relatedSubjects,
                revealMode = effectiveRevealMode,
                isAnswered = isAnswered,
                questionType = questionType,
                onRelatedSubjectClick = viewModel::navigateToRelated,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
                showPitchAccent = uiState.showPitchAccent,
                onPlayReading = viewModel::playReading,
                strokeOrder = uiState.strokeOrder
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
