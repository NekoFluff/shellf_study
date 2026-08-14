package com.crazyfluff.shellfstudy.shared.database.pitchaccent

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun getPitchAccentDatabaseBuilder(context: Context): RoomDatabase.Builder<PitchAccentDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(PITCH_ACCENT_DATABASE_FILE_NAME)
    return Room.databaseBuilder(appContext, PitchAccentDatabase::class.java, dbFile.absolutePath)
}
