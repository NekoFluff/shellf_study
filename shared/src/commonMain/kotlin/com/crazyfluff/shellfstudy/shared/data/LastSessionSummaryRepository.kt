package com.crazyfluff.shellfstudy.shared.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.crazyfluff.shellfstudy.shared.data.model.SessionAnswerRow
import com.crazyfluff.shellfstudy.shared.data.model.SessionMissedItemRow
import kotlinx.coroutines.flow.Flow
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
 * Persists the most recently completed lesson or review session's summary so the user can revisit
 * it later from the dashboard — the live ViewModel's session-complete state is otherwise ephemeral
 * and gone the moment its screen is popped off the back stack.
 */
class LastSessionSummaryRepository(
    dataStore: DataStore<Preferences>,
    json: Json
) {
    private val store = JsonPreferenceStore(
        dataStore, json, "last_session_summary", LastSessionSummary.serializer()
    )

    val exists: Flow<Boolean> = store.exists

    suspend fun save(summary: LastSessionSummary) = store.save(summary)

    suspend fun load(): LastSessionSummary? = store.load()

    suspend fun clear() = store.clear()
}
