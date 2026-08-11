package com.crazyfluff.shellfstudy.core.database.pitchaccent

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.crazyfluff.shellfstudy.core.data.model.PitchAccent
import kotlinx.coroutines.flow.Flow

/**
 * A vocab word's live-scraped pitch accent data (see [PitchAccentDatabase] for why this is
 * isolated from the main subject cache). [fetchedAt] is null until a scrape has actually returned
 * data; [lastAttemptedAt] advances on every attempt, success or failure, so the background worker
 * can back off from words weblio has no entry for instead of retrying them every run.
 */
@Entity(tableName = "pitch_accent_cache")
data class PitchAccentCacheEntity(
    @PrimaryKey val characters: String,
    val pitchAccents: List<PitchAccent>,
    val fetchedAt: Long?,
    val lastAttemptedAt: Long
)

@Dao
interface PitchAccentCacheDao {
    @Query("SELECT * FROM pitch_accent_cache WHERE characters = :characters")
    fun observeByCharacters(characters: String): Flow<PitchAccentCacheEntity?>

    @Upsert
    suspend fun upsert(entity: PitchAccentCacheEntity)

    /** Characters attempted since [cutoffMillis] — used to exclude already-fresh words from a scrape batch. */
    @Query("SELECT characters FROM pitch_accent_cache WHERE lastAttemptedAt >= :cutoffMillis")
    suspend fun getFreshCharacters(cutoffMillis: Long): List<String>
}
