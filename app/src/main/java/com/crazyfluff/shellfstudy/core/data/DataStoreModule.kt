package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.crazyfluff.shellfstudy.shared.data.AndroidKeystoreTokenCipher
import com.crazyfluff.shellfstudy.shared.data.CmpPitchAccentBundledSource
import com.crazyfluff.shellfstudy.shared.data.PitchAccentBundledSource
import com.crazyfluff.shellfstudy.shared.data.TokenCipher
import com.crazyfluff.shellfstudy.shared.data.getPreferencesDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

val dataStoreModule = module {
    single<DataStore<Preferences>> { getPreferencesDataStore(androidContext()) }

    // AndroidKeystoreTokenCipher lives in :shared as a plain class (no DI annotations there —
    // that library isn't available on Kotlin/Native), hence the explicit registration here.
    single<TokenCipher> { AndroidKeystoreTokenCipher() }

    single { CmpPitchAccentBundledSource() } bind PitchAccentBundledSource::class
}
