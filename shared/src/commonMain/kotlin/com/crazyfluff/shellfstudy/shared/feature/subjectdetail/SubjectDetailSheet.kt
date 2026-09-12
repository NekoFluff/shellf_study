package com.crazyfluff.shellfstudy.shared.feature.subjectdetail

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
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.designsystem.PlatformBackHandler
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.DetailQuestionType
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.DetailRevealMode
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.LocalPitchAccentCheck
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentCheck
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.SubjectDetailContent
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.canOfferForceReveal
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.resolveEffectiveRevealMode
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.SubjectDetailHandleHeight
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.SubjectDetailTestTags
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.SubjectDetailViewModel
import kotlinx.coroutines.flow.drop
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

private enum class SheetAnchor { Collapsed, Open }

/**
 * The shared "everything about this subject" sheet. One implementation, two callers: Review's
 * swipe-up-for-details panel and the "look something up" sheet from Dashboard, Lesson, and search.
 * See the original doc comment in SubjectDetailSheet for full details.
 */
@Composable
fun SubjectDetailSheet(
    subjectId: Long,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    revealMode: DetailRevealMode,
    isAnswered: Boolean,
    questionType: DetailQuestionType?,
    modifier: Modifier = Modifier,
    handleTestTag: String = SubjectDetailTestTags.PEEK_HANDLE,
    dismissesFully: Boolean = false,
    active: Boolean = true
) {
    val density = LocalDensity.current

    val sheetHeightDp = rememberNearFullScreenSheetHeightDp()
    val sheetHeightPx = with(density) { sheetHeightDp.toPx() }
    val handleHeightPx = with(density) { SubjectDetailHandleHeight.toPx() }
    val navBarBottomPx = with(density) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() }
    val collapsedOffsetPx = if (dismissesFully) {
        sheetHeightPx + navBarBottomPx
    } else {
        (sheetHeightPx - handleHeightPx).coerceAtLeast(0f)
    }

    val dragState = remember {
        AnchoredDraggableState(initialValue = SheetAnchor.Collapsed).apply {
            updateAnchors(
                DraggableAnchors {
                    SheetAnchor.Open at 0f
                    SheetAnchor.Collapsed at collapsedOffsetPx
                }
            )
        }
    }

    LaunchedEffect(expanded) {
        val target = if (expanded) SheetAnchor.Open else SheetAnchor.Collapsed
        if (dragState.targetValue != target) {
            dragState.animateTo(target)
        }
    }

    val currentExpanded by rememberUpdatedState(expanded)
    val currentOnToggle by rememberUpdatedState(onToggle)
    LaunchedEffect(dragState) {
        snapshotFlow { dragState.settledValue }
            .drop(1)
            .collect { settled ->
                val settledExpanded = settled == SheetAnchor.Open
                if (settledExpanded != currentExpanded) currentOnToggle()
            }
    }

    val isOpenIsh = dragState.targetValue == SheetAnchor.Open ||
        dragState.currentValue == SheetAnchor.Open ||
        dragState.settledValue == SheetAnchor.Open

    val strokeOrderSettled = dragState.settledValue == SheetAnchor.Open

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(if (active) 1f else 0f)
            .then(if (active) Modifier else Modifier.clearAndSetSemantics {})
            .testTag(SubjectDetailTestTags.SHEET_ROOT)
    ) {
        // Gated on `active` too, not just `isOpenIsh`: a stale `expanded` flag left over from a
        // finished session must not leave this scrim/body hit-testable underneath other screens
        // — see the session-complete "Back to dashboard" dropped-tap bug this caused.
        if (isOpenIsh && active) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        enabled = dragState.settledValue == SheetAnchor.Open,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss
                    )
            )
            // The sheet's own Surface stops above the navigation bar inset (see its
            // .navigationBarsPadding() below), so on edge-to-edge devices with a translucent nav
            // bar this strip is what shows through it — tint it to match the sheet instead of
            // leaving the dimmed scrim (and the screen behind it) visible through the OS controls.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
            )
        }

        Surface(
            tonalElevation = 3.dp,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            modifier = Modifier
                .navigationBarsPadding()
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeightDp)
                .offset { IntOffset(0, dragState.requireOffset().roundToInt()) }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // The collapsed peek strip. Still composed once the sheet is open (at zero height)
                // rather than removed: the in-flight drag that opened the sheet is delivered to this
                // node, so dropping it mid-gesture would cancel that drag and strand the sheet at a
                // partial offset. Open, the body owns the top of the sheet — the X closes it.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isOpenIsh) 0.dp else SubjectDetailHandleHeight)
                        .then(if (active) Modifier.anchoredDraggable(dragState, Orientation.Vertical) else Modifier)
                        .then(if (active) Modifier.clickable(onClick = onToggle) else Modifier)
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
                        text = "Tap to view details",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isOpenIsh && active) {
                    SubjectDetailBody(
                        subjectId = subjectId,
                        revealMode = revealMode,
                        isAnswered = isAnswered,
                        questionType = questionType,
                        onCollapse = onDismiss,
                        autoPlayStrokeOrder = strokeOrderSettled
                    )
                }
            }
        }
    }
}

