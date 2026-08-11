package com.crazyfluff.shellfstudy.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.crazyfluff.shellfstudy.core.network.AuxiliaryMeaningData
import com.crazyfluff.shellfstudy.core.network.ContextSentenceData
import com.crazyfluff.shellfstudy.core.network.MeaningData
import com.crazyfluff.shellfstudy.core.network.PronunciationAudioData
import com.crazyfluff.shellfstudy.core.network.ReadingData

@Entity(tableName = "subjects", indices = [Index("level"), Index("subjectType")])
data class SubjectEntity(
    @PrimaryKey val id: Long,
    val subjectType: String,
    val level: Int,
    val slug: String,
    val characters: String?,
    val characterImageUrl: String? = null,
    val meanings: List<MeaningData>,
    val readings: List<ReadingData>,
    val auxiliaryMeanings: List<AuxiliaryMeaningData> = emptyList(),
    val documentUrl: String?,
    val meaningMnemonic: String? = null,
    val readingMnemonic: String? = null,
    val meaningHint: String? = null,
    val readingHint: String? = null,
    val lessonPosition: Int = 0,
    val srsSystemId: Long = 0,
    val componentSubjectIds: List<Long> = emptyList(),
    val amalgamationSubjectIds: List<Long> = emptyList(),
    val visuallySimilarSubjectIds: List<Long> = emptyList(),
    val partsOfSpeech: List<String> = emptyList(),
    val contextSentences: List<ContextSentenceData> = emptyList(),
    val pronunciationAudios: List<PronunciationAudioData> = emptyList(),
    val hiddenAt: String? = null,
    /** Precomputed lowercase concat of characters+slug+meanings+readings, for LIKE search. */
    val searchTarget: String = ""
)
