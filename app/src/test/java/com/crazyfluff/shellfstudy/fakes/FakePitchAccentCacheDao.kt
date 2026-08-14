package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.shared.database.pitchaccent.PitchAccentCacheDao
import com.crazyfluff.shellfstudy.shared.database.pitchaccent.PitchAccentCacheEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory stand-in for [PitchAccentCacheDao] used by repository/ViewModel unit tests. */
class FakePitchAccentCacheDao : PitchAccentCacheDao {
    private val entries = MutableStateFlow<Map<String, PitchAccentCacheEntity>>(emptyMap())

    override fun observeByCharacters(characters: String): Flow<PitchAccentCacheEntity?> =
        entries.map { it[characters] }

    override suspend fun upsert(entity: PitchAccentCacheEntity) {
        entries.value = entries.value + (entity.characters to entity)
    }

    override suspend fun getFreshCharacters(cutoffMillis: Long): List<String> =
        entries.value.values.filter { it.lastAttemptedAt >= cutoffMillis }.map { it.characters }
}
