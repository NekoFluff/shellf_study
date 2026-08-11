package com.crazyfluff.shellfstudy.feature.subjectdetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.DetailQuestionType
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.DetailRevealMode
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.SubjectDetailContent
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.SubjectDetailTestTags
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Height of the always-present grab strip, both as the collapsed "peek" bar and as the drag
 *  handle once the sheet is pulled open. Purely the handle's own content height — system-bar
 *  clearance is handled separately by padding the sheet as a whole, not baked in here. */
val DetailPeekHandleHeight = 56.dp

private enum class PeekAnchor { Collapsed, Open }

/**
 * The swipe-up-for-details panel used mid-review: unlike [SubjectDetailSheet] (which hosts
 * [SubjectDetailContent] in a Material3 [androidx.compose.material3.ModalBottomSheet] — its own
 * dialog window, used for the search/lookup detail views elsewhere), this hosts it directly in the
 * caller's own window so the same [AnchoredDraggableState] instance drives both the always-visible
 * handle bar and the sheet's open/closed position — a genuine single drag gesture takes it from
 * "peeking" to "open," rather than a drag on a separate handle merely toggling a boolean that a
 * different, independently-animating sheet then reacts to.
 *
 * The handle is the only draggable surface. Once open, the content column underneath scrolls
 * completely normally — there's no nested-scroll bridging back into the drag state, so scrolling
 * through a long subject's mnemonics can never accidentally start closing the sheet. The trade-off
 * is that closing requires grabbing the handle again (or the close button, or back) rather than
 * overscrolling the content, which is an intentional, predictable limitation, not an oversight.
 *
 * [expanded] is the source of truth (mirrors [com.crazyfluff.shellfstudy.feature.review.ReviewUiState.isDetailsExpanded]);
 * this composable is a controlled component that keeps its own gesture-driven [AnchoredDraggableState]
 * in sync with it in both directions — external changes animate the drag position to match, and a
 * user's drag/fling that settles somewhere other than what [expanded] currently says calls [onToggle].
 *
 * [SubjectDetailViewModel] (and the network/DB work behind it) is only ever created once the sheet
 * has actually been asked to open — see [DetailPeekBody] — not merely because this handle exists,
 * so answering a question you never peek at never pays for a subject-detail fetch.
 */
