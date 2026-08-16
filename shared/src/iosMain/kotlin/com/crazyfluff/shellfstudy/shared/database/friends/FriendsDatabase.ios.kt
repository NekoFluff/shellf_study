package com.crazyfluff.shellfstudy.shared.database.friends

import androidx.room.Room
import androidx.room.RoomDatabase
import com.crazyfluff.shellfstudy.shared.database.iosDocumentDirectoryPath

fun getFriendsDatabaseBuilder(): RoomDatabase.Builder<FriendsDatabase> {
    val dbFilePath = "${iosDocumentDirectoryPath()}/$FRIENDS_DATABASE_FILE_NAME"
    return Room.databaseBuilder<FriendsDatabase>(name = dbFilePath)
}
