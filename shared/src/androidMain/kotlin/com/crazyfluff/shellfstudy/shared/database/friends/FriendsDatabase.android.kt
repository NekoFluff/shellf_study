package com.crazyfluff.shellfstudy.shared.database.friends

import android.content.Context
import androidx.room.RoomDatabase
import com.crazyfluff.shellfstudy.shared.database.androidRoomDatabaseBuilder

fun getFriendsDatabaseBuilder(context: Context): RoomDatabase.Builder<FriendsDatabase> =
    androidRoomDatabaseBuilder(context, FRIENDS_DATABASE_FILE_NAME, FriendsDatabase::class.java)
