package com.crazyfluff.shellfstudy.shared.feature.studytime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeReport
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeWindow
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun StudyTimeRoute(onBack: () -> Unit, viewModel: StudyTimeViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StudyTimeScreen(
        uiState = uiState,
        onBack = onBack,
        onWindowSelect = viewModel::onWindowSelect,
        onBarSelect = viewModel::onBarSelect
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyTimeScreen(
    uiState: StudyTimeUiState,
    onBack: () -> Unit,
    onWindowSelect: (StudyTimeWindow) -> Unit,
    onBarSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.testTag(StudyTimeTestTags.SCREEN),
        topBar = {
            TopAppBar(
                title = { Text("Study time") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(StudyTimeTestTags.BACK_BUTTON)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        when (uiState) {
            StudyTimeUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            is StudyTimeUiState.Loaded -> if (uiState.report.overview.hasAnyData) {
                StudyTimeContent(
                    report = uiState.report,
                    selectedBarIndex = uiState.selectedBarIndex,
                    onWindowSelect = onWindowSelect,
                    onBarSelect = onBarSelect,
                    contentPadding = innerPadding
                )
            } else {
                StudyTimeEmptyState(goalMs = uiState.report.overview.goalMs, modifier = Modifier.padding(innerPadding))
            }
        }
    }
}

@Composable
private fun StudyTimeEmptyState(goalMs: Long, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp).testTag(StudyTimeTestTags.EMPTY_STATE),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Timer,
            contentDescription = null,
            tint = reviewTimeColor(),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "No study time yet", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Time tracking starts with your next lesson or review. WaniKani doesn't record how long " +
                "you study, so earlier sessions can't be filled in.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your daily goal is ${formatStudyDuration(goalMs)}. You can change it in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StudyTimeContent(
    report: StudyTimeReport,
    selectedBarIndex: Int?,
    onWindowSelect: (StudyTimeWindow) -> Unit,
    onBarSelect: (Int?) -> Unit,
    contentPadding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "today") { TodayCard(report.overview) }
        item(key = "history") {
            HistoryCard(report, selectedBarIndex, onWindowSelect, onBarSelect)
        }
        item(key = "stats") { PeriodStatsCard(report.stats, report.window) }
        item(key = "pace") { PaceCard(report.overview.pace) }
        if (report.levels.isNotEmpty()) {
            item(key = "levels") { LevelsCard(report.levels, report.currentLevel) }
        }
        if (!report.heatmap.isEmpty) {
            item(key = "heatmap") { HeatmapCard(report.heatmap) }
        }
    }
}

@Composable
internal fun SectionCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
