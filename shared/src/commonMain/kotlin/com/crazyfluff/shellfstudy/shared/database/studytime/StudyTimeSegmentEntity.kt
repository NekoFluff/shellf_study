package com.crazyfluff.shellfstudy.shared.database.studytime

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * One stretch of active lesson or review time: from a session clock starting (or resuming) to it
 * pausing, freezing at a checkpoint, or finishing. Only the raw stretch is stored — no local date —
 * so every read-side view (days, weeks, the hour heatmap) is derived from the same instants in the
 * current time zone and a stretch across midnight splits cleanly instead of landing on one day.
 */
@OptIn(ExperimentalUuidApi::class)
@Entity(
    tableName = "study_time_segments",
    indices = [Index("startedAtMs"), Index(value = ["syncId"], unique = true)]
)
data class StudyTimeSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [com.crazyfluff.shellfstudy.shared.data.studytime.StudyKind] name. */
    val kind: String,
    val startedAtMs: Long,
    val durationMs: Long,
    /** The learner's WaniKani level when the stretch ended, or null if it wasn't cached yet. */
    val level: Int?,
    /** Reviews: answers graded. Lessons: items that finished their lesson quiz. Drives pace. */
    val itemsAnswered: Int,
    /** The stretch's identity on every device, and its record id in history sync. [id] is only
     *  meaningful on the device that made the row. */
    val syncId: String = Uuid.random().toString(),
    /** Whether the history-sync backend already has this row: uploaded from here, or pulled from it. */
    @ColumnInfo(defaultValue = "0")
    val uploaded: Boolean = false
)

@Dao
interface StudyTimeDao {
    @Insert
    suspend fun insert(entity: StudyTimeSegmentEntity)

    @Query("SELECT * FROM study_time_segments ORDER BY startedAtMs")
    fun observeAll(): Flow<List<StudyTimeSegmentEntity>>

    @Query("DELETE FROM study_time_segments")
    suspend fun clearAll()

    @Query("SELECT * FROM study_time_segments WHERE uploaded = 0 ORDER BY id LIMIT :limit")
    suspend fun getNotUploaded(limit: Int): List<StudyTimeSegmentEntity>

    @Query("UPDATE study_time_segments SET uploaded = 1 WHERE syncId IN (:syncIds)")
    suspend fun markUploaded(syncIds: List<String>)

    /** After the server copy is deleted, so turning sync back on uploads everything again. */
    @Query("UPDATE study_time_segments SET uploaded = 0")
    suspend fun markAllNotUploaded()

    /** Stretches from other devices. One already here, by [StudyTimeSegmentEntity.syncId], is kept. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFromRemote(entities: List<StudyTimeSegmentEntity>)
}
