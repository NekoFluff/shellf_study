package com.crazyfluff.shellfstudy.shared.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun getAppDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(APP_DATABASE_FILE_NAME)
    return Room.databaseBuilder(appContext, AppDatabase::class.java, dbFile.absolutePath)
}
