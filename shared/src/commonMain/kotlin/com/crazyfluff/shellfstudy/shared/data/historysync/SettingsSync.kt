package com.crazyfluff.shellfstudy.shared.data.historysync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.crazyfluff.shellfstudy.shared.data.PENDING_SETTINGS_KEY
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.SyncedSetting
import com.crazyfluff.shellfstudy.shared.data.changedAtKey
import com.crazyfluff.shellfstudy.shared.data.markSettingChanged
import com.crazyfluff.shellfstudy.shared.network.historysync.HistoryRecordDto
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock

internal const val SETTING_RECORD = "setting"

/** Bumped only for an incompatible change. Readers skip versions they don't know. */
internal const val SETTING_PAYLOAD_VERSION = 1

/** One change to one synced setting. Its field names are a permanent contract — see HistoryRecords.kt. */
@Serializable
internal data class SyncedSettingChangeV1(
    val v: Int,
    val name: String,
    val value: JsonPrimitive,
    val changedAtMs: Long
)

/**
 * Settings sync, on the same backend and passes as study history. Every change to a synced setting is
 * uploaded as a record of its own, and the change with the latest [SyncedSettingChangeV1.changedAtMs]
 * wins on every device. That is the device clock, deliberately: a change made offline and uploaded
 * hours later should still lose to a newer one made meanwhile, which arrival order would get backwards.
 *
 * A change's record id is its setting's name plus its time, so uploading the same change twice
 * stores it once.
 */
class SettingsSync(
    private val dataStore: DataStore<Preferences>,
    settingsRepository: SettingsRepository,
    private val json: Json,
    private val clock: Clock = Clock.System
) {
    private val settings: List<SyncedSetting> = settingsRepository.syncedSettings
    private val byName = settings.associateBy { it.name }

    /** This device's changes that the backend doesn't have yet. */
    suspend fun pendingRecords(): List<HistoryRecordDto> {
        val prefs = dataStore.data.first()
        val pending = prefs[PENDING_SETTINGS_KEY] ?: return emptyList()
        return pending.mapNotNull { name ->
            val setting = byName[name] ?: return@mapNotNull null
            val value = setting.read(prefs) ?: return@mapNotNull null
            val changedAtMs = prefs[changedAtKey(setting)] ?: return@mapNotNull null
            HistoryRecordDto(
                type = SETTING_RECORD,
                recordId = "${name.replace('_', '-')}-$changedAtMs",
                payload = json.encodeToJsonElement(
                    SyncedSettingChangeV1(SETTING_PAYLOAD_VERSION, name, value, changedAtMs)
                ).jsonObject
            )
        }
    }

    /**
     * Clears the pending mark on what [records] uploaded — unless the setting changed again while
     * they were in flight, in which case the newer change stays pending.
     */
    suspend fun markUploaded(records: List<HistoryRecordDto>) {
        val uploaded = records.mapNotNull { it.toChange() }
        dataStore.edit { prefs ->
            val stillPending = (prefs[PENDING_SETTINGS_KEY] ?: emptySet()).filterNot { name ->
                val setting = byName[name]
                setting != null && uploaded.any { it.name == name && it.changedAtMs == prefs[changedAtKey(setting)] }
            }
            prefs[PENDING_SETTINGS_KEY] = stillPending.toSet()
        }
    }

    /** Applies other devices' changes that are newer than this device's own. */
    suspend fun applyRemote(records: List<HistoryRecordDto>) {
        val changes = records.mapNotNull { it.toChange() }
        if (changes.isEmpty()) return
        dataStore.edit { prefs ->
            for (change in changes) {
                val setting = byName[change.name]
                if (setting != null &&
                    change.isNewerThan(prefs[changedAtKey(setting)], setting.read(prefs)) &&
                    setting.write(prefs, change.value)
                ) {
                    prefs[changedAtKey(setting)] = change.changedAtMs
                    // A local change it supersedes no longer needs uploading.
                    prefs[PENDING_SETTINGS_KEY] = (prefs[PENDING_SETTINGS_KEY] ?: emptySet()) - change.name
                }
            }
        }
    }

    /**
     * Settings changed here before sync existed have a value but no change time. Run after a pull, so
     * a device joining sync adopts what the account already has; whatever is left is stamped now and
     * uploaded. Returns whether anything was stamped.
     */
    suspend fun adoptUntimedChanges(): Boolean = stampAll { prefs, setting -> prefs[changedAtKey(setting)] == null }

    /** Marks every changed setting for upload again — after the backend copy was deleted. */
    suspend fun markAllPending() {
        stampAll { prefs, setting -> prefs[changedAtKey(setting)] == null }
        dataStore.edit { prefs ->
            prefs[PENDING_SETTINGS_KEY] = settings.filter { prefs[changedAtKey(it)] != null }.map { it.name }.toSet()
        }
    }

    private suspend fun stampAll(needsStamp: (Preferences, SyncedSetting) -> Boolean): Boolean {
        var stamped = false
        val now = clock.now().toEpochMilliseconds()
        dataStore.edit { prefs ->
            settings.filter { it.read(prefs) != null && needsStamp(prefs, it) }.forEach {
                prefs.markSettingChanged(it, now)
                stamped = true
            }
        }
        return stamped
    }

    private fun HistoryRecordDto.toChange(): SyncedSettingChangeV1? =
        runCatching { json.decodeFromJsonElement<SyncedSettingChangeV1>(payload) }.getOrNull()
            ?.takeIf { type == SETTING_RECORD && it.v == SETTING_PAYLOAD_VERSION }

    /**
     * Later change time wins. Two devices changing the same setting in the same millisecond is a tie
     * broken by the value's text, so that every device settles on the same one.
     */
    private fun SyncedSettingChangeV1.isNewerThan(localChangedAtMs: Long?, localValue: JsonPrimitive?): Boolean =
        when {
            localChangedAtMs == null || changedAtMs > localChangedAtMs -> true
            changedAtMs < localChangedAtMs -> false
            else -> localValue != null && value.toString() > localValue.toString()
        }
}
