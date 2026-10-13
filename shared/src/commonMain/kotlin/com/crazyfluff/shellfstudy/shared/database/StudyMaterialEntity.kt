package com.crazyfluff.shellfstudy.shared.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * The user's own study material for one subject — only the meaning synonyms, which grading accepts
 * alongside WaniKani's meanings. WaniKani keeps at most one per subject, hence the unique index.
 * Notes are not mirrored: nothing in the app shows them yet.
 */
@Entity(tableName = "study_materials", indices = [Index("subjectId", unique = true)])
data class StudyMaterialEntity(
    @PrimaryKey val id: Long,
    val subjectId: Long,
    val meaningSynonyms: List<String>,
    val hidden: Boolean
)

@Dao
interface StudyMaterialDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(materials: List<StudyMaterialEntity>)

    @Query("DELETE FROM study_materials")
    suspend fun clearAll()

    @Query("SELECT * FROM study_materials WHERE subjectId IN (:subjectIds)")
    fun observeBySubjectIds(subjectIds: List<Long>): Flow<List<StudyMaterialEntity>>
}
