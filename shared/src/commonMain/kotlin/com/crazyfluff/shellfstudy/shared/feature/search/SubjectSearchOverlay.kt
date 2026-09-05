package com.crazyfluff.shellfstudy.shared.feature.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.crazyfluff.shellfstudy.shared.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.shared.designsystem.PlatformBackHandler
import com.crazyfluff.shellfstudy.shared.designsystem.theme.LocalJapaneseFontFamily
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectTypeLabel
import kotlinx.coroutines.flow.drop

object SearchOverlayTestTags {
    const val TRIGGER_BUTTON = "search_trigger_button"
    const val QUERY_FIELD = "search_query_field"
    const val CLOSE_BUTTON = "search_close_button"
    const val CLEAR_BUTTON = "search_clear_button"
    const val EMPTY_STATE = "search_empty_state"
    const val NO_RESULTS = "search_no_results"
    const val RESULT_ROW_PREFIX = "search_result_"
    const val SYNCING_STATE = "search_syncing_state"
    const val MORE_RESULTS_FOOTER = "search_more_results_footer"
}

/**
 * An in-place, animated search entry point — slides down over the current screen instead of
 * navigating to a separate one. Stateless: the caller's Route hoists the SearchViewModel.
 */
@Composable
fun SubjectSearchOverlay(
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSubjectClick: (Long) -> Unit = {}
) {
    val queryFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(active) {
        if (active) {
            onQueryChange("")
            queryFocusRequester.requestFocus()
        }
    }

    PlatformBackHandler(enabled = active) {
        onActiveChange(false)
    }

    AnimatedVisibility(
        visible = active,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                var isFieldFocused by remember { mutableStateOf(false) }
                val fieldBorderColor by animateColorAsState(
                    targetValue = if (isFieldFocused) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    },
                    label = "searchBarBorder"
                )
                val searchIconTint by animateColorAsState(
                    targetValue = if (isFieldFocused) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "searchIconTint"
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onActiveChange(false) },
                        modifier = Modifier.testTag(SearchOverlayTestTags.CLOSE_BUTTON)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(24.dp)
                            )
                            .border(1.dp, fieldBorderColor, RoundedCornerShape(24.dp))
                            .padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = searchIconTint,
                            modifier = Modifier.size(20.dp)
                        )
                        Box(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            if (uiState.query.isEmpty()) {
                                Text(
                                    text = "Search kanji, vocabulary, radicals",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            // Owned locally rather than driven by `value`/`onValueChange` directly
                            // so the field goes through Compose's modern text-input pipeline instead
                            // of the legacy CoreTextField path, whose IME cursor-anchor bookkeeping
                            // has a framework crash (see LegacyCursorAnchorInfoBuilder). Unlike
                            // QuizAnswerField, this field stays mounted across resets (clearing via
                            // the "X" button, or re-opening the overlay), so — rather than a
                            // reset key — external changes to uiState.query are synced back in
                            // whenever they didn't originate from this field's own typing.
                            val queryFieldState = rememberTextFieldState(uiState.query)
                            LaunchedEffect(queryFieldState) {
                                // Drop the initial emission (the seed value the state was just
                                // created with) — only push up actual edits, so this can't race an
                                // external reset (e.g. the clear button) landing the same frame.
                                snapshotFlow { queryFieldState.text.toString() }.drop(1).collect(onQueryChange)
                            }
                            LaunchedEffect(uiState.query) {
                                if (uiState.query != queryFieldState.text.toString()) {
                                    queryFieldState.setTextAndPlaceCursorAtEnd(uiState.query)
                                }
                            }
                            BasicTextField(
                                state = queryFieldState,
                                lineLimits = TextFieldLineLimits.SingleLine,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(queryFocusRequester)
                                    .onFocusChanged { isFieldFocused = it.isFocused }
                                    .testTag(SearchOverlayTestTags.QUERY_FIELD)
                            )
                        }
                        AnimatedVisibility(
                            visible = uiState.query.isNotEmpty(),
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier.size(28.dp).testTag(SearchOverlayTestTags.CLEAR_BUTTON)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                when {
                    uiState.query.isBlank() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Search every radical, kanji, and vocabulary word in the WaniKani library.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.testTag(SearchOverlayTestTags.EMPTY_STATE)
                                )
                            }
                        }
                    }

                    uiState.isSyncing && uiState.results.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 48.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.testTag(SearchOverlayTestTags.SYNCING_STATE))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Still syncing your subject library…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    uiState.results.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No matches found.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.testTag(SearchOverlayTestTags.NO_RESULTS)
                                )
                            }
                        }
                    }

                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(uiState.results, key = { it.subjectId }) { subject ->
                                    SubjectResultRow(subject) { subjectId ->
                                        focusManager.clearFocus()
                                        onSubjectClick(subjectId)
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                            if (uiState.totalMatchCount > uiState.results.size) {
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text(
                                        text = "Showing ${uiState.results.size} of ${uiState.totalMatchCount} — refine your search.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                            .testTag(SearchOverlayTestTags.MORE_RESULTS_FOOTER)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val ResultRowGlyphInlineId = "glyph"

@Composable
private fun SubjectResultRow(subject: SubjectSummary, onClick: (Long) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(subject.subjectId) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(SearchOverlayTestTags.RESULT_ROW_PREFIX + subject.subjectId)
    ) {
        val imageGlyphUrl = subject.characterImageUrl.takeIf { subject.characters == null }
        val japaneseFontFamily = LocalJapaneseFontFamily.current
        Text(
            text = buildAnnotatedString {
                when {
                    subject.characters != null -> {
                        withStyle(SpanStyle(color = subjectColor(subject.subjectType), fontFamily = japaneseFontFamily)) {
                            append(subject.characters)
                        }
                        append(" — ")
                    }
                    imageGlyphUrl != null -> {
                        appendInlineContent(ResultRowGlyphInlineId, "[glyph]")
                        append(" — ")
                    }
                }
                append(subject.meanings.joinToString(", "))
            },
            inlineContent = if (imageGlyphUrl != null) {
                mapOf(
                    ResultRowGlyphInlineId to InlineTextContent(
                        Placeholder(width = 20.sp, height = 20.sp, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter)
                    ) {
                        AsyncImage(
                            model = imageGlyphUrl,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(subjectColor(subject.subjectType)),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            } else {
                emptyMap()
            },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val reading = subject.readings.firstOrNull()
        Text(
            text = buildAnnotatedString {
                if (reading != null) {
                    withStyle(SpanStyle(fontFamily = japaneseFontFamily)) { append(reading) }
                    append(" · ")
                }
                append("Level ${subject.level} · ${subjectTypeLabel(subject.subjectType)}")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
