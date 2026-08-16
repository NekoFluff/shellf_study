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
import androidx.compose.runtime.Composable
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
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.SubjectDetailContent
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.SubjectDetailTestTags
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.SubjectDetailViewModel
import kotlinx.coroutines.flow.drop
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/** Height of the always-present grab strip in its collapsed "peek" state. */
val SubjectDetailHandleHeight = 56.dp

private val SubjectDetailOpenHandleHeight = 32.dp

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
        if (isOpenIsh) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        enabled = active && dragState.settledValue == SheetAnchor.Open,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss
                    )
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isOpenIsh) SubjectDetailOpenHandleHeight else SubjectDetailHandleHeight)
                        .then(if (active) Modifier.anchoredDraggable(dragState, Orientation.Vertical) else Modifier)
                        .clickable(enabled = active, onClick = onToggle)
                        .testTag(handleTestTag)
                ) {
                    if (isOpenIsh) {
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        Spacer(modifier = Modifier.width(16.dp))
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Swipe up for details",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (isOpenIsh) {
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
            strokeOrder = uiState.strokeOrder,
            autoPlayStrokeOrder = autoPlayStrokeOrder,
            showStrokeOrder = uiState.showStrokeOrder,
            srsStage = uiState.srsStage
        )
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
