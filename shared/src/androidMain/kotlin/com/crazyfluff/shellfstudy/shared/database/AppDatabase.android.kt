package com.crazyfluff.shellfstudy.shared.database

import android.content.Context
import androidx.room.RoomDatabase

fun getAppDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> =
    androidRoomDatabaseBuilder(context, APP_DATABASE_FILE_NAME, AppDatabase::class.java)
