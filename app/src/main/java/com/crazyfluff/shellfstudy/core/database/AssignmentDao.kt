package com.crazyfluff.shellfstudy.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AssignmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(assignments: List<AssignmentEntity>)

    @Query("SELECT * FROM assignments WHERE dueForReview = 1 AND hidden = 0")
    fun observeDueForReview(): Flow<List<AssignmentEntity>>

    @Query("DELETE FROM assignments WHERE dueForReview = 1")
    suspend fun clearDueForReview()

    @Transaction
    suspend fun replaceDueForReview(assignments: List<AssignmentEntity>) {
        clearDueForReview()
        upsertAll(assignments)
    }

    @Query("SELECT * FROM assignments WHERE dueForLesson = 1 AND hidden = 0")
    fun observeDueForLesson(): Flow<List<AssignmentEntity>>

    @Query("DELETE FROM assignments WHERE dueForLesson = 1")
    suspend fun clearDueForLesson()

    @Transaction
    suspend fun replaceDueForLesson(assignments: List<AssignmentEntity>) {
        clearDueForLesson()
        upsertAll(assignments)
    }
}
