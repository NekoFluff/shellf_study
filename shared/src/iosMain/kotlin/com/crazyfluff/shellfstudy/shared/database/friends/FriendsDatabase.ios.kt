package com.crazyfluff.shellfstudy.shared.database.friends

import androidx.room.RoomDatabase
import com.crazyfluff.shellfstudy.shared.database.iosRoomDatabaseBuilder

fun getFriendsDatabaseBuilder(): RoomDatabase.Builder<FriendsDatabase> =
    iosRoomDatabaseBuilder(FRIENDS_DATABASE_FILE_NAME)
