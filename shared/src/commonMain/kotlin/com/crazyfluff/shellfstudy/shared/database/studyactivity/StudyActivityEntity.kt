package com.crazyfluff.shellfstudy.shared.database.studyactivity

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One calendar day (local zone, ISO `YYYY-MM-DD`) on which the user completed at least one
 *  review — the only local record needed to drive the study-streak reminder notification. */
@Entity(tableName = "study_activity_days")
data class StudyActivityDayEntity(@PrimaryKey val date: String)

@Dao
interface StudyActivityDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markActive(entity: StudyActivityDayEntity)

    @Query("SELECT date FROM study_activity_days ORDER BY date DESC")
    fun observeActiveDays(): Flow<List<String>>
}
