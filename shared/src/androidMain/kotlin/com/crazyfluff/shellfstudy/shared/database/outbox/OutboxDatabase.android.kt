package com.crazyfluff.shellfstudy.shared.database.outbox

import android.content.Context
import androidx.room.RoomDatabase
import com.crazyfluff.shellfstudy.shared.database.androidRoomDatabaseBuilder

fun getOutboxDatabaseBuilder(context: Context): RoomDatabase.Builder<OutboxDatabase> =
    androidRoomDatabaseBuilder(context, OUTBOX_DATABASE_FILE_NAME, OutboxDatabase::class.java)
