package com.crazyfluff.shellfstudy.shared.data.historysync

import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.LocalHistoryGuard
import com.crazyfluff.shellfstudy.shared.data.TokenRepository
import com.crazyfluff.shellfstudy.shared.data.isAuthError
import com.crazyfluff.shellfstudy.shared.data.safeApiCall
import com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDao
import com.crazyfluff.shellfstudy.shared.database.studytime.StudyTimeDao
import com.crazyfluff.shellfstudy.shared.network.historysync.HistoryRecordDto
import com.crazyfluff.shellfstudy.shared.network.historysync.HistorySyncApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.time.Clock

enum class HistorySyncOutcome { SUCCESS, SKIPPED, RETRY, AUTH_FAILURE }

/** What the settings screen needs from history sync — an interface so a screen test can fake it. */
interface HistorySyncSettings {
    /** False until the backend is deployed — see
     *  [com.crazyfluff.shellfstudy.shared.network.historysync.HISTORY_SYNC_BASE_URL]. */
    val isAvailable: Boolean
    val enabled: Flow<Boolean>
    suspend fun setEnabled(enabled: Boolean)

    /** Deletes this account's history and settings from the backend and turns sync off. Nothing on
     *  this device changes. */
    suspend fun deleteSyncedHistory(): ApiResult<Unit>
}

/**
 * Keeps this device's study history (study-time segments and study-streak days) and synced settings
 * (see [SettingsSync]) in step with the history-sync backend: uploads what the backend doesn't have
 * yet, then pulls what other devices added.
 *
 * Both directions are idempotent, which is what makes cancelling a pass anywhere safe. The backend
 * stores a record once however often it is pushed, so a pass cancelled between the push and marking
 * rows uploaded only re-sends them. Pulled rows are inserted keep-existing, so a pass cancelled
 * between inserting a page and saving the cursor only re-reads that page.
 *
 * History is only ever synced for the account [LocalHistoryGuard] says made it, and only with a
 * backend token issued for that same account. Otherwise a pass run between one account signing out
 * and the next one's first dashboard load could upload the previous person's history to the new one.
 */
