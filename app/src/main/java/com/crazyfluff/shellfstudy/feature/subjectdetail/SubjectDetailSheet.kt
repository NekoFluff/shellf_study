package com.crazyfluff.shellfstudy.feature.subjectdetail

import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.SubjectDetailUiState
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.SubjectDetailViewModel
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.tracing.trace
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.DetailQuestionType
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.DetailRevealMode
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.SubjectDetailContent
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.SubjectDetailTestTags
import kotlinx.coroutines.flow.drop
import kotlin.math.roundToInt

/** Height of the always-present grab strip in its collapsed "peek" state — labeled with "Swipe up
 *  for details" so the affordance is discoverable. Purely the handle's own content height —
 *  system-bar clearance is handled separately by padding the sheet as a whole, not baked in here. */
val SubjectDetailHandleHeight = 56.dp

/** Height of the same grab strip once the sheet is open — just a plain drag pill at that point, no
 *  label needed (the close (X) button in the content header already reads as "dismiss"), so it can
 *  be shorter and hand the reclaimed space to the content underneath. */
private val SubjectDetailOpenHandleHeight = 32.dp

private enum class SheetAnchor { Collapsed, Open }

/**
 * The shared "everything about this subject" sheet. One implementation, two callers: Review's
 * swipe-up-for-details panel (mounted for the whole question, driven by [expanded]/[onToggle]
 * toggling between peeking and open) and the "look something up" sheet from Dashboard, Lesson, and
 * search (mounted only while showing — see [SubjectDetailSheetHost] — which starts it already
 * [expanded] and tears it down once a close settles it back to collapsed). Both get the exact same
 * gesture and the exact same animation.
 *
 * Hosted directly in the caller's own window, not a separate Dialog, so a single
 * [AnchoredDraggableState] drives both the always-visible handle bar and the sheet's open/closed
 * position together — a genuine single drag gesture takes it from "peeking" to "open," rather than
 * a drag on a separate handle merely toggling a boolean that a different, independently-animating
 * sheet then reacts to.
 *
 * The handle is the only draggable surface. Once open, the content column underneath scrolls
 * completely normally — there's no nested-scroll bridging back into the drag state, so scrolling
 * through a long subject's mnemonics can never accidentally start closing the sheet. The trade-off
 * is that closing requires grabbing the handle again (or the close button, or back, or tapping
 * outside) rather than overscrolling the content — an intentional, predictable limitation.
 *
 * [expanded] is the source of truth; this composable is a controlled component that keeps its own
 * gesture-driven [AnchoredDraggableState] in sync with it in both directions — external changes
 * animate the drag position to match, and a user's drag/fling that settles somewhere other than
 * what [expanded] currently says calls [onToggle]. That sync reacts to
 * [AnchoredDraggableState.settledValue], not [AnchoredDraggableState.currentValue] or
 * [AnchoredDraggableState.targetValue] — both of the latter can flip to the opposite anchor well
 * before the user actually lets go (as soon as a drag crosses the anchors' midpoint), which would
 * otherwise tear the content down mid-drag, well before any close animation had actually played.
 * settledValue only moves once a release's fling/animateTo has genuinely finished, so content (and
 * the dim scrim) stay visible through the whole close gesture and its animation, never disappearing
 * until the sheet has truly come to rest.
 *
 * [onToggle] and [onDismiss] look redundant but answer different questions, and must not be
 * collapsed into one. A drag/fling settling somewhere other than [expanded] is the *gesture* telling
 * the caller which way it ended up — genuinely bidirectional (Review's peek handle can settle either
 * Open or Collapsed), so [onToggle] has to be a real flip. The scrim tap, close button, and back
 * handler are discrete actions that always mean "close", never "toggle" — routing them through
 * [onToggle] would risk re-opening the sheet if it ever fired while already collapsed, and previously
 * routing them through a local `collapse()` helper that only animated [dragState] (relying on the
 * settle-mismatch sync above to eventually notice and call [onToggle]) left a real window where a
 * second animateTo could interrupt the first before settledValue ever registered the change, so that
 * sync never fired and [expanded] got stuck out of sync with the now-collapsed sheet. [onDismiss]
 * closes that gap by updating the caller's authoritative state directly and immediately, the same way
 * the handle's own tap-to-toggle already did — the animation is then just a reaction to that change,
 * not a prerequisite for it.
 *
 * [SubjectDetailViewModel] (and the network/DB work behind it) is only ever created once the sheet
 * has actually been asked to open — see [SubjectDetailBody] — not merely because this handle exists,
 * so answering a question you never peek at never pays for a subject-detail fetch.
 *
 * [dismissesFully] controls what "collapsed" settles at. Review keeps this composable mounted the
 * whole time and wants a real resting state to peek from — the default `false` collapses only as
 * far as the handle bar, leaving it visible. [SubjectDetailSheetHost] instead mounts this fresh each
 * time and tears it down the moment it collapses, so there's no peek bar left to rest at — `true`
 * collapses all the way past the bottom of the screen, so the close animation reads as the whole
 * sheet sliding away rather than shrinking down to a bar that then vanishes.
 *
 * [active] lets a caller keep this composable mounted (so its [dragState] and other `remember`ed
 * state survive) for stretches where nothing should actually be visible or interactable — Review's
 * mid-quiz sheet must not show even a bare "Swipe up for details" handle before the current question
 * has been answered (that handle would itself be a spoiler-adjacent affordance, and its content is
 * shown fully revealed with no gating once opened). Always laid out at real size — alpha and
 * interactivity are what actually gate on `active`, not composition or measurement. Measuring this
 * Surface (near-full-screen, elevated, rounded corners, nav-bar-aware) from scratch is itself a real
 * cost (~50ms+ across two frames; see ReviewSubmitJankProfilingTest) — Review resets `rankChange`/
 * `feedback` every question, so an implementation that skips composition, or even just collapses to
 * zero size, while inactive pays that full re-measure cost again on every single question that
 * reveals this sheet, not just the session's first. Keeping it laid out at a fixed real size means
 * that cost lands exactly once per session; every question after that is a cheap alpha flip on an
 * already-measured, already-drawn layer.
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

    // navigationBarsPadding on the Surface below already reserves the bottom system-bar clearance,
    // so it's subtracted here too, otherwise the sheet would be pushed that same amount past the
    // top of the screen.
    val sheetHeightDp = rememberNearFullScreenSheetHeightDp()
    val sheetHeightPx = with(density) { sheetHeightDp.toPx() }
    val handleHeightPx = with(density) { SubjectDetailHandleHeight.toPx() }
    // navigationBarsPadding's reserved clearance sits *below* the sheet's un-offset resting slot,
    // not below the physical screen edge — an offset of just sheetHeightPx lands the handle's top
    // exactly at the top of that clearance, leaving the handle's own height sitting inside it. On a
    // gesture-nav device that clearance is drawn-through translucent, so the handle (and its "Swipe
    // up for details" label) stays visible under the system nav controls even fully "dismissed".
    // Adding the nav bar's own height clears the handle past the physical bottom edge too.
    val navBarBottomPx = with(density) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() }
    val collapsedOffsetPx = if (dismissesFully) {
        sheetHeightPx + navBarBottomPx
    } else {
        (sheetHeightPx - handleHeightPx).coerceAtLeast(0f)
    }

    // Always starts Collapsed regardless of the incoming `expanded` — so a caller that mounts this
    // already-expanded (SubjectDetailSheetHost) still gets the same slide-up-from-the-handle open
    // animation via the LaunchedEffect below, instead of just popping in fully open.
    val dragState = remember {
        trace("subjectDetailSheet:firstMountSetup") {
            AnchoredDraggableState(initialValue = SheetAnchor.Collapsed).apply {
                updateAnchors(
                    DraggableAnchors {
                        SheetAnchor.Open at 0f
                        SheetAnchor.Collapsed at collapsedOffsetPx
                    }
                )
            }
        }
    }

    // External (non-gesture) changes to `expanded` — the initial swipe/tap that first reveals this
    // composable, SubjectDetailSheetHost mounting it already expanded, or a future caller-driven
    // close — animate the drag position to match. Keyed on expanded itself, so this restarts (and
    // reads the fresh value) on every change; no staleness concern here the way there is below.
    LaunchedEffect(expanded) {
        val target = if (expanded) SheetAnchor.Open else SheetAnchor.Collapsed
        if (dragState.targetValue != target) {
            dragState.animateTo(target)
        }
    }

    // A user's drag/fling settling somewhere other than what `expanded` says is the gesture telling
    // the caller to update — for Review that flips isDetailsExpanded back to false; for the look-up
    // sheet (SubjectDetailSheetHost) it dismisses the sheet entirely. This effect is keyed on
    // `dragState` (a stable, never-changing reference), so it launches exactly once and keeps
    // running for this composable's whole lifetime — which means the `expanded`/`onToggle` captured
    // in its closure would otherwise be frozen at whatever they were on that first launch, causing a
    // spurious extra toggle the first time the state actually changes. rememberUpdatedState gives
    // the long-running collector a live reference instead. drop(1) discards snapshotFlow's synthetic
    // first emission (the just-mounted resting value, not a real settle event) — without it, a
    // caller that mounts already expanded would see settledValue's initial Collapsed not match
    // `expanded = true` and fire onToggle before the open animation even starts.
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

    // currentValue/targetValue track the nearest anchor while a drag is still in progress, so a
    // decisive downward drag can flip both to Collapsed well before the user actually lets go.
    // settledValue doesn't move until a release's fling/animateTo has fully finished, so including
    // it here keeps the content (and the dim scrim) visible through the whole close gesture and its
    // animation, only dropping them once the sheet has genuinely come to rest collapsed.
    val isOpenIsh = dragState.targetValue == SheetAnchor.Open ||
        dragState.currentValue == SheetAnchor.Open ||
        dragState.settledValue == SheetAnchor.Open

    // Starting the stroke-order playback before the drag has genuinely settled would make it
    // compete with this sheet's own open animation for frame budget; settledValue is the precise
    // "has it stopped moving" signal for that, same reasoning as isOpenIsh above.
    val strokeOrderSettled = dragState.settledValue == SheetAnchor.Open

    // Always laid out at real size (never a zero-size or absent subtree) — only alpha and
    // interactivity are gated on [active] now, not composition or measurement. Going from a 0dp
    // constraint to this Surface's real (near-full-screen, elevated, rounded-corner) size is itself
    // an expensive measure/layout/draw pass — see ReviewSubmitJankProfilingTest — so gating on size
    // (or, before that, on composition at all) meant paying that cost again on every question that
    // reveals this sheet, not just the session's first. Keeping the real size fixed means that cost
    // is paid exactly once per session, the first time this composable is ever placed; every
    // question after that just flips an already-cached layer's alpha, which is cheap.
    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(if (active) 1f else 0f)
            .then(if (active) Modifier else Modifier.clearAndSetSemantics {})
            .testTag(SubjectDetailTestTags.SHEET_ROOT)
    ) {
        // Dims the rest of the screen only while meaningfully open — never while merely peeking, so
        // the collapsed handle bar behaves passively and doesn't steal touches from what's behind
        // it. Tapping it collapses the sheet the same animated way as dragging the handle down.
        // `enabled = active` (rather than the whole Box's inherited alpha) is what actually stops
        // this from stealing touches meant for the quiz underneath while inactive.
        if (isOpenIsh) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        // Gated on settledValue, not just isOpenIsh/active — the scrim mounts (and
                        // covers whatever triggered the open, e.g. a search result row) as soon as
                        // the open animateTo *starts*, well before it settles. onDismiss (below) is
                        // what makes a stray tap here safe even mid-animation — this gate is purely
                        // to stop a fast second tap from reading as "immediately undo the open you
                        // just triggered": until the sheet has visibly finished opening, that tap
                        // passes through to whatever's underneath instead of dismissing.
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
                // Outermost, so it shifts the sheet's whole footprint — handle included — clear of
                // the system nav bar, rather than being absorbed into the handle's own fixed height
                // and squeezing/hiding its content (see SubjectDetailHandleHeight's doc comment).
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
                        // Just a plain drag pill once open — the close (X) button in the content
                        // header below already says "dismiss", so a second labeled "Hide details"
                        // control here would be redundant chrome eating into content space.
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
 * split out from [SubjectDetailSheet] so [koinViewModel] (and the [SubjectDetailViewModel] fetch it
 * triggers) is only ever invoked once the sheet is genuinely open, not merely because the
 * always-present handle bar exists.
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

    // A plain BackHandler works here (unlike the old ModalBottomSheet-hosted version) because this
    // sheet lives directly in the caller's own window and shares its Activity's single
    // OnBackPressedDispatcher — no separate dialog window with its own dispatcher to worry about.
    BackHandler(enabled = uiState.backStack.isNotEmpty()) { viewModel.goBack() }
    BackHandler(enabled = uiState.backStack.isEmpty(), onBack = onCollapse)

    // Drilling into a related radical/kanji/vocab is meant to show everything about it regardless
    // of what the original triggering question was gating — the restriction only makes sense for
    // the root subject actually being quizzed. A "Show all" override lifts the same restriction on
    // the root subject itself, for a user who just wants to peek.
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
            srsStage = uiState.srsStage
        )
    }
}

/**
 * Reaches almost to the top of the screen — just enough gap below the status bar to read as a
 * sheet, not a full-screen takeover — rather than sizing to content, so callers get a stable height
 * regardless of what's inside.
 */
@Composable
internal fun rememberNearFullScreenSheetHeightDp(): Dp {
    val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    return (screenHeightDp - statusBarTopDp - navBarBottomDp - 12.dp).coerceAtLeast(200.dp)
}
