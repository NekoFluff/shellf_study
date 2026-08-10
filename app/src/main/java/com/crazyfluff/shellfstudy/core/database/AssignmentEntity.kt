package com.crazyfluff.shellfstudy.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assignments")
data class AssignmentEntity(
    @PrimaryKey val id: Long,
    val subjectId: Long,
    val subjectType: String,
    val srsStage: Int,
    val availableAt: String?,
    val passedAt: String?,
    val burnedAt: String?,
    val hidden: Boolean,
    /** Set from the API's immediately_available_for_review filter when this row was cached. */
    val dueForReview: Boolean,
    /** Set from the API's immediately_available_for_lessons filter when this row was cached. */
    val dueForLesson: Boolean = false
)