class HistorySyncer(
    private val api: HistorySyncApi?,
    private val store: HistorySyncStore,
    private val tokenRepository: TokenRepository,
    private val localHistoryGuard: LocalHistoryGuard,
    private val studyTimeDao: StudyTimeDao,
    private val studyActivityDao: StudyActivityDao,
    private val settingsSync: SettingsSync,
    private val scheduler: HistorySyncScheduler,
    private val json: Json,
    private val clock: Clock = Clock.System
) : HistorySyncSettings {
    private val lock = Mutex()

    override val isAvailable: Boolean get() = api != null

    override val enabled: Flow<Boolean> = store.enabled

    override suspend fun setEnabled(enabled: Boolean) {
        store.setEnabled(enabled)
        if (enabled) scheduler.requestSync()
    }

    suspend fun sync(): HistorySyncOutcome = lock.withLock {
        val api = api ?: return@withLock HistorySyncOutcome.SKIPPED
        if (!store.isEnabled()) return@withLock HistorySyncOutcome.SKIPPED
        // Not matched to an account yet: the dashboard's first /user fetch does that.
        val owner = localHistoryGuard.owner() ?: return@withLock HistorySyncOutcome.SKIPPED

        when (val result = withSession(api, owner) { token -> pushAndPull(api, token) }) {
            is ApiResult.Success -> HistorySyncOutcome.SUCCESS
            is ApiResult.Error -> if (result.isAuthError) HistorySyncOutcome.AUTH_FAILURE else HistorySyncOutcome.RETRY
        }
    }

    override suspend fun deleteSyncedHistory(): ApiResult<Unit> = lock.withLock {
        val api = api ?: return@withLock ApiResult.Error("History sync isn't available.")
        val owner = localHistoryGuard.owner() ?: return@withLock ApiResult.Error("Not signed in.")

        val result = withSession(api, owner) { token -> safeApiCall { api.deleteAll(token) } }
        if (result is ApiResult.Success) {
            store.setEnabled(false)
            store.setCursor(0L)
            // So that turning sync back on uploads everything again, rather than only what's new.
            studyTimeDao.markAllNotUploaded()
            studyActivityDao.markAllNotUploaded()
            settingsSync.markAllPending()
        }
        when (result) {
            is ApiResult.Success -> result
            is ApiResult.Error -> result.copy(message = "Couldn't delete synced data. Try again later.")
        }
    }

    /**
     * Runs [block] with a backend token for [owner]. A token the backend rejects (expired early, or
     * its signing secret rotated) is swapped for a fresh one once; a second rejection is final.
     */
    private suspend fun <T> withSession(
        api: HistorySyncApi,
        owner: String,
        block: suspend (token: String) -> ApiResult<T>
    ): ApiResult<T> {
        val result = session(api, owner, reuseStored = true).then { block(it.token) }
        if (result !is ApiResult.Error || !result.isAuthError) return result
        store.clearSession()
        return session(api, owner, reuseStored = false).then { block(it.token) }
    }

    private suspend fun session(
        api: HistorySyncApi,
        owner: String,
        reuseStored: Boolean
    ): ApiResult<HistorySyncSession> {
        val stored = store.session()?.takeIf { reuseStored && it.isUsableFor(owner) }
        return if (stored != null) ApiResult.Success(stored) else exchange(api, owner)
    }

    private fun HistorySyncSession.isUsableFor(owner: String): Boolean =
        userId == owner && expiresAtMs - clock.now().toEpochMilliseconds() > SESSION_RENEW_MARGIN_MS

    /** Swaps the WaniKani token for a backend token, keeping it only if it was issued for [owner]. */
    private suspend fun exchange(api: HistorySyncApi, owner: String): ApiResult<HistorySyncSession> {
        val wkToken = tokenRepository.tokenFlow.firstOrNull()
            ?: return ApiResult.Error("Not signed in.", status = UNAUTHORIZED)
        return safeApiCall { api.auth(wkToken) }.then { response ->
            if (response.userId != owner) {
                // The WaniKani token belongs to someone other than whoever made this device's
                // history; the guard reconciles that on the next dashboard load. Until then, sync
                // nothing rather than mix the two.
                ApiResult.Error("Signed-in account doesn't own this device's history yet.")
            } else {
                val session = HistorySyncSession(response.token, response.userId, response.expiresAtMs)
                store.saveSession(session)
                ApiResult.Success(session)
            }
        }
    }

    private suspend fun pushAndPull(api: HistorySyncApi, token: String): ApiResult<Unit> =
        pushAll(
            notUploaded = { studyTimeDao.getNotUploaded(it) },
            toRecord = { it.toRecord(json) },
            markUploaded = { rows -> studyTimeDao.markUploaded(rows.map { it.syncId }) },
            push = { api.push(token, it) }
        ).then {
            pushAll(
                notUploaded = { studyActivityDao.getNotUploaded(it) },
                toRecord = { it.toRecord() },
                markUploaded = { rows -> studyActivityDao.markUploaded(rows.map { it.date }) },
                push = { api.push(token, it) }
            )
        }.then { pushSettings(api, token) }
            .then { pull(api, token) }
            // After the pull, so a device joining sync takes the account's settings rather than
            // overwriting them with ones it changed before sync existed.
            .then { if (settingsSync.adoptUntimedChanges()) pushSettings(api, token) else ApiResult.Success(Unit) }

    private suspend fun pushSettings(api: HistorySyncApi, token: String): ApiResult<Unit> {
        val records = settingsSync.pendingRecords()
        if (records.isEmpty()) return ApiResult.Success(Unit)
        return safeApiCall { api.push(token, records) }.then {
            settingsSync.markUploaded(records)
            ApiResult.Success(Unit)
        }
    }

    /** Uploads [notUploaded] rows a batch at a time, marking each batch once the backend has it. */
    private suspend fun <Row> pushAll(
        notUploaded: suspend (limit: Int) -> List<Row>,
        toRecord: (Row) -> HistoryRecordDto,
        markUploaded: suspend (List<Row>) -> Unit,
        push: suspend (List<HistoryRecordDto>) -> Unit
    ): ApiResult<Unit> {
        var result: ApiResult<Unit> = ApiResult.Success(Unit)
        var batch = notUploaded(PUSH_BATCH_SIZE)
        while (batch.isNotEmpty() && result is ApiResult.Success) {
            result = safeApiCall { push(batch.map(toRecord)) }
            if (result is ApiResult.Success) {
                markUploaded(batch)
                batch = if (batch.size < PUSH_BATCH_SIZE) emptyList() else notUploaded(PUSH_BATCH_SIZE)
            }
        }
        return result
    }

    private suspend fun pull(api: HistorySyncApi, token: String): ApiResult<Unit> {
        var cursor = store.cursor()
        while (true) {
            val page = when (val pulled = safeApiCall { api.pull(token, cursor, PULL_PAGE_SIZE) }) {
                is ApiResult.Success -> pulled.data
                is ApiResult.Error -> return pulled
            }
            val segments = page.records.mapNotNull { it.toSegmentEntity(json) }
            val days = page.records.mapNotNull { it.toActivityDayEntity() }
            if (segments.isNotEmpty()) studyTimeDao.insertFromRemote(segments)
            if (days.isNotEmpty()) studyActivityDao.insertFromRemote(days)
            settingsSync.applyRemote(page.records)
            // Saved per page, so a pass that fails part-way resumes from here rather than the start.
            cursor = page.cursor
            store.setCursor(cursor)
            if (!page.hasMore) return ApiResult.Success(Unit)
        }
    }

    companion object {
        /** Well under the backend's 500-record cap, and the SQLite bound-variable limit in markUploaded. */
        const val PUSH_BATCH_SIZE = 200
        const val PULL_PAGE_SIZE = 500

        /** Renew a stored token this long before it expires, so a pass never starts on one about to lapse. */
        private const val SESSION_RENEW_MARGIN_MS = 24 * 60 * 60 * 1000L
        private const val UNAUTHORIZED = 401
    }
}

/** [next] on success; the error as it is otherwise. */
private inline fun <T, R> ApiResult<T>.then(next: (T) -> ApiResult<R>): ApiResult<R> = when (this) {
    is ApiResult.Success -> next(data)
    is ApiResult.Error -> this
}
