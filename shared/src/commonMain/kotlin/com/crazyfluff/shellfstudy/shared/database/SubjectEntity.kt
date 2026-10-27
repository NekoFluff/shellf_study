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

/** One subject search result: what a result row shows, plus how many subjects matched in all — the
 *  same on every row, since the query returns only the first [SubjectDao.observeSearch]'s `limit`. */
data class SubjectSearchRow(
    val id: Long,
    val subjectType: String,
    val characters: String?,
    val characterImageUrl: String?,
    val level: Int,
    val meanings: List<MeaningData>,
    val readings: List<ReadingData>,
    val totalCount: Int
)

/** A subject's id, type and [SubjectEntity.primaryReadingKey] — the "phonetically similar" lookup's
 *  row, kept to the three columns it matches on rather than whole subjects. */
data class SubjectReadingKeyRow(val id: Long, val subjectType: String, val primaryReadingKey: String)

/** A vocabulary subject's level and pronunciation clips — the offline audio library's catalog row,
 *  kept to the two columns it needs rather than whole subjects. */
data class SubjectAudioRow(val level: Int, val pronunciationAudios: List<PronunciationAudioData>)

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
     * The leading wildcard makes this a full scan of the subject library: no index can narrow a
     * substring search. Against this schema with 9,000 rows of realistic payloads (a 9.7 MB table) a
     * scan reads 2,257 pages, the whole table. An index on `searchTarget` was tried and passed over:
     * a covering index scan reads ~18x fewer pages, but only when selecting ids alone, and any
     * `ORDER BY` other than the index's own gives that back. An FTS table was rejected as well: it
     * matches tokens and prefixes where this search box matches any substring.
     *
     * Results come best match first — the whole word or character typed (`水`, "fire"), then a word
     * that starts with it ("pi" → "pillar"), then the rest, each lowest level first — and a match on
     * one of the user's own synonyms ranks with the prefix matches. Ranking every match means the
     * scan can't stop at [limit], and [SubjectSearchRow.totalCount] (`COUNT(*) OVER ()`) comes from
     * that same pass, so the true total costs nothing more. Measured on the host against the same
     * 9,000 rows, median of warm runs: 12 ms for "p" (1,800 matches) and "water", 10 ms for a query
     * matching nothing — against 2-4 ms for the previous unranked `LIMIT 200`, which stopped early
     * but showed the 200 lowest ids, in id order, under a count that was really just "200". The
     * search is debounced (see `SearchViewModel.QUERY_DEBOUNCE_MILLIS`) and runs on Room's own
     * dispatcher. Only the listed columns are read out, not the whole row with its mnemonics.
     *
     * The user's own meaning synonyms match too. They live in `study_materials` rather than in
     * `searchTarget`, because they change on their own sync and from this device's edits, and baking
     * them into the subject row would mean rewriting it on every synonym change. Matching each
     * synonym through `json_each` rather than the raw JSON text keeps a query from matching across
     * the quotes and commas between them. The `EXISTS` costs one lookup on the unique `subjectId`
     * index per scanned row, which is cheap next to the scan itself, and naming the table here makes
     * Room re-run the search when a synonym is added or removed. `lower` only folds ASCII, which is
     * all an English synonym needs; the caller lowercases [query].
     *
     * [subjectTypes] lists the wk type strings to keep (the caller passes all four for "any type").
     * This query deliberately doesn't name `assignments`: Room re-runs a query whenever a table it
     * names is written, so naming it would re-run the search on every review submission while the
     * overlay is open. [observeUnlockedSearch] is the variant that does. */
    @Query(
        """
        SELECT id, subjectType, characters, characterImageUrl, level, meanings, readings,
            COUNT(*) OVER () AS totalCount
        FROM subjects
        WHERE (
                searchTarget LIKE '%' || :query || '%' ESCAPE '\'
                OR EXISTS (
                    SELECT 1 FROM study_materials, json_each(study_materials.meaningSynonyms) AS synonym
                    WHERE study_materials.subjectId = subjects.id
                        AND lower(synonym.value) LIKE '%' || :query || '%' ESCAPE '\'
                )
            )
            AND subjectType IN (:subjectTypes)
        ORDER BY
            CASE
                WHEN ' ' || searchTarget || ' ' LIKE '% ' || :query || ' %' ESCAPE '\' THEN 0
                WHEN ' ' || searchTarget LIKE '% ' || :query || '%' ESCAPE '\' THEN 1
                WHEN searchTarget NOT LIKE '%' || :query || '%' ESCAPE '\' THEN 1
                ELSE 2
            END,
            level,
            id
        LIMIT :limit
        """
    )
    fun observeSearch(query: String, subjectTypes: List<String>, limit: Int): Flow<List<SubjectSearchRow>>

    /** [observeSearch], kept to subjects with a visible assignment — the "Unlocked" filter. WaniKani
     *  only creates an assignment when its subject unlocks. The `EXISTS` is one lookup on the
     *  `subjectId` index per scanned row, like the synonym one. */
    @Query(
        """
        SELECT id, subjectType, characters, characterImageUrl, level, meanings, readings,
            COUNT(*) OVER () AS totalCount
        FROM subjects
        WHERE (
                searchTarget LIKE '%' || :query || '%' ESCAPE '\'
                OR EXISTS (
                    SELECT 1 FROM study_materials, json_each(study_materials.meaningSynonyms) AS synonym
                    WHERE study_materials.subjectId = subjects.id
                        AND lower(synonym.value) LIKE '%' || :query || '%' ESCAPE '\'
                )
            )
            AND subjectType IN (:subjectTypes)
            AND EXISTS (
                SELECT 1 FROM assignments WHERE assignments.subjectId = subjects.id AND assignments.hidden = 0
            )
        ORDER BY
            CASE
                WHEN ' ' || searchTarget || ' ' LIKE '% ' || :query || ' %' ESCAPE '\' THEN 0
                WHEN ' ' || searchTarget LIKE '% ' || :query || '%' ESCAPE '\' THEN 1
                WHEN searchTarget NOT LIKE '%' || :query || '%' ESCAPE '\' THEN 1
                ELSE 2
            END,
            level,
            id
        LIMIT :limit
        """
    )
    fun observeUnlockedSearch(query: String, subjectTypes: List<String>, limit: Int): Flow<List<SubjectSearchRow>>

    /** Every subject whose primary reading is one of [readingKeys], lowest level first — the
     *  candidates for the "Phonetically similar" section. Which of them count as similar to a given
     *  subject (vocabulary only, not the subject itself) is decided by the caller. */
    @Query(
        """
        SELECT id, subjectType, primaryReadingKey FROM subjects
        WHERE primaryReadingKey IN (:readingKeys) AND primaryReadingKey != ''
        ORDER BY level, id
        """
    )
    fun observeByPrimaryReadingKeys(readingKeys: List<String>): Flow<List<SubjectReadingKeyRow>>

    @Query("SELECT level, pronunciationAudios FROM subjects WHERE subjectType IN ('vocabulary', 'kana_vocabulary')")
    suspend fun getAudioRows(): List<SubjectAudioRow>

    @Query("SELECT characterImageUrl FROM subjects WHERE level = :level AND characterImageUrl IS NOT NULL")
    suspend fun getCharacterImageUrlsAtLevel(level: Int): List<String>

    @Query("SELECT COUNT(*) FROM subjects")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT subjectType, COUNT(*) as count FROM subjects GROUP BY subjectType")
    fun observeTotalCountsByType(): Flow<List<SubjectTypeCount>>
}
