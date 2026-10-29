package com.crazyfluff.shellfstudy.shared.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDao
import com.crazyfluff.shellfstudy.shared.database.studytime.StudyTimeDao
import kotlinx.coroutines.flow.first

/**
 * Keeps the history WaniKani can't give back (study days for the streak, and study time) tied to the
 * account that made it, instead of wiping it at logout.
 *
 * Logging out used to delete it along with the other account data. That included the automatic
 * logout when WaniKani rejects the token, which happens whenever the learner regenerates their API
 * key, so a routine key rotation erased months of history. Now the data survives logout. It is only
 * cleared when a *different* account signs in, so one person's history still never shows up under
 * another's.
 *
 * An install that predates this has no owner recorded; the first account seen adopts the existing
 * history rather than wiping it, since it almost certainly made it.
 */
class LocalHistoryGuard(
    private val dataStore: DataStore<Preferences>,
    private val studyActivityDao: StudyActivityDao,
    private val studyTimeDao: StudyTimeDao,
    /** Runs when another account's history is cleared — how history sync forgets that account's
     *  cursor, token and opt-in. */
    private val onOwnerChanged: suspend () -> Unit = {}
) {
    private val ownerKey = stringPreferencesKey("local_history_owner_user_id")

    /** The WaniKani user id the local history belongs to, or null before any account was seen. */
    suspend fun owner(): String? = dataStore.data.first()[ownerKey]

    /** Records [userId] as the owner, clearing the history first if another account made it. */
    suspend fun claimFor(userId: String) {
        val owner = dataStore.data.first()[ownerKey]
        if (owner == userId) return
        if (owner != null) {
            studyActivityDao.clearAll()
            studyTimeDao.clearAll()
            onOwnerChanged()
        }
        dataStore.edit { it[ownerKey] = userId }
    }
}
