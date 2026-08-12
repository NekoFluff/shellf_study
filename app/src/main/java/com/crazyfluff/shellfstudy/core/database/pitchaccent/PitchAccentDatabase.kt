package com.crazyfluff.shellfstudy.core.database.pitchaccent

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Isolated from [com.crazyfluff.shellfstudy.core.database.AppDatabase] for the same reason as
 * `studyactivity`, but the opposite motivation: this holds scraped pitch-accent data that IS
 * re-derivable (by re-scraping weblio.jp), but re-scraping is slow, rate-limit-sensitive, and
 * courteous to avoid repeating unnecessarily. Keeping it out of the disposable subject cache means
 * a schema bump there doesn't force every word to be re-scraped.
 */
@Database(entities = [PitchAccentCacheEntity::class], version = 1, exportSchema = true)
@TypeConverters(PitchAccentConverters::class)
abstract class PitchAccentDatabase : RoomDatabase() {
    abstract fun pitchAccentCacheDao(): PitchAccentCacheDao
}
