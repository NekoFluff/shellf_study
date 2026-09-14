package com.crazyfluff.shellfstudy.shared.database

import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Builds a [RoomDatabase.Builder] rooted in the app's Documents directory for [fileName] — the
 * boilerplate shared by every iOS database builder (AppDatabase, OutboxDatabase, FriendsDatabase,
 * StudyActivityDatabase), which otherwise differed only in the file name and the database type.
 */
internal inline fun <reified T : RoomDatabase> iosRoomDatabaseBuilder(fileName: String): RoomDatabase.Builder<T> {
    val dbFilePath = "${iosDocumentDirectoryPath()}/$fileName"
    return Room.databaseBuilder(name = dbFilePath)
}
