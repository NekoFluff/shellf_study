package com.crazyfluff.shellfstudy.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT characters FROM subjects WHERE subjectType IN ('vocabulary', 'kana_vocabulary') AND characters IS NOT NULL")
    suspend fun getVocabularyCharacters(): List<String>
}
