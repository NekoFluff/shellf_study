package com.crazyfluff.shellfstudy.shared.database.outbox

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun getOutboxDatabaseBuilder(context: Context): RoomDatabase.Builder<OutboxDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(OUTBOX_DATABASE_FILE_NAME)
    return Room.databaseBuilder(appContext, OutboxDatabase::class.java, dbFile.absolutePath)
}
