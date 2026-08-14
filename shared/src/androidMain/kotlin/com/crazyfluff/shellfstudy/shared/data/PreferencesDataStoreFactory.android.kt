package com.crazyfluff.shellfstudy.shared.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toOkioPath

/**
 * Matches the path `androidx.datastore.preferences.preferencesDataStore(name = ...)` would have
 * used (a `datastore/` subdirectory under files, not files/ directly) — existing installs already
 * have their preferences file there, and a different path would silently look like a fresh install.
 */
fun getPreferencesDataStore(context: Context): DataStore<Preferences> =
    createPreferencesDataStore {
        context.applicationContext.filesDir.resolve("datastore").resolve(PREFERENCES_FILE_NAME).toOkioPath()
    }
