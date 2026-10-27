package com.crazyfluff.shellfstudy.shared.feature.search

import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.LocalOpenSubjectDetail
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.input.TextFieldLineLimits
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.crazyfluff.shellfstudy.shared.data.model.SearchFilters
import com.crazyfluff.shellfstudy.shared.data.model.SentenceSearchResult
import com.crazyfluff.shellfstudy.shared.designsystem.components.Pill
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.crazyfluff.shellfstudy.shared.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.shared.designsystem.PlatformBackHandler
import com.crazyfluff.shellfstudy.shared.designsystem.text.rememberPushUpTextFieldState
import com.crazyfluff.shellfstudy.shared.designsystem.theme.LocalJapaneseFontFamily
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectTypeLabel

object SearchOverlayTestTags {
    const val QUERY_FIELD = "search_query_field"
    const val CLOSE_BUTTON = "search_close_button"
    const val CLEAR_BUTTON = "search_clear_button"
    const val EMPTY_STATE = "search_empty_state"
    const val NO_RESULTS = "search_no_results"
    const val RESULT_ROW_PREFIX = "search_result_"
    const val SENTENCE_ROW_PREFIX = "search_sentence_"
    const val TYPE_CHIP_PREFIX = "search_type_chip_"
    const val UNLOCKED_ONLY_CHIP = "search_unlocked_only_chip"
    const val TAB_SUBJECTS = "search_tab_subjects"
    const val TAB_SENTENCES = "search_tab_sentences"
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
    actions: SearchActions,
    modifier: Modifier = Modifier
) {
    val onQueryChange = actions.onQueryChange
    val queryFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val openSubjectDetail = LocalOpenSubjectDetail.current
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
            // The overlay isn't a Scaffold, so it pads the bottom insets itself: without this the results
            // and the "Showing N of M" footer run under the edge-to-edge nav bar (and the keyboard).
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            ) {
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
                            // Unlike QuizAnswerField, this field stays mounted across resets
                            // (clearing via the "X" button, or re-opening the overlay), so — rather
                            // than a reset key — external changes to uiState.query are synced back
                            // in below whenever they didn't originate from this field's own typing.
                            val queryFieldState = rememberPushUpTextFieldState(uiState.query, onQueryChange)
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

                SearchFilterRow(filters = uiState.filters, onFiltersChange = actions.onFiltersChange)

                if (uiState.query.isBlank()) {
                    SearchMessage(
                        icon = Icons.Default.Search,
                        text = "Search every radical, kanji, vocabulary word and example sentence " +
                            "in the WaniKani library.",
                        testTag = SearchOverlayTestTags.EMPTY_STATE
                    )
                } else {
                    SearchTabRow(uiState = uiState, onTabChange = actions.onTabChange)
                    val onOpenSubject: (Long) -> Unit = { subjectId ->
                        focusManager.clearFocus()
                        openSubjectDetail(subjectId)
                    }
                    when (uiState.tab) {
                        SearchTab.SUBJECTS -> SearchResultList(
                            items = uiState.results,
                            totalCount = uiState.totalMatchCount,
                            isSyncing = uiState.isSyncing,
                            noResultsText = "No matches found.",
                            key = { it.subjectId }
                        ) { subject -> SubjectResultRow(subject, onOpenSubject) }
                        SearchTab.SENTENCES -> SearchResultList(
                            items = uiState.sentenceResults,
                            totalCount = uiState.sentenceTotalCount,
                            isSyncing = uiState.isSyncing,
                            noResultsText = "No example sentences match.",
                            key = null
                        ) { sentence -> SentenceResultRow(sentence, uiState.query.trim(), onOpenSubject) }
                    }
                }
            }
        }
    }
}

/** The type chips (none on means any type) and the "Unlocked" chip, scrolling sideways when the
 *  row outgrows a narrow screen. */
