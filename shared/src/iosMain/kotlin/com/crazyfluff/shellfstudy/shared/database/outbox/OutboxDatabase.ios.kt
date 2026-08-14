package com.crazyfluff.shellfstudy.shared.database.outbox

import androidx.room.Room
import androidx.room.RoomDatabase
import com.crazyfluff.shellfstudy.shared.database.iosDocumentDirectoryPath

fun getOutboxDatabaseBuilder(): RoomDatabase.Builder<OutboxDatabase> {
    val dbFilePath = "${iosDocumentDirectoryPath()}/$OUTBOX_DATABASE_FILE_NAME"
    return Room.databaseBuilder<OutboxDatabase>(name = dbFilePath)
}
