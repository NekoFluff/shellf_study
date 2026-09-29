package com.crazyfluff.shellfstudy.shared.database.session

import androidx.room.RoomDatabase
import com.crazyfluff.shellfstudy.shared.database.iosRoomDatabaseBuilder

fun getSessionDatabaseBuilder(): RoomDatabase.Builder<SessionDatabase> =
    iosRoomDatabaseBuilder(SESSION_DATABASE_FILE_NAME)
