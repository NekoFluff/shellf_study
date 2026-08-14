package com.crazyfluff.shellfstudy.shared.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "level_progressions", indices = [Index("level")])
data class LevelProgressionEntity(
    @PrimaryKey val id: Long,
    val level: Int,
    val createdAt: String,
    val unlockedAt: String?,
    val startedAt: String?,
    val passedAt: String?,
    val completedAt: String?,
    val abandonedAt: String?
)

@Dao
interface LevelProgressionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(progressions: List<LevelProgressionEntity>)

    @Query("SELECT * FROM level_progressions")
    fun observeAll(): Flow<List<LevelProgressionEntity>>
}
