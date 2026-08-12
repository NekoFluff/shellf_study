package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Generic DataStore-backed JSON persistence for a single value under one preferences key —
 * factors out the save/load/clear pattern shared by [ReviewSessionRepository] and
 * [LessonSessionRepository]. Takes an explicit [KSerializer] rather than being an inline reified
 * function since callers hold this as a stored field of their own generic type.
 */
class JsonPreferenceStore<T>(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
    keyName: String,
    private val serializer: KSerializer<T>
) {
    private val key = stringPreferencesKey(keyName)

    val exists: Flow<Boolean> = dataStore.data.map { it[key] != null }

    suspend fun save(value: T) {
        dataStore.edit { prefs -> prefs[key] = json.encodeToString(serializer, value) }
    }

    suspend fun load(): T? {
        val raw = dataStore.data.first()[key] ?: return null
        return runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
    }

    suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(key) }
    }
}
