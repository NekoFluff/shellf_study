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
 *
 * A negative [id] is a placeholder for a material created on this device whose create request has not
 * come back yet — see StudyMaterialRepository. The server's row replaces it through the unique index.
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

    @Query("DELETE FROM study_materials WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM study_materials WHERE subjectId = :subjectId LIMIT 1")
    suspend fun getBySubjectId(subjectId: Long): StudyMaterialEntity?

    @Query("SELECT * FROM study_materials WHERE subjectId = :subjectId LIMIT 1")
    fun observeBySubjectId(subjectId: Long): Flow<StudyMaterialEntity?>

    /** Every material — small enough to hold whole: one row per subject the user has written about,
     *  which is the grading path's live synonym source for a whole session. */
    @Query("SELECT * FROM study_materials")
    fun observeAll(): Flow<List<StudyMaterialEntity>>
}
