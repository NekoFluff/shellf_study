package com.crazyfluff.shellfstudy.shared.database.session

import android.content.Context
import androidx.room.RoomDatabase
import com.crazyfluff.shellfstudy.shared.database.androidRoomDatabaseBuilder

fun getSessionDatabaseBuilder(context: Context): RoomDatabase.Builder<SessionDatabase> =
    androidRoomDatabaseBuilder(context, SESSION_DATABASE_FILE_NAME, SessionDatabase::class.java)
