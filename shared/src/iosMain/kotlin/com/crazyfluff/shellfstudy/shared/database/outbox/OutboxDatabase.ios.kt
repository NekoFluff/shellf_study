package com.crazyfluff.shellfstudy.shared.database.outbox

import androidx.room.RoomDatabase
import com.crazyfluff.shellfstudy.shared.database.iosRoomDatabaseBuilder

fun getOutboxDatabaseBuilder(): RoomDatabase.Builder<OutboxDatabase> =
    iosRoomDatabaseBuilder(OUTBOX_DATABASE_FILE_NAME)
