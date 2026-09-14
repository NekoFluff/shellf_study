package com.crazyfluff.shellfstudy.shared.database

import androidx.room.RoomDatabase

fun getAppDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> =
    iosRoomDatabaseBuilder(APP_DATABASE_FILE_NAME)
