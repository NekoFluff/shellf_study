package com.crazyfluff.shellfstudy.shared.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "study_materials", indices = [Index("subjectId", unique = true)])
data class StudyMaterialEntity(
    @PrimaryKey val id: Long,
    val subjectId: Long,
    val subjectType: String,
    val meaningNote: String?,
    val readingNote: String?,
    val meaningSynonyms: List<String>,
    val hidden: Boolean
)

@Dao
interface StudyMaterialDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(materials: List<StudyMaterialEntity>)

    @Query("SELECT * FROM study_materials WHERE subjectId = :subjectId")
    suspend fun getBySubjectId(subjectId: Long): StudyMaterialEntity?

    @Query("SELECT * FROM study_materials")
    fun observeAll(): Flow<List<StudyMaterialEntity>>
}
