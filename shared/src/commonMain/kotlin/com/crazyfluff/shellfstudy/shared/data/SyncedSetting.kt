package com.crazyfluff.shellfstudy.shared.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * One setting that follows the learner across devices (see [SettingsRepository.syncedSettings]).
 * Its sync identity is its DataStore key's name, which is already a persisted, never-renamed string.
 *
 * [write] validates what it is given, because the value may come from another device running another
 * build: an out-of-range number is clamped, and an enum constant this build doesn't know is refused,
 * leaving the local value as it was.
 */
sealed class SyncedSetting(val key: Preferences.Key<*>) {
    val name: String get() = key.name

    abstract fun read(prefs: Preferences): JsonPrimitive?

    /** Stores [value], or returns false if this build can't accept it. */
    abstract fun write(prefs: MutablePreferences, value: JsonPrimitive): Boolean

    class IntSetting(private val intKey: Preferences.Key<Int>, private val range: IntRange) : SyncedSetting(intKey) {
        override fun read(prefs: Preferences) = prefs[intKey]?.let(::JsonPrimitive)

        override fun write(prefs: MutablePreferences, value: JsonPrimitive): Boolean {
            val number = value.intOrNull ?: return false
            prefs[intKey] = number.coerceIn(range)
            return true
        }
    }

    class BooleanSetting(private val booleanKey: Preferences.Key<Boolean>) : SyncedSetting(booleanKey) {
        override fun read(prefs: Preferences) = prefs[booleanKey]?.let(::JsonPrimitive)

        override fun write(prefs: MutablePreferences, value: JsonPrimitive): Boolean {
            prefs[booleanKey] = value.booleanOrNull ?: return false
            return true
        }
    }

    class EnumSetting(private val stringKey: Preferences.Key<String>, private val names: Set<String>) :
        SyncedSetting(stringKey) {
        override fun read(prefs: Preferences) = prefs[stringKey]?.let(::JsonPrimitive)

        override fun write(prefs: MutablePreferences, value: JsonPrimitive): Boolean {
            val name = value.content.takeIf { value.isString && it in names } ?: return false
            prefs[stringKey] = name
            return true
        }
    }
}

/** When this device last changed [setting] (or adopted a change to it from another device). */
internal fun changedAtKey(setting: SyncedSetting): Preferences.Key<Long> =
    longPreferencesKey("settings_sync_changed_at_${setting.name}")

/** Names of synced settings changed here since they were last uploaded. */
internal val PENDING_SETTINGS_KEY: Preferences.Key<Set<String>> = stringSetPreferencesKey("settings_sync_pending")

/** Records a local change to [setting], to be uploaded on the next sync pass. */
internal fun MutablePreferences.markSettingChanged(setting: SyncedSetting, changedAtMs: Long) {
    this[changedAtKey(setting)] = changedAtMs
    this[PENDING_SETTINGS_KEY] = (this[PENDING_SETTINGS_KEY] ?: emptySet()) + setting.name
}
