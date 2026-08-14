package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.fakes.FakeTokenCipher
import com.crazyfluff.shellfstudy.shared.data.TokenRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TokenRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createRepository(): TokenRepository {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        return TokenRepository(dataStore, FakeTokenCipher())
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
