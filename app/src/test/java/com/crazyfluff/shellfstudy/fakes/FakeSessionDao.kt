package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.shared.database.session.PersistedSessionEntity
import com.crazyfluff.shellfstudy.shared.database.session.SessionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory stand-in for [SessionDao].
 *
 * Mirrors the real DAO's tombstone semantics: clearing writes a row with a null payload rather than
 * removing the row, so `observeExists` has something to re-emit on. A fake that deleted the row
 * instead would let a bug pass here that the real DAO would surface as a session that never resumes.
 */
class FakeSessionDao : SessionDao {
    private val rows = MutableStateFlow<Map<String, String?>>(emptyMap())

    override suspend fun upsert(session: PersistedSessionEntity) {
        rows.value = rows.value + (session.sessionKey to session.payload)
    }

    override suspend fun getPayload(sessionKey: String): String? = rows.value[sessionKey]

    override fun observeExists(sessionKey: String): Flow<Boolean> =
        rows.map { it[sessionKey] != null }

    override suspend fun clearAll() {
        rows.value = emptyMap()
    }
}
