package com.crazyfluff.shellfstudy.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.crazyfluff.shellfstudy.core.network.SrsStageData
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "srs_systems")
data class SrsSystemEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val unlockingStagePosition: Int,
    val startingStagePosition: Int,
    val passingStagePosition: Int,
    val burningStagePosition: Int,
    val stages: List<SrsStageData> = emptyList()
)

@Dao
interface SrsSystemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(systems: List<SrsSystemEntity>)

    @Query("SELECT * FROM srs_systems")
    fun observeAll(): Flow<List<SrsSystemEntity>>

    @Query("SELECT * FROM srs_systems WHERE id = :id")
    suspend fun getById(id: Long): SrsSystemEntity?
}
