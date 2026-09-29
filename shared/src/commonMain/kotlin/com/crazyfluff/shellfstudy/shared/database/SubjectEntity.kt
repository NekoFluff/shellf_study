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

@Entity(tableName = "subjects", indices = [Index("level"), Index("subjectType"), Index("primaryReadingKey")])
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
    val searchTarget: String = "",
    /** Katakana-normalized primary reading (vocabulary/kana-vocabulary only), for the
     *  "phonetically similar" section — matches subjects whose primary reading is identical. */
    val primaryReadingKey: String = ""
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

    /** [query] must already have its own literal `\`, `%` and `_` characters backslash-escaped by
     *  the caller (see [com.crazyfluff.shellfstudy.shared.data.SubjectRepository.observeSearch]) —
     *  Room can't do that for you, and without it a query containing a literal `%`/`_` is
     *  interpreted as a wildcard instead of matching that literal character.
     *
     * The leading wildcard makes this a full scan of the subject library, which was measured rather
     * than assumed. Against this schema with 9,000 rows of realistic payloads (a 9.7 MB table):
     * `EXPLAIN QUERY PLAN` reports `SCAN subjects`, and the selective case — a query matching one
     * row near the end, so `LIMIT 200` never cuts the scan short — reads 2,257 pages through
     * `PRAGMA stats`, the whole table, in 2-3 ms warm on the host.
     *
     * No index can narrow a substring search, and adding one cannot help this query at all: with an
     * index on `searchTarget` present, the plan for this `SELECT *` is still `SCAN subjects`, since a
     * row's other columns have to come from the table whatever the filter does. An index would
     * therefore only add writes to every sync of the library.
     *
     * The shape that does use it was measured too and passed over. Selecting only ids plans as
     * `SCAN subjects USING COVERING INDEX index_subjects_searchTarget` — 120 page reads, ~18x less
     * I/O — and [observeByIds] would then fetch the at most 200 matches. But that returns the first
     * 200 matches in index (alphabetical) order where today's returns the 200 lowest ids, and asking
     * for `ORDER BY id` to match gives the order back by giving up the index (planned and measured:
     * `SCAN subjects` again, 2,257 pages). With the search debounced to at most one query per 200 ms
     * of typing (see `SearchViewModel.QUERY_DEBOUNCE_MILLIS`) on Room's own dispatcher, the saving is
     * not worth a migration plus a reordering. An FTS table was rejected as well: it matches tokens
     * and prefixes where this search box matches any substring. */
    @Query("SELECT * FROM subjects WHERE searchTarget LIKE '%' || :query || '%' ESCAPE '\\' LIMIT 200")
    fun observeSearch(query: String): Flow<List<SubjectEntity>>

    /** Other vocabulary/kana-vocabulary subjects sharing this subject's exact primary reading —
     *  powers the "Phonetically similar" section. */
    @Query(
        """
        SELECT id FROM subjects
        WHERE subjectType IN ('vocabulary', 'kana_vocabulary')
          AND primaryReadingKey = :readingKey AND primaryReadingKey != ''
          AND id != :excludeId
        """
    )
    fun observePhoneticallySimilarIds(readingKey: String, excludeId: Long): Flow<List<Long>>

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
