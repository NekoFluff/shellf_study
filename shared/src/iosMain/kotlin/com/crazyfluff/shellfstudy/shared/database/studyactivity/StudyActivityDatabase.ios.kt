package com.crazyfluff.shellfstudy.shared.database.studyactivity

import androidx.room.Room
import androidx.room.RoomDatabase
import com.crazyfluff.shellfstudy.shared.database.iosDocumentDirectoryPath

fun getStudyActivityDatabaseBuilder(): RoomDatabase.Builder<StudyActivityDatabase> {
    val dbFilePath = "${iosDocumentDirectoryPath()}/$STUDY_ACTIVITY_DATABASE_FILE_NAME"
    return Room.databaseBuilder<StudyActivityDatabase>(name = dbFilePath)
}
