package com.crazyfluff.shellfstudy.shared.database.pitchaccent

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * Isolated from [com.crazyfluff.shellfstudy.shared.database.AppDatabase] for the same reason as
 * `studyactivity`, but the opposite motivation: this holds scraped pitch-accent data that IS
 * re-derivable (by re-scraping weblio.jp), but re-scraping is slow, rate-limit-sensitive, and
 * courteous to avoid repeating unnecessarily. Keeping it out of the disposable subject cache means
 * a schema bump there doesn't force every word to be re-scraped.
 */
@Database(entities = [PitchAccentCacheEntity::class], version = 1, exportSchema = true)
@TypeConverters(PitchAccentConverters::class)
@ConstructedBy(PitchAccentDatabaseConstructor::class)
abstract class PitchAccentDatabase : RoomDatabase() {
    abstract fun pitchAccentCacheDao(): PitchAccentCacheDao
}

@Suppress("KotlinNoActualForExpect")
expect object PitchAccentDatabaseConstructor : RoomDatabaseConstructor<PitchAccentDatabase> {
    override fun initialize(): PitchAccentDatabase
}

internal const val PITCH_ACCENT_DATABASE_FILE_NAME = "pitch_accent.db"

fun buildPitchAccentDatabase(builder: RoomDatabase.Builder<PitchAccentDatabase>): PitchAccentDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
