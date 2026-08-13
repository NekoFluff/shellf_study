package com.crazyfluff.shellfstudy.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One subject type's total subject count — the item-spread "locked" type-breakdown source. */
data class SubjectTypeCount(val subjectType: String, val count: Int)

@Dao
interface SubjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(subjects: List<SubjectEntity>)

    @Query("SELECT * FROM subjects WHERE id IN (:ids)")
    fun observeByIds(ids: List<Long>): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun getById(id: Long): SubjectEntity?

    @Query("SELECT * FROM subjects")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE searchTarget LIKE '%' || :query || '%' LIMIT 200")
    fun observeSearch(query: String): Flow<List<SubjectEntity>>

    @Query("SELECT COUNT(*) FROM subjects")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT subjectType, COUNT(*) as count FROM subjects GROUP BY subjectType")
    fun observeTotalCountsByType(): Flow<List<SubjectTypeCount>>

    /** Vocab characters for subjects the user has actually unlocked — keeps the background pitch-accent scrape from fetching data for locked/future-level vocab. */
    @Query(
        """
        SELECT DISTINCT s.characters FROM subjects s
        JOIN assignments a ON a.subjectId = s.id
        WHERE s.subjectType IN ('vocabulary', 'kana_vocabulary')
          AND s.characters IS NOT NULL
          AND a.unlockedAt IS NOT NULL
          AND a.hidden = 0
        """
    )
    suspend fun getUnlockedVocabularyCharacters(): List<String>
}
