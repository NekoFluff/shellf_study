package com.crazyfluff.shellfstudy.shared.database.pitchaccent

import androidx.room.Room
import androidx.room.RoomDatabase
import com.crazyfluff.shellfstudy.shared.database.iosDocumentDirectoryPath

fun getPitchAccentDatabaseBuilder(): RoomDatabase.Builder<PitchAccentDatabase> {
    val dbFilePath = "${iosDocumentDirectoryPath()}/$PITCH_ACCENT_DATABASE_FILE_NAME"
    return Room.databaseBuilder<PitchAccentDatabase>(name = dbFilePath)
}
