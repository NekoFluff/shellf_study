package com.crazyfluff.shellfstudy.shared.designsystem.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.LastSessionKind
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummary
import com.crazyfluff.shellfstudy.shared.data.model.SessionAnswerRow
import com.crazyfluff.shellfstudy.shared.data.model.SessionMissedItemRow
import com.crazyfluff.shellfstudy.shared.designsystem.text.JapaneseText
import com.crazyfluff.shellfstudy.shared.designsystem.theme.CorrectAnswerColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.CorrectAnswerColorDark
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.themeAwareColor

@Composable
fun SessionOverviewCard(
    itemsLabel: String,
    itemsCount: Int,
    correctFirstTry: Int,
    testTags: SessionOverviewCardTestTags,
    modifier: Modifier = Modifier
) {
    val accuracyPercent = if (itemsCount == 0) 0 else correctFirstTry * 100 / itemsCount
    Card(modifier = modifier.testTag(testTags.card)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { accuracyPercent / 100f },
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp,
                    color = themeAwareColor(CorrectAnswerColor, CorrectAnswerColorDark),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text("$accuracyPercent%", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "$itemsLabel: $itemsCount",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag(testTags.itemsText)
                )
                Text(
                    text = "Correct on first try: $correctFirstTry of $itemsCount ($accuracyPercent%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(testTags.correctFirstTryText)
                )
            }
        }
    }
}

@Composable
fun SessionTimingCard(
    totalElapsedMs: Long,
    averageTimePerItemMs: Long,
    averageLabel: String,
    testTags: SessionTimingCardTestTags,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.testTag(testTags.card)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Timing", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Total time: ${formatDuration(totalElapsedMs)}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(testTags.totalTime)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$averageLabel: ${formatDuration(averageTimePerItemMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(testTags.averageTime)
            )
        }
    }
}

@Composable
fun SessionSlowestAnswersCard(
    answers: List<SessionAnswerRow>,
    onSubjectClick: (Long) -> Unit,
    cardTestTag: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.testTag(cardTestTag)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Slowest answers", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            answers.forEach { answer ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onSubjectClick(answer.subjectId) }
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(subjectColor(answer.subjectType))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${answer.label} (${answer.typeLabel}) — ${formatDuration(answer.elapsedMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (answer.isCorrect) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = if (answer.isCorrect) "Correct" else "Incorrect",
                        tint = if (answer.isCorrect) {
                            themeAwareColor(CorrectAnswerColor, CorrectAnswerColorDark)
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SessionMissedItemsCard(
    items: List<SessionMissedItemRow>,
    onSubjectClick: (Long) -> Unit,
    cardTestTag: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.testTag(cardTestTag)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Missed items", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    val color = subjectColor(item.subjectType)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSubjectClick(item.subjectId) }
                            .background(color.copy(alpha = 0.12f))
                            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        JapaneseText(text = item.label, color = color, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
    }
}

/** Test tags for [SessionCompleteContent] — one bundle per feature (Lesson/Review), each following
 *  the same 1:1 naming scheme their screen's own test-tag object already used. */
/** [SessionOverviewCard]'s own test tags — three nodes that can only ever travel together. */
data class SessionOverviewCardTestTags(
    val card: String,
    val itemsText: String,
    val correctFirstTryText: String
)

/** [SessionTimingCard]'s own test tags. */
data class SessionTimingCardTestTags(
    val card: String,
    val totalTime: String,
    val averageTime: String
)


data class SessionCompleteTestTags(
    val root: String,
    val overviewCard: String,
    val itemsText: String,
    val correctFirstTryText: String,
    val timingCard: String,
    val totalTimeText: String,
    val averageTimeText: String,
    val slowestCard: String,
    val missedCard: String,
    val doneButton: String
) {
    /** [SessionOverviewCard]'s sub-bundle, so the card takes one value instead of three loose tags. */
    val overviewCardTags: SessionOverviewCardTestTags
        get() = SessionOverviewCardTestTags(
            card = overviewCard,
            itemsText = itemsText,
            correctFirstTryText = correctFirstTryText
        )

    /** [SessionTimingCard]'s sub-bundle. */
    val timingCardTags: SessionTimingCardTestTags
        get() = SessionTimingCardTestTags(
            card = timingCard,
            totalTime = totalTimeText,
            averageTime = averageTimeText
        )
}

/**
 * The session figures [SessionCompleteContent] renders, plus which kind of session produced them.
 *
 * [kind] travels with the figures because it is what decides the two "Items ..." labels — and
 * deriving those at each call site had already drifted into three copies: two hardcoded strings (in
 * the lesson and review screens) and a pair of private helpers in `LastSessionSummaryScreen`.
 */
data class SessionSummaryDisplay(
    val kind: LastSessionKind,
    val itemsCount: Int,
    val correctFirstTry: Int,
    val totalElapsedMs: Long,
    val averageTimePerItemMs: Long,
    val slowestAnswers: List<SessionAnswerRow>,
    val missedItems: List<SessionMissedItemRow>
) {
    val itemsLabel: String
        get() = when (kind) {
            LastSessionKind.LESSON -> "Items learned"
            LastSessionKind.REVIEW -> "Items reviewed"
        }

    val averageLabel: String
        get() = when (kind) {
            LastSessionKind.LESSON -> "Avg. time per item learned"
            LastSessionKind.REVIEW -> "Avg. time per item reviewed"
        }
}

/** A persisted snapshot as the display value above — the one place a stored summary becomes one. */
fun LastSessionSummary.toSessionSummaryDisplay(): SessionSummaryDisplay = SessionSummaryDisplay(
    kind = kind,
    itemsCount = itemsCount,
    correctFirstTry = correctFirstTry,
    totalElapsedMs = totalElapsedMs,
    averageTimePerItemMs = averageTimePerItemMs,
    slowestAnswers = slowestAnswers,
    missedItems = missedItems
)

/** The "session complete" screen shown after a lesson or review session finishes — shared between
 *  both features, which previously carried near-identical copies of this composable. */
@Composable
fun SessionCompleteContent(
    title: String,
    subtitle: String?,
    summary: SessionSummaryDisplay,
    onDone: () -> Unit,
    onSubjectClick: (Long) -> Unit,
    testTags: SessionCompleteTestTags,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(testTags.root)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Celebration,
                contentDescription = null,
                tint = themeAwareColor(CorrectAnswerColor, CorrectAnswerColorDark)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium)
        }
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().testTag(testTags.doneButton)
        ) { Text("Back to dashboard") }

        if (summary.itemsCount > 0) {
            Spacer(modifier = Modifier.height(24.dp))
            SessionOverviewCard(
                itemsLabel = summary.itemsLabel,
                itemsCount = summary.itemsCount,
                correctFirstTry = summary.correctFirstTry,
                testTags = testTags.overviewCardTags,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            SessionTimingCard(
                totalElapsedMs = summary.totalElapsedMs,
                averageTimePerItemMs = summary.averageTimePerItemMs,
                averageLabel = summary.averageLabel,
                testTags = testTags.timingCardTags,
                modifier = Modifier.fillMaxWidth()
            )
            if (summary.slowestAnswers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                SessionSlowestAnswersCard(
                    answers = summary.slowestAnswers,
                    onSubjectClick = onSubjectClick,
                    cardTestTag = testTags.slowestCard,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (summary.missedItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                SessionMissedItemsCard(
                    items = summary.missedItems,
                    onSubjectClick = onSubjectClick,
                    cardTestTag = testTags.missedCard,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "$minutes:${seconds.toString().padStart(2, '0')}" else "${seconds}s"
}
