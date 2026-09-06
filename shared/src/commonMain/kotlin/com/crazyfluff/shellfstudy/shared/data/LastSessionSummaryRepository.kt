package com.crazyfluff.shellfstudy.shared.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.crazyfluff.shellfstudy.shared.data.model.SessionAnswerRow
import com.crazyfluff.shellfstudy.shared.data.model.SessionMissedItemRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class LastSessionKind { LESSON, REVIEW }

@Serializable
data class LastSessionSummary(
    val kind: LastSessionKind,
    val itemsCount: Int,
    val correctFirstTry: Int,
    val totalElapsedMs: Long,
    val averageTimePerItemMs: Long,
    val slowestAnswers: List<SessionAnswerRow>,
    val missedItems: List<SessionMissedItemRow>,
    val completedAtMillis: Long
)

/**
 * Persists the most recently completed lesson session's summary and the most recently completed
 * review session's summary — as two independent, kind-scoped DataStore keys — so the user can
 * revisit either later from the dashboard, after the live ViewModel's otherwise-ephemeral
 * session-complete state is gone (its screen popped off the back stack).
 *
 * Deliberately two separate stores rather than one shared slot keyed by [LastSessionKind]: a single
 * shared key meant a completed-but-unviewed lesson summary was silently destroyed the instant a
 * review session completed (or vice versa) — the one confirmed case of a review session actually
 * affecting lesson state. Splitting the storage key removes the possibility entirely, rather than
 * requiring every writer to remember to check `kind` before overwriting.
 */
class LastSessionSummaryRepository(
    dataStore: DataStore<Preferences>,
    json: Json
) {
    private val lessonStore = JsonPreferenceStore(
        dataStore, json, "last_session_summary_lesson", LastSessionSummary.serializer()
    )
    private val reviewStore = JsonPreferenceStore(
        dataStore, json, "last_session_summary_review", LastSessionSummary.serializer()
    )

    val lessonExists: Flow<Boolean> = lessonStore.exists
    val reviewExists: Flow<Boolean> = reviewStore.exists
    val exists: Flow<Boolean> = combine(lessonExists, reviewExists) { lesson, review -> lesson || review }

    /** Dispatches on [LastSessionSummary.kind] internally, so existing call sites that just save
     *  "the" summary don't need to know about the underlying split. */
    suspend fun save(summary: LastSessionSummary) = when (summary.kind) {
        LastSessionKind.LESSON -> lessonStore.save(summary)
        LastSessionKind.REVIEW -> reviewStore.save(summary)
    }

    suspend fun loadLesson(): LastSessionSummary? = lessonStore.load()
    suspend fun loadReview(): LastSessionSummary? = reviewStore.load()

    suspend fun clearLesson() = lessonStore.clear()
    suspend fun clearReview() = reviewStore.clear()
    suspend fun clearAll() {
        clearLesson()
        clearReview()
    }
}
