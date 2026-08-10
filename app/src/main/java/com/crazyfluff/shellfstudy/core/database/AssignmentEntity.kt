package com.crazyfluff.shellfstudy.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "assignments", indices = [Index("subjectId"), Index("availableAt")])
data class AssignmentEntity(
    @PrimaryKey val id: Long,
    val subjectId: Long,
    val subjectType: String,
    val srsStage: Int,
    val createdAt: String,
    val unlockedAt: String? = null,
    val startedAt: String? = null,
    val passedAt: String? = null,
    val burnedAt: String? = null,
    val availableAt: String? = null,
    val resurrectedAt: String? = null,
    val hidden: Boolean
)
