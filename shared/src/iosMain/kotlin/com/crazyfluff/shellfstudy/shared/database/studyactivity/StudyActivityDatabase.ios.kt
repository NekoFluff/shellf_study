package com.crazyfluff.shellfstudy.shared.database.studyactivity

import androidx.room.RoomDatabase
import com.crazyfluff.shellfstudy.shared.database.iosRoomDatabaseBuilder

fun getStudyActivityDatabaseBuilder(): RoomDatabase.Builder<StudyActivityDatabase> =
    iosRoomDatabaseBuilder(STUDY_ACTIVITY_DATABASE_FILE_NAME)
