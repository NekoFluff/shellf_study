package com.crazyfluff.shellfstudy.shared.database.studyactivity

import android.content.Context
import androidx.room.RoomDatabase
import com.crazyfluff.shellfstudy.shared.database.androidRoomDatabaseBuilder

fun getStudyActivityDatabaseBuilder(context: Context): RoomDatabase.Builder<StudyActivityDatabase> =
    androidRoomDatabaseBuilder(context, STUDY_ACTIVITY_DATABASE_FILE_NAME, StudyActivityDatabase::class.java)
