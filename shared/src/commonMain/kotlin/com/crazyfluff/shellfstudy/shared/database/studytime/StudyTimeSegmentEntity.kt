package com.crazyfluff.shellfstudy.shared.database.studytime

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One stretch of active lesson or review time: from a session clock starting (or resuming) to it
 * pausing, freezing at a checkpoint, or finishing. Only the raw stretch is stored — no local date —
 * so every read-side view (days, weeks, the hour heatmap) is derived from the same instants in the
 * current time zone and a stretch across midnight splits cleanly instead of landing on one day.
 */
@Entity(tableName = "study_time_segments", indices = [Index("startedAtMs")])
data class StudyTimeSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [com.crazyfluff.shellfstudy.shared.data.studytime.StudyKind] name. */
    val kind: String,
    val startedAtMs: Long,
    val durationMs: Long,
    /** The learner's WaniKani level when the stretch ended, or null if it wasn't cached yet. */
    val level: Int?,
    /** Reviews: answers graded. Lessons: items that finished their lesson quiz. Drives pace. */
    val itemsAnswered: Int
)

@Dao
interface StudyTimeDao {
    @Insert
    suspend fun insert(entity: StudyTimeSegmentEntity)

    @Query("SELECT * FROM study_time_segments ORDER BY startedAtMs")
    fun observeAll(): Flow<List<StudyTimeSegmentEntity>>

    @Query("DELETE FROM study_time_segments")
    suspend fun clearAll()
}
