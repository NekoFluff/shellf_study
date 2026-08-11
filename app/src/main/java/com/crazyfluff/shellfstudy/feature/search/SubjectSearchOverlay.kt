package com.crazyfluff.shellfstudy.feature.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.core.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.core.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.core.designsystem.theme.subjectTypeLabel

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
 * navigating to a separate one. Deliberately a plain Surface/BasicTextField rather than
 * Material3's `SearchBar`: that component expects to be composed collapsed-then-expanded so it
 * has a stable anchor to animate from, but this is triggered from a separate icon and jumps
 * straight to "expanded", which left the real `SearchBar` rendering at the wrong position. This
 * gives the same slide-in/fade animation with fully predictable placement, styled as a compact
 * single-row pill instead of a full-height `OutlinedTextField` box.
 *
 * Stateless like the rest of this app's screens: the caller's Route hoists the [SearchViewModel]
 * and passes its state down, so this composable stays testable without a Hilt-aware host.
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
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
                            .height(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(22.dp)
                            )
                            .padding(horizontal = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            if (uiState.query.isEmpty()) {
                                Text(
                                    text = "Search kanji, vocabulary, radicals",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = uiState.query,
                                onValueChange = onQueryChange,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(SearchOverlayTestTags.QUERY_FIELD)
                            )
                        }
                        if (uiState.query.isNotEmpty()) {
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
                            Text(
                                text = "Search every radical, kanji, and vocabulary word in the WaniKani library.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp)
                                    .testTag(SearchOverlayTestTags.EMPTY_STATE)
                            )
                        }
                    }

                    uiState.isSyncing && uiState.results.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(top = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(modifier = Modifier.testTag(SearchOverlayTestTags.SYNCING_STATE))
                            Text(
                                text = "Still syncing your subject library…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }

                    uiState.results.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            Text(
                                text = "No matches found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 24.dp).testTag(SearchOverlayTestTags.NO_RESULTS)
                            )
                        }
                    }

                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(uiState.results, key = { it.subjectId }) { subject ->
                                    SubjectResultRow(subject, onSubjectClick)
                                }
                            }
                            if (uiState.totalMatchCount > uiState.results.size) {
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

@Composable
private fun SubjectResultRow(subject: SubjectSummary, onClick: (Long) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(subject.subjectId) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(SearchOverlayTestTags.RESULT_ROW_PREFIX + subject.subjectId)
    ) {
        // The main line spans the full row width instead of sharing it with a leading glyph
        // column, so a long vocabulary word like お誕生日おめでとう has room to render on one line
        // instead of wrapping character-by-character in a narrow fixed-width slot.
        Text(
            text = buildAnnotatedString {
                if (subject.characters != null) {
                    withStyle(SpanStyle(color = subjectColor(subject.subjectType))) {
                        append(subject.characters)
                    }
                    append(" — ")
                }
                append(subject.meanings.joinToString(", "))
            },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val reading = subject.readings.firstOrNull()
        Text(
            text = listOfNotNull(reading, "Level ${subject.level}", subjectTypeLabel(subject.subjectType))
                .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
