package com.crazyfluff.shellfstudy.shared.database

import androidx.room.Room
import androidx.room.RoomDatabase

fun getAppDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFilePath = "${iosDocumentDirectoryPath()}/$APP_DATABASE_FILE_NAME"
    return Room.databaseBuilder<AppDatabase>(name = dbFilePath)
}
