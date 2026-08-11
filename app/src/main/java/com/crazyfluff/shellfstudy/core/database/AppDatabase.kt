package com.crazyfluff.shellfstudy.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        SubjectEntity::class,
        AssignmentEntity::class,
        SrsSystemEntity::class,
        ReviewStatisticEntity::class,
        StudyMaterialEntity::class,
        LevelProgressionEntity::class,
        SyncStateEntity::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun assignmentDao(): AssignmentDao
    abstract fun srsSystemDao(): SrsSystemDao
    abstract fun reviewStatisticDao(): ReviewStatisticDao
    abstract fun studyMaterialDao(): StudyMaterialDao
    abstract fun levelProgressionDao(): LevelProgressionDao
    abstract fun syncStateDao(): SyncStateDao
}
