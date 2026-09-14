package com.crazyfluff.shellfstudy.shared.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Builds a [RoomDatabase.Builder] rooted at the app's private database directory for [fileName] —
 * the boilerplate shared by every Android database builder (AppDatabase, OutboxDatabase,
 * FriendsDatabase, StudyActivityDatabase), which otherwise differed only in the file name and the
 * database class.
 */
internal fun <T : RoomDatabase> androidRoomDatabaseBuilder(
    context: Context,
    fileName: String,
    klass: Class<T>
): RoomDatabase.Builder<T> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(fileName)
    return Room.databaseBuilder(appContext, klass, dbFile.absolutePath)
}
