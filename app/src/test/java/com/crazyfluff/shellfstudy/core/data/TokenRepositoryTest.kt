package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.fakes.FakeTokenCipher
import com.crazyfluff.shellfstudy.shared.data.TokenCipher
import kotlinx.coroutines.flow.first
import com.crazyfluff.shellfstudy.shared.data.TokenRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TokenRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createRepository(cipher: TokenCipher = FakeTokenCipher()): TokenRepository {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        return TokenRepository(dataStore, cipher)
    }

    /** Counts decrypts, and fails them while [failing] is set. */
    private class CountingCipher : TokenCipher by FakeTokenCipher() {
        var decrypts = 0
        var failing = false
        private val delegate = FakeTokenCipher()
        override fun decrypt(encoded: String): String {
            decrypts++
            check(!failing) { "Keystore unavailable" }
            return delegate.decrypt(encoded)
        }
    }

    @Test
    fun `reading the token repeatedly decrypts it once`() = runTest {
        // Every HTTP request reads the token, and each decrypt is a Keystore round trip.
        val cipher = CountingCipher()
        val repository = createRepository(cipher)
        repository.saveToken("my-secret-token")

        repeat(5) { assertThat(repository.tokenFlow.first()).isEqualTo("my-secret-token") }

        assertThat(cipher.decrypts).isEqualTo(1)
    }

    @Test
    fun `a new token is decrypted rather than served from the previous one`() = runTest {
        val repository = createRepository()
        repository.saveToken("first")
        repository.tokenFlow.first()

        repository.saveToken("second")

        assertThat(repository.tokenFlow.first()).isEqualTo("second")
    }

    @Test
    fun `a failed decrypt is retried on the next read`() = runTest {
        val cipher = CountingCipher()
        val repository = createRepository(cipher)
        repository.saveToken("my-secret-token")

        cipher.failing = true
        assertThat(repository.tokenFlow.first()).isNull()
        cipher.failing = false

        assertThat(repository.tokenFlow.first()).isEqualTo("my-secret-token")
    }

    @Test
    fun `tokenFlow emits null when nothing stored`() = runTest {
        val repository = createRepository()
        repository.tokenFlow.test {
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `saveToken then tokenFlow emits the saved value`() = runTest {
        val repository = createRepository()
        repository.saveToken("my-secret-token")

        repository.tokenFlow.test {
            assertThat(awaitItem()).isEqualTo("my-secret-token")
        }
    }

    @Test
    fun `clearToken removes the stored value`() = runTest {
        val repository = createRepository()
        repository.saveToken("my-secret-token")
        repository.clearToken()

        repository.tokenFlow.test {
            assertThat(awaitItem()).isNull()
        }
    }
}
