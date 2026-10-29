package com.crazyfluff.shellfstudy.shared.database.studyactivity

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One calendar day (local zone, ISO `YYYY-MM-DD`) on which the user completed at least one
 *  review or lesson item — the local record behind the study streak and its reminder
 *  notification. */
@Entity(tableName = "study_activity_days")
data class StudyActivityDayEntity(
    @PrimaryKey val date: String,
    /** Whether the history-sync backend already has this day: uploaded from here, or pulled from it. */
    @ColumnInfo(defaultValue = "0")
    val uploaded: Boolean = false
)

@Dao
interface StudyActivityDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markActive(entity: StudyActivityDayEntity)

    @Query("DELETE FROM study_activity_days")
    suspend fun clearAll()

    @Query("SELECT date FROM study_activity_days ORDER BY date DESC")
    fun observeActiveDays(): Flow<List<String>>

    @Query("SELECT * FROM study_activity_days WHERE uploaded = 0 ORDER BY date LIMIT :limit")
    suspend fun getNotUploaded(limit: Int): List<StudyActivityDayEntity>

    @Query("UPDATE study_activity_days SET uploaded = 1 WHERE date IN (:dates)")
    suspend fun markUploaded(dates: List<String>)

    /** After the server copy is deleted, so turning sync back on uploads everything again. */
    @Query("UPDATE study_activity_days SET uploaded = 0")
    suspend fun markAllNotUploaded()

    /** Days from other devices. A day already here is kept as it is. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFromRemote(entities: List<StudyActivityDayEntity>)
}
