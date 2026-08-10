package com.crazyfluff.shellfstudy.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.crazyfluff.shellfstudy.core.network.MeaningData
import com.crazyfluff.shellfstudy.core.network.ReadingData

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey val id: Long,
    val subjectType: String,
    val level: Int,
    val slug: String,
    val characters: String?,
    val meanings: List<MeaningData>,
    val readings: List<ReadingData>,
    val documentUrl: String?,
    val meaningMnemonic: String? = null,
    val readingMnemonic: String? = null
)
