package com.crazyfluff.shellfstudy.shared.data.audio

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import com.crazyfluff.shellfstudy.shared.data.model.WaniKaniUser
import com.crazyfluff.shellfstudy.shared.data.model.toPronunciationAudios
import com.crazyfluff.shellfstudy.shared.database.SubjectAudioRow
import com.crazyfluff.shellfstudy.shared.database.SubjectDao
import com.crazyfluff.shellfstudy.shared.network.MAX_WANIKANI_LEVEL
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Which voices a level download fetches. */
enum class OfflineAudioVoices { ALL, FEMALE, MALE }

data class OfflineAudioPreferences(
    /** Keep the level the user is on downloaded, adding each new level as they reach it. */
    val autoDownloadCurrentLevel: Boolean = true,
    val wifiOnly: Boolean = false,
    val voices: OfflineAudioVoices = OfflineAudioVoices.ALL,
    /** Every level the user has asked to keep, whether by hand or through [autoDownloadCurrentLevel]. */
    val wantedLevels: Set<Int> = emptySet(),
    /** Null until the first `/user` fetch has told us. */
    val currentLevel: Int? = null,
    val maxLevelGranted: Int = MAX_WANIKANI_LEVEL
)

sealed interface LevelAudioStatus {
    /** Above what the account's subscription unlocks. */
    data object Locked : LevelAudioStatus

    /** Nothing stored and not asked for. [clips] is how many a download would fetch. */
    data class NotDownloaded(val clips: Int) : LevelAudioStatus

    /** Asked for, waiting its turn (or for a connection). */
    data class Queued(val stored: Int, val clips: Int) : LevelAudioStatus

    data class Downloading(val done: Int, val total: Int) : LevelAudioStatus

    /** Some clips stored — from playing them, or a download that hasn't finished — but not all. */
    data class Partial(val stored: Int, val clips: Int, val bytes: Long) : LevelAudioStatus

    data class Downloaded(val clips: Int, val bytes: Long) : LevelAudioStatus
}

data class LevelAudioRow(val level: Int, val status: LevelAudioStatus)

data class OfflineAudioState(
    val preferences: OfflineAudioPreferences = OfflineAudioPreferences(),
    val levels: List<LevelAudioRow> = emptyList(),
    val totalClips: Int = 0,
    val totalBytes: Long = 0,
    val downloadedLevels: Int = 0
)

private data class ActiveDownload(val level: Int, val done: Int, val total: Int)

private val AUTO_DOWNLOAD_CURRENT_LEVEL_KEY = booleanPreferencesKey("offline_audio_auto_current_level")
private val WIFI_ONLY_KEY = booleanPreferencesKey("offline_audio_wifi_only")
private val VOICES_KEY = stringPreferencesKey("offline_audio_voices")
private val WANTED_LEVELS_KEY = stringSetPreferencesKey("offline_audio_wanted_levels")
private val CURRENT_LEVEL_KEY = intPreferencesKey("offline_audio_current_level")
private val MAX_LEVEL_GRANTED_KEY = intPreferencesKey("offline_audio_max_level_granted")

private const val MP3_CONTENT_TYPE = "audio/mpeg"

/**
 * The offline audio library's policy: which levels to keep, which voices, and when to fetch them.
 * [AudioLibrary] does the storing; this decides what goes in it.
 *
 * Downloads run through [scheduler], which on Android is a WorkManager job (so a download survives the
 * app being backgrounded and waits for the right kind of network) and on iOS a coroutine restarted when
 * the connection returns. Either way the job just calls [runDownloads], which works through every
 * wanted level still missing clips; anything already stored is skipped, so running it again after an
 * interruption picks up where it stopped.
 */
