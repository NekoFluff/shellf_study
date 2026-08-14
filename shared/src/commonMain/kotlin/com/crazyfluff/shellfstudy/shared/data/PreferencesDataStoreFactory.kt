package com.crazyfluff.shellfstudy.shared.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path

internal const val PREFERENCES_FILE_NAME = "shellf_study_prefs.preferences_pb"

fun createPreferencesDataStore(producePath: () -> Path): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = producePath)
