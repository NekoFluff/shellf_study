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
        LevelProgressionEntity::class,
        SyncStateEntity::class
    ],
    version = 10,
    exportSchema = true
)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun assignmentDao(): AssignmentDao
    abstract fun srsSystemDao(): SrsSystemDao
    abstract fun reviewStatisticDao(): ReviewStatisticDao
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
        // Real migrations first — see [APP_DATABASE_MIGRATIONS]. The destructive fallback stays as a
        // safety net for a version jump no migration covers, where the alternative is a crash on open.
        // The local data is a cache re-fetchable from the API, so that trade is worth taking, but it is
        // the last resort rather than the normal path it used to be.
        .addMigrations(*APP_DATABASE_MIGRATIONS)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
