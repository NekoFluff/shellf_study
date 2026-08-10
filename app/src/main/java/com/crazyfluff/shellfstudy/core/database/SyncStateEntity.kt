package com.crazyfluff.shellfstudy.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/** Tracks the `updated_after` sync cursor and staleness timestamps for one API resource. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val resource: String,
    val lastSyncedAt: String? = null,
    val lastSyncAttemptAt: String? = null,
    val lastSyncSuccessAt: String? = null
)

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE resource = :resource")
    suspend fun get(resource: String): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)
}
