package com.crazyfluff.shellfstudy.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.crazyfluff.shellfstudy.shared.data.AndroidKeystoreTokenCipher
import com.crazyfluff.shellfstudy.shared.data.TokenCipher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "shellf_study_prefs")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore
}

@Module
@InstallIn(SingletonComponent::class)
object TokenCipherModule {
    // @Provides, not @Binds — AndroidKeystoreTokenCipher lives in :shared, so it has no
    // javax.inject annotations for Hilt to see (that library isn't available on Kotlin/Native,
    // and Hilt's codegen doesn't run over :shared's own compilation anyway).
    @Provides
    @Singleton
    fun provideTokenCipher(): TokenCipher = AndroidKeystoreTokenCipher()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PitchAccentBundledSourceModule {
    @Binds
    @Singleton
    abstract fun bindPitchAccentBundledSource(impl: AndroidPitchAccentBundledSource): PitchAccentBundledSource
}