class OfflineAudioManager(
    private val dataStore: DataStore<Preferences>,
    private val library: AudioLibrary,
    private val subjectDao: SubjectDao,
    private val scheduler: AudioDownloadScheduler,
    /** Puts a level's radical images in the image cache — the other thing an offline lesson at that
     *  level needs from the network. Best-effort: a failure here never fails the audio download. */
    private val prefetchImages: suspend (urls: List<String>) -> Unit = {}
) {
    val preferences: Flow<OfflineAudioPreferences> = dataStore.data.map { prefs ->
        OfflineAudioPreferences(
            autoDownloadCurrentLevel = prefs[AUTO_DOWNLOAD_CURRENT_LEVEL_KEY] ?: true,
            wifiOnly = prefs[WIFI_ONLY_KEY] ?: false,
            voices = prefs[VOICES_KEY]?.let { raw -> runCatching { OfflineAudioVoices.valueOf(raw) }.getOrNull() }
                ?: OfflineAudioVoices.ALL,
            wantedLevels = prefs[WANTED_LEVELS_KEY].orEmpty().mapNotNull(String::toIntOrNull).toSet(),
            currentLevel = prefs[CURRENT_LEVEL_KEY],
            maxLevelGranted = prefs[MAX_LEVEL_GRANTED_KEY] ?: MAX_WANIKANI_LEVEL
        )
    }.distinctUntilChanged()

    /** Every vocabulary subject's level and clips. Loaded on first use and after each sync — the
     *  clips themselves change only when WaniKani adds or re-records some. */
    private val catalog = MutableStateFlow<List<SubjectAudioRow>?>(null)
    private val active = MutableStateFlow<ActiveDownload?>(null)
    private val runLock = Mutex()

    /** How many clips a complete download of each level holds, under the current voice choice. Its own
     *  flow so it is worked out when the catalog or the voices change, not on every progress tick. */
    private val clipsPerLevel: Flow<Map<Int, Int>?> =
        combine(catalog, preferences.map { it.voices }.distinctUntilChanged()) { rows, voices ->
            rows?.groupBy { it.level }
                ?.mapValues { (_, levelRows) -> levelRows.sumOf { libraryClips(it, voices).size } }
        }

    val state: Flow<OfflineAudioState> =
        combine(preferences, library.levels, clipsPerLevel, active) { prefs, stored, clipsPerLevel, download ->
            if (clipsPerLevel == null) return@combine OfflineAudioState(preferences = prefs)
            val levels = (1..MAX_WANIKANI_LEVEL).map { level ->
                LevelAudioRow(level, levelStatus(level, prefs, clipsPerLevel[level] ?: 0, stored[level], download))
            }
            OfflineAudioState(
                preferences = prefs,
                levels = levels,
                totalClips = stored.values.sumOf { it.clips },
                totalBytes = stored.values.sumOf { it.bytes },
                downloadedLevels = levels.count { it.status is LevelAudioStatus.Downloaded }
            )
        }

    /** Loads what's needed for [state] to describe every level. */
    suspend fun prepare() {
        library.load()
        if (catalog.value == null) refreshCatalog()
    }

    /** Changes whichever settings are given. Switching the current-level download on fetches the
     *  current level at once; any change restarts waiting downloads under the new terms. */
    suspend fun updateSettings(
        autoDownloadCurrentLevel: Boolean? = null,
        wifiOnly: Boolean? = null,
        voices: OfflineAudioVoices? = null
    ) {
        dataStore.edit { prefs ->
            autoDownloadCurrentLevel?.let { prefs[AUTO_DOWNLOAD_CURRENT_LEVEL_KEY] = it }
            wifiOnly?.let { prefs[WIFI_ONLY_KEY] = it }
            voices?.let { prefs[VOICES_KEY] = it.name }
        }
        val currentLevel = preferences.first().currentLevel
        if (autoDownloadCurrentLevel == true && currentLevel != null) download(listOf(currentLevel)) else resume()
    }

    /** Adds [levels] to the library and starts fetching them. */
    suspend fun download(levels: Collection<Int>) {
        val granted = preferences.first().maxLevelGranted
        val allowed = levels.filter { it in 1..granted }
        if (allowed.isEmpty()) return
        dataStore.edit { prefs ->
            prefs[WANTED_LEVELS_KEY] = prefs[WANTED_LEVELS_KEY].orEmpty() + allowed.map(Int::toString)
        }
        resume()
    }

    suspend fun deleteLevel(level: Int) {
        dataStore.edit { prefs -> prefs[WANTED_LEVELS_KEY] = prefs[WANTED_LEVELS_KEY].orEmpty() - level.toString() }
        library.deleteLevel(level)
    }

    suspend fun deleteAll() {
        scheduler.cancel()
        dataStore.edit { it.remove(WANTED_LEVELS_KEY) }
        library.deleteAll()
    }

    /** Called after every successful `/user` fetch: notices a new level (and so a new level to keep,
     *  when [OfflineAudioPreferences.autoDownloadCurrentLevel] is on), and gives any download that
     *  stopped for lack of a network another go now that one has evidently worked. */
    suspend fun onUserRefreshed(user: WaniKaniUser) {
        dataStore.edit { prefs ->
            prefs[CURRENT_LEVEL_KEY] = user.level
            prefs[MAX_LEVEL_GRANTED_KEY] = user.maxLevelGranted
            if ((prefs[AUTO_DOWNLOAD_CURRENT_LEVEL_KEY] ?: true) && user.level <= user.maxLevelGranted) {
                prefs[WANTED_LEVELS_KEY] = prefs[WANTED_LEVELS_KEY].orEmpty() + user.level.toString()
            }
        }
        refreshCatalog()
        resume()
    }

    /** Asks the platform to run [runDownloads] if any wanted level is missing clips. */
    suspend fun resume() {
        val prefs = preferences.first()
        if (prefs.wantedLevels.isEmpty()) return
        scheduler.schedule(wifiOnly = prefs.wifiOnly)
    }

    /**
     * Fetches every clip the wanted levels are missing, lowest level first. The platform scheduler's
     * job body — never called from UI code. Serialised, so a second run waits for the first rather
     * than downloading the same clips alongside it.
     *
     * Wanted levels are re-read after each one, so a level added while this runs is picked up by this
     * run (the schedulers rely on that rather than restarting it), and one deleted meanwhile is skipped.
     */
    suspend fun runDownloads(): AudioDownloadOutcome = runLock.withLock {
        prepare()
        try {
            val attempted = mutableSetOf<Int>()
            while (true) {
                val prefs = preferences.first()
                val level = (prefs.wantedLevels - attempted).minOrNull() ?: break
                attempted += level
                val clips = catalog.value.orEmpty()
                    .filter { it.level == level }
                    .flatMap { libraryClips(it, prefs.voices) }
                val outcome = library.download(clips) { done, total ->
                    active.value = ActiveDownload(level, done, total)
                }
                if (outcome == AudioDownloadOutcome.NETWORK_FAILURE) return@withLock outcome
                try {
                    prefetchImages(subjectDao.getCharacterImageUrlsAtLevel(level))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Best-effort, see prefetchImages.
                }
            }
            AudioDownloadOutcome.COMPLETE
        } finally {
            active.value = null
        }
    }

    /** Signing out removes everything: the clips belong to the account's levels and settings. */
    suspend fun clearForLogout() {
        deleteAll()
        dataStore.edit { prefs ->
            prefs.remove(CURRENT_LEVEL_KEY)
            prefs.remove(MAX_LEVEL_GRANTED_KEY)
        }
    }

    private suspend fun refreshCatalog() {
        catalog.update { subjectDao.getAudioRows() }
    }
}

