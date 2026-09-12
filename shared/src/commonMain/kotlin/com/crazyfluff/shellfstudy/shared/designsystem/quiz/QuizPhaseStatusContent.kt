package com.crazyfluff.shellfstudy.shared.designsystem.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Full-screen centered spinner — Lesson and Review's identical `Phase.Loading` rendering, owned
 * here once instead of copied into each screen.
 */
@Composable
fun QuizLoadingContent(loadingIndicatorTestTag: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.testTag(loadingIndicatorTestTag))
    }
}

/** Test tags for [QuizErrorContent] — one bundle per feature (Lesson/Review), same shape as
 *  [QuizQuestionTestTags]. */
data class QuizErrorTestTags(
    val errorText: String,
    val retryButton: String,
    val studyOfflineButton: String
)

/**
 * A load failure with two ways forward: retry the fetch, or fall back to whatever is already
 * cached. Lesson and Review hit this identically — same copy, same layout — when their initial
 * queue fetch fails.
 */
@Composable
fun QuizErrorContent(
    message: String,
    onRetry: () -> Unit,
    onStudyOffline: () -> Unit,
    testTags: QuizErrorTestTags,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(testTags.errorText)
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry, modifier = Modifier.testTag(testTags.retryButton)) { Text("Retry") }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onStudyOffline, modifier = Modifier.testTag(testTags.studyOfflineButton)) {
            Text("Study offline with cached data")
        }
    }
}

/** Test tags for [QuizEmptyQueueContent]. */
data class QuizEmptyQueueTestTags(
    val messageText: String,
    val doneButton: String
)

/**
 * Nothing due — Lesson's "no lessons available" and Review's "no reviews available" are the same
 * shape (headline message, "Back to dashboard" button), differing only in copy.
 */
@Composable
fun QuizEmptyQueueContent(
    message: String,
    onDone: () -> Unit,
    testTags: QuizEmptyQueueTestTags,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag(testTags.messageText)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onDone, modifier = Modifier.testTag(testTags.doneButton)) { Text("Back to dashboard") }
    }
}
