package com.crazyfluff.shellfstudy.shared.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.crazyfluff.shellfstudy.shared.network.AuxiliaryMeaningData
import com.crazyfluff.shellfstudy.shared.network.ContextSentenceData
import com.crazyfluff.shellfstudy.shared.network.MeaningData
import com.crazyfluff.shellfstudy.shared.network.PronunciationAudioData
import com.crazyfluff.shellfstudy.shared.network.ReadingData
import kotlinx.coroutines.flow.Flow

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

/** One subject type's total subject count — the item-spread "locked" type-breakdown source. */
data class SubjectTypeCount(val subjectType: String, val count: Int)

@Dao
interface SubjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(subjects: List<SubjectEntity>)

    @Query("SELECT * FROM subjects WHERE id IN (:ids)")
    fun observeByIds(ids: List<Long>): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun getById(id: Long): SubjectEntity?

    @Query("SELECT * FROM subjects")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE searchTarget LIKE '%' || :query || '%' LIMIT 200")
    fun observeSearch(query: String): Flow<List<SubjectEntity>>

    @Query("SELECT COUNT(*) FROM subjects")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT subjectType, COUNT(*) as count FROM subjects GROUP BY subjectType")
    fun observeTotalCountsByType(): Flow<List<SubjectTypeCount>>

    /** Vocab characters for subjects the user has actually unlocked — keeps the background pitch-accent scrape from fetching data for locked/future-level vocab. */
    @Query(
        """
        SELECT DISTINCT s.characters FROM subjects s
        JOIN assignments a ON a.subjectId = s.id
        WHERE s.subjectType IN ('vocabulary', 'kana_vocabulary')
          AND s.characters IS NOT NULL
          AND a.unlockedAt IS NOT NULL
          AND a.hidden = 0
        """
    )
    suspend fun getUnlockedVocabularyCharacters(): List<String>
}