/**
 * The actual subject-detail content, plus its back/drill-down handling and the close (X) button —
 * split out from [SubjectDetailSheet] so [koinViewModel] is only invoked once the sheet is open.
 */
@Composable
private fun ColumnScope.SubjectDetailBody(
    subjectId: Long,
    revealMode: DetailRevealMode,
    isAnswered: Boolean,
    questionType: DetailQuestionType?,
    onCollapse: () -> Unit,
    autoPlayStrokeOrder: Boolean,
    viewModel: SubjectDetailViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(subjectId) { viewModel.open(subjectId) }
    DisposableEffect(Unit) { onDispose { viewModel.stopPlayback() } }

    PlatformBackHandler(enabled = uiState.backStack.isNotEmpty()) { viewModel.goBack() }
    PlatformBackHandler(enabled = uiState.backStack.isEmpty(), onBack = onCollapse)

    val canShowAll = canOfferForceReveal(revealMode, uiState.backStack.isNotEmpty(), uiState.forceRevealAll)
    val effectiveRevealMode = resolveEffectiveRevealMode(revealMode, uiState.backStack.isNotEmpty(), uiState.forceRevealAll)

    // Top padding is what keeps the controls off the sheet's rounded top edge: open, this row is the
    // first thing in the Surface (the peek strip above it collapses to zero height), so without it the
    // 48dp icon buttons sit flush against the corner. Kept modest on the bottom so the content's own
    // 8dp top padding is what separates the header from the glyph.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)
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
        IconButton(onClick = onCollapse) {
            Icon(Icons.Filled.Close, contentDescription = "Close")
        }
    }

    val detail = uiState.detail
    if (uiState.isLoading || detail == null) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        // Remembered so a reading only recomposes when the check state flips, not on every unrelated
        // uiState change (the detail flow re-emits for settings, scroll offsets, stats, ...).
        val pitchAccentCheck = remember(uiState.isCheckingPitchAccent, uiState.pitchAccentCheckFailed, viewModel) {
            PitchAccentCheck(
                inProgress = uiState.isCheckingPitchAccent,
                failed = uiState.pitchAccentCheckFailed,
                onClick = viewModel::checkPitchAccent
            )
        }
        CompositionLocalProvider(LocalPitchAccentCheck provides pitchAccentCheck) {
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
                restrictAudioToMp3 = uiState.restrictAudioToMp3,
                strokeOrder = uiState.strokeOrder,
                autoPlayStrokeOrder = autoPlayStrokeOrder,
                showStrokeOrder = uiState.showStrokeOrder,
                hideContextSentenceTranslations = uiState.hideContextSentenceTranslations,
                assignmentStats = uiState.assignmentStats,
                reviewStats = uiState.reviewStats,
                initialScrollOffset = uiState.pendingScrollOffset,
                onScrollPositionChanged = { viewModel.recordScrollOffset(detail.subjectId, it) }
            )
        }
    }
}

/** Reaches almost to the top of the screen so callers get a stable height regardless of content. */
@Composable
internal fun rememberNearFullScreenSheetHeightDp(): Dp {
    val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val screenHeightDp = with(density) { windowInfo.containerSize.height.toDp() }
    return (screenHeightDp - statusBarTopDp - navBarBottomDp - 12.dp).coerceAtLeast(200.dp)
}
