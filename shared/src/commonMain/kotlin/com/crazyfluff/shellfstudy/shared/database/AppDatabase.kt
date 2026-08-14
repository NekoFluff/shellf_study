package com.crazyfluff.shellfstudy.shared.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

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
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun assignmentDao(): AssignmentDao
    abstract fun srsSystemDao(): SrsSystemDao
    abstract fun reviewStatisticDao(): ReviewStatisticDao
    abstract fun studyMaterialDao(): StudyMaterialDao
    abstract fun levelProgressionDao(): LevelProgressionDao
    abstract fun syncStateDao(): SyncStateDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

internal const val APP_DATABASE_FILE_NAME = "shellf_study.db"

/** Applies the driver/dispatcher common to every platform's [AppDatabase] builder. */
fun buildAppDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        // Dispatchers.IO isn't public on every Kotlin/Native target for this coroutines version;
        // Default is fine here since queries just run against the bundled SQLite driver.
        .setQueryCoroutineContext(Dispatchers.Default)
        // Local cache only (subjects/assignments/etc. re-fetched from the API), so a destructive
        // migration on schema changes is simpler than hand-written Migration objects.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
