package com.crazyfluff.shellfstudy.shared.database.studytime

import androidx.room.RoomDatabase
import com.crazyfluff.shellfstudy.shared.database.iosRoomDatabaseBuilder

fun getStudyTimeDatabaseBuilder(): RoomDatabase.Builder<StudyTimeDatabase> =
    iosRoomDatabaseBuilder(STUDY_TIME_DATABASE_FILE_NAME)
