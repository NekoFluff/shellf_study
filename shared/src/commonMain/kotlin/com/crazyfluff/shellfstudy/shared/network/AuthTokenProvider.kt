package com.crazyfluff.shellfstudy.shared.network

/**
 * Supplies the current WaniKani API token to attach as a Bearer credential, or null if none is
 * stored. Kept as a plain suspend callback — rather than depending on TokenRepository directly —
 * so this network layer has no dependency on token storage/DataStore, which still lives in the
 * Android-only :app module until that's migrated too.
 */
fun interface AuthTokenProvider {
    suspend fun currentToken(): String?
}