@Composable
private fun SearchFilterRow(filters: SearchFilters, onFiltersChange: (SearchFilters) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        FilterTypes.forEach { type ->
            val selected = type in filters.types
            Pill(
                selected = selected,
                onClick = {
                    val types = if (selected) filters.types - type else filters.types + type
                    onFiltersChange(filters.copy(types = types))
                },
                text = subjectTypeLabel(type),
                modifier = Modifier.testTag(SearchOverlayTestTags.TYPE_CHIP_PREFIX + type.name)
            )
        }
        Pill(
            selected = filters.unlockedOnly,
            onClick = { onFiltersChange(filters.copy(unlockedOnly = !filters.unlockedOnly)) },
            text = "Unlocked",
            modifier = Modifier.testTag(SearchOverlayTestTags.UNLOCKED_ONLY_CHIP)
        )
    }
}

/** Kana-only vocabulary rides along with the Vocabulary chip — see [SearchFilters.types]. */
private val FilterTypes = listOf(SubjectType.RADICAL, SubjectType.KANJI, SubjectType.VOCABULARY)

@Composable
private fun SearchTabRow(uiState: SearchUiState, onTabChange: (SearchTab) -> Unit) {
    PrimaryTabRow(selectedTabIndex = uiState.tab.ordinal, containerColor = MaterialTheme.colorScheme.surface) {
        Tab(
            selected = uiState.tab == SearchTab.SUBJECTS,
            onClick = { onTabChange(SearchTab.SUBJECTS) },
            text = { Text("Subjects (${uiState.totalMatchCount})") },
            modifier = Modifier.testTag(SearchOverlayTestTags.TAB_SUBJECTS)
        )
        Tab(
            selected = uiState.tab == SearchTab.SENTENCES,
            onClick = { onTabChange(SearchTab.SENTENCES) },
            text = { Text("Sentences (${uiState.sentenceTotalCount})") },
            modifier = Modifier.testTag(SearchOverlayTestTags.TAB_SENTENCES)
        )
    }
}

/** One tab's results — a syncing spinner or "no matches" when there are none, otherwise the list,
 *  with a footer when [totalCount] is more than the capped [items] shown. */
@Composable
private fun <T> SearchResultList(
    items: List<T>,
    totalCount: Int,
    isSyncing: Boolean,
    noResultsText: String,
    key: ((T) -> Any)?,
    row: @Composable (T) -> Unit
) {
    when {
        isSyncing && items.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 48.dp)
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Still syncing your subject library…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items.isEmpty() -> SearchMessage(
            icon = Icons.Default.Close,
            text = noResultsText,
            testTag = SearchOverlayTestTags.NO_RESULTS
        )

        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(items, key = key) { item ->
                        row(item)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
                if (totalCount > items.size) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            text = "Showing ${items.size} of $totalCount — refine your search.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchMessage(icon: ImageVector, text: String, testTag: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 48.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}

/** A matching context sentence: the Japanese with the match highlighted, the English below it
 *  (highlighted too, case-insensitively), and the word it illustrates. */
@Composable
private fun SentenceResultRow(sentence: SentenceSearchResult, query: String, onClick: (Long) -> Unit) {
    val japaneseFontFamily = LocalJapaneseFontFamily.current
    val highlight = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(sentence.subjectId) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(SearchOverlayTestTags.SENTENCE_ROW_PREFIX + sentence.subjectId)
    ) {
        Text(
            text = highlightMatches(sentence.japanese, query, highlight),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = japaneseFontFamily)
        )
        Text(
            text = highlightMatches(sentence.english, query, highlight),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            text = buildAnnotatedString {
                sentence.characters?.let {
                    withStyle(SpanStyle(color = subjectColor(sentence.subjectType), fontFamily = japaneseFontFamily)) {
                        append(it)
                    }
                    append(" · ")
                }
                append(subjectTypeLabel(sentence.subjectType))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** [text] with every case-insensitive occurrence of [query] in [style] — the same matching the
 *  sentence search itself does, so the span shown is the one that matched. */
fun highlightMatches(text: String, query: String, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(text)
    if (query.isEmpty()) return@buildAnnotatedString
    var from = text.indexOf(query, ignoreCase = true)
    while (from >= 0) {
        addStyle(style, from, from + query.length)
        from = text.indexOf(query, startIndex = from + query.length, ignoreCase = true)
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