@Composable
fun DetailPeekSheet(
    subjectId: Long,
    expanded: Boolean,
    onToggle: () -> Unit,
    revealMode: DetailRevealMode,
    isAnswered: Boolean,
    questionType: DetailQuestionType,
    modifier: Modifier = Modifier,
    handleTestTag: String = SubjectDetailTestTags.PEEK_HANDLE
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Reaches almost to the top of the screen (matching the old ModalBottomSheet's "Expanded"
    // reach) rather than an arbitrary fraction of it — just enough gap below the status bar to
    // read as a sheet, not a full-screen takeover. navigationBarsPadding on the Surface below
    // already reserves the bottom system-bar clearance, so it's subtracted here too, otherwise
    // the sheet would be pushed the same amount past the top of the screen.
    val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val sheetHeightDp = (screenHeightDp - statusBarTopDp - navBarBottomDp - 12.dp).coerceAtLeast(200.dp)
    val sheetHeightPx = with(density) { sheetHeightDp.toPx() }
    val handleHeightPx = with(density) { DetailPeekHandleHeight.toPx() }
    val collapsedOffsetPx = (sheetHeightPx - handleHeightPx).coerceAtLeast(0f)

    val dragState = remember {
        AnchoredDraggableState(initialValue = PeekAnchor.Collapsed).apply {
            updateAnchors(
                DraggableAnchors {
                    PeekAnchor.Open at 0f
                    PeekAnchor.Collapsed at collapsedOffsetPx
                }
            )
        }
    }

    // External (non-gesture) changes to `expanded` — the initial swipe/tap that first reveals this
    // composable, or a future caller-driven close — animate the drag position to match. Keyed on
    // expanded itself, so this restarts (and reads the fresh value) on every change; no staleness
    // concern here the way there is below.
    LaunchedEffect(expanded) {
        val target = if (expanded) PeekAnchor.Open else PeekAnchor.Collapsed
        if (dragState.targetValue != target) {
            dragState.animateTo(target)
        }
    }

    // A user's drag/fling settling somewhere other than what `expanded` says is the gesture telling
    // the ViewModel to update — mirrors how the old ModalBottomSheet's targetValue-flips-to-Hidden
    // used to drive onDismiss. This effect is keyed on `dragState` (a stable, never-changing
    // reference), so it launches exactly once and keeps running for this composable's whole
    // lifetime — which means the `expanded`/`onToggle` captured in its closure would otherwise be
    // frozen at whatever they were on that first launch (almost always `expanded = false`), causing
    // a spurious extra toggle the very first time the state actually changes. rememberUpdatedState
    // gives the long-running collector a live reference instead.
    val currentExpanded by rememberUpdatedState(expanded)
    val currentOnToggle by rememberUpdatedState(onToggle)
    LaunchedEffect(dragState) {
        snapshotFlow { dragState.settledValue }.collect { settled ->
            val settledExpanded = settled == PeekAnchor.Open
            if (settledExpanded != currentExpanded) currentOnToggle()
        }
    }

    val isOpenIsh = dragState.targetValue == PeekAnchor.Open || dragState.currentValue == PeekAnchor.Open

    Box(modifier = modifier.fillMaxSize()) {
        // Dims the rest of the screen only while meaningfully open — never while merely peeking,
        // so the collapsed handle bar behaves like today's: fully passive, doesn't steal touches
        // from the quiz content behind it.
        if (isOpenIsh) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { scope.launch { dragState.animateTo(PeekAnchor.Collapsed) } }
            )
        }

        Surface(
            tonalElevation = 3.dp,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            modifier = Modifier
                // Outermost, so it shifts the sheet's whole footprint — handle included — clear of
                // the system nav bar, rather than being absorbed into the handle's own fixed height
                // and squeezing/hiding its content (see DetailPeekHandleHeight's doc comment).
                .navigationBarsPadding()
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeightDp)
                .offset { IntOffset(0, dragState.requireOffset().roundToInt()) }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DetailPeekHandleHeight)
                        .anchoredDraggable(dragState, Orientation.Vertical)
                        .clickable(onClick = onToggle)
                        .testTag(handleTestTag)
                ) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isOpenIsh) "Hide details" else "Swipe up for details",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isOpenIsh) {
                    DetailPeekBody(
                        subjectId = subjectId,
                        revealMode = revealMode,
                        isAnswered = isAnswered,
                        questionType = questionType,
                        onCollapse = { scope.launch { dragState.animateTo(PeekAnchor.Collapsed) } }
                    )
                }
            }
        }
    }
}

/**
 * The actual subject-detail content, plus its back/drill-down handling — split out from
 * [DetailPeekSheet] so [hiltViewModel] (and the [SubjectDetailViewModel] fetch it triggers) is only
 * ever invoked once the sheet is genuinely open, not merely because the always-present handle bar
 * exists.
 */
@Composable
private fun ColumnScope.DetailPeekBody(
    subjectId: Long,
    revealMode: DetailRevealMode,
    isAnswered: Boolean,
    questionType: DetailQuestionType,
    onCollapse: () -> Unit,
    viewModel: SubjectDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(subjectId) { viewModel.open(subjectId) }
    DisposableEffect(Unit) { onDispose { viewModel.stopPlayback() } }

    BackHandler(enabled = uiState.backStack.isNotEmpty()) { viewModel.goBack() }
    BackHandler(enabled = uiState.backStack.isEmpty(), onBack = onCollapse)

    if (uiState.backStack.isNotEmpty()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            IconButton(onClick = { viewModel.goBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    }

    val canShowAll = revealMode == DetailRevealMode.HIDE_UNTIL_ANSWERED &&
        uiState.backStack.isEmpty() &&
        !uiState.forceRevealAll
    val effectiveRevealMode = if (uiState.forceRevealAll || uiState.backStack.isNotEmpty()) {
        DetailRevealMode.FULL
    } else {
        revealMode
    }

    if (canShowAll) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::toggleForceReveal) {
                Text("Show all")
            }
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
            revealMode = effectiveRevealMode,
            isAnswered = isAnswered,
            questionType = questionType,
            onRelatedSubjectClick = viewModel::navigateToRelated,
            modifier = Modifier
                .weight(1f)
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
            showPitchAccent = uiState.showPitchAccent,
            onPlayReading = viewModel::playReading,
            strokeOrder = uiState.strokeOrder
        )
    }
}
