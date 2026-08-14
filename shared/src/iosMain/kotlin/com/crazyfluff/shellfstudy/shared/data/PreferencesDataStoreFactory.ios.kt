package com.crazyfluff.shellfstudy.shared.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.crazyfluff.shellfstudy.shared.database.iosDocumentDirectoryPath
import okio.Path.Companion.toPath

fun getPreferencesDataStore(): DataStore<Preferences> =
    createPreferencesDataStore { "${iosDocumentDirectoryPath()}/$PREFERENCES_FILE_NAME".toPath() }
