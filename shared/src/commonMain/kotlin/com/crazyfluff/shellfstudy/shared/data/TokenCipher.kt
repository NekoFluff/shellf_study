package com.crazyfluff.shellfstudy.shared.data

/** Encrypts/decrypts short secrets such as the WaniKani API token before they hit disk. */
interface TokenCipher {
    fun encrypt(plainText: String): String
    fun decrypt(encoded: String): String

    /** Clears any secure-storage state backing [encrypt]/[decrypt] beyond what the caller already
     *  stores itself (e.g. iOS's Keychain entry). Android's Keystore-backed cipher doesn't need
     *  this: its ciphertext lives entirely in the caller's own storage (DataStore), so deleting
     *  that preference key alone is already enough — the Keystore key itself can be left in place. */
    fun clear() {}
}
