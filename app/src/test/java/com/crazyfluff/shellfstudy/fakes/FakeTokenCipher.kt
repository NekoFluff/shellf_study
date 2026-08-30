package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.shared.data.TokenCipher

/**
 * No-op stand-in for [TokenCipher] — real AES/Keystore crypto isn't available on the JVM test runner.
 *
 * @param failingTokens plain tokens that should simulate a decrypt failure (e.g. a Keystore entry
 * invalidated after a device unlock change) instead of round-tripping normally.
 */
class FakeTokenCipher(private val failingTokens: Set<String> = emptySet()) : TokenCipher {
    override fun encrypt(plainText: String): String = "enc:$plainText"
    override fun decrypt(encoded: String): String {
        val token = encoded.removePrefix("enc:")
        check(token !in failingTokens) { "Simulated decrypt failure for $token" }
        return token
    }
}