private fun levelStatus(
    level: Int,
    prefs: OfflineAudioPreferences,
    clips: Int,
    stored: LevelStorage?,
    download: ActiveDownload?
): LevelAudioStatus {
    val storedClips = stored?.clips ?: 0
    val storedBytes = stored?.bytes ?: 0
    return when {
        level > prefs.maxLevelGranted -> LevelAudioStatus.Locked
        download != null && download.level == level -> LevelAudioStatus.Downloading(download.done, download.total)
        storedClips > 0 && storedClips >= clips -> LevelAudioStatus.Downloaded(storedClips, storedBytes)
        level in prefs.wantedLevels -> LevelAudioStatus.Queued(storedClips, clips)
        storedClips > 0 -> LevelAudioStatus.Partial(storedClips, clips, storedBytes)
        else -> LevelAudioStatus.NotDownloaded(clips)
    }
}

/**
 * The clips the library keeps for one subject: one per voice per reading, in mp3 where the voice has
 * one (WaniKani sends each in several formats, and mp3 plays everywhere), limited to [voices].
 *
 * A subject recorded in none of the chosen voices keeps all of its voices instead, so choosing one
 * never leaves a word with no audio at all.
 */
internal fun libraryClips(row: SubjectAudioRow, voices: OfflineAudioVoices): List<PronunciationAudio> {
    val audios = row.pronunciationAudios.toPronunciationAudios(row.level)
    val perVoice = audios.groupBy { it.pronunciation to (it.voiceActorId ?: it.gender ?: it.url) }
        .values.map { clips -> clips.firstOrNull { it.contentType == MP3_CONTENT_TYPE } ?: clips.first() }
    val gender = when (voices) {
        OfflineAudioVoices.ALL -> return perVoice
        OfflineAudioVoices.FEMALE -> "female"
        OfflineAudioVoices.MALE -> "male"
    }
    return perVoice.filter { it.gender == gender }.ifEmpty { perVoice }
}
