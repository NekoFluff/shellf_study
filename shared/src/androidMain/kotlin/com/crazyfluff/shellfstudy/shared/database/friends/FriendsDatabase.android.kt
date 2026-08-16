package com.crazyfluff.shellfstudy.shared.database.friends

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun getFriendsDatabaseBuilder(context: Context): RoomDatabase.Builder<FriendsDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(FRIENDS_DATABASE_FILE_NAME)
    return Room.databaseBuilder(appContext, FriendsDatabase::class.java, dbFile.absolutePath)
}
