package com.crazyfluff.shellfstudy.shared.database.studytime

import android.content.Context
import androidx.room.RoomDatabase
import com.crazyfluff.shellfstudy.shared.database.androidRoomDatabaseBuilder

fun getStudyTimeDatabaseBuilder(context: Context): RoomDatabase.Builder<StudyTimeDatabase> =
    androidRoomDatabaseBuilder(context, STUDY_TIME_DATABASE_FILE_NAME, StudyTimeDatabase::class.java)
