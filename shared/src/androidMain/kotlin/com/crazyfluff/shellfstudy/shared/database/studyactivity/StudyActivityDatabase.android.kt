package com.crazyfluff.shellfstudy.shared.database.studyactivity

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun getStudyActivityDatabaseBuilder(context: Context): RoomDatabase.Builder<StudyActivityDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(STUDY_ACTIVITY_DATABASE_FILE_NAME)
    return Room.databaseBuilder(appContext, StudyActivityDatabase::class.java, dbFile.absolutePath)
}
