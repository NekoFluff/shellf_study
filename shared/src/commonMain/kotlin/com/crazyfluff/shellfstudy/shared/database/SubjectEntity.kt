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

/** A subject's id, type and [SubjectEntity.primaryReadingKey] — the "phonetically similar" lookup's
 *  row, kept to the three columns it matches on rather than whole subjects. */
data class SubjectReadingKeyRow(val id: Long, val subjectType: String, val primaryReadingKey: String)

/** A vocabulary subject's level and pronunciation clips — the offline audio library's catalog row,
 *  kept to the two columns it needs rather than whole subjects. */
data class SubjectAudioRow(val level: Int, val pronunciationAudios: List<PronunciationAudioData>)

/** One context sentence matching a search, with the subject it belongs to — the search overlay's
 *  Sentences tab row, kept to what that row shows rather than whole subjects. */
data class SentenceMatchRow(
    val subjectId: Long,
    val subjectType: String,
    val characters: String?,
    val japanese: String,
    val english: String
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
     * and prefixes where this search box matches any substring.
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
     * The search overlay's filters narrow the same scan rather than running a query of their own:
     * [subjectTypes] lists the wk type strings to keep (the caller passes all four for "any type"),
     * and [unlockedOnly] keeps subjects with a visible assignment — WaniKani only creates one when
     * the subject unlocks. Its `EXISTS` is one lookup on the `subjectId` index per scanned row, like
     * the synonym one, and naming `assignments` makes the search re-run as items unlock. */
    @Query(
        """
        SELECT * FROM subjects
        WHERE (
                searchTarget LIKE '%' || :query || '%' ESCAPE '\'
                OR EXISTS (
                    SELECT 1 FROM study_materials, json_each(study_materials.meaningSynonyms) AS synonym
                    WHERE study_materials.subjectId = subjects.id
                        AND lower(synonym.value) LIKE '%' || :query || '%' ESCAPE '\'
                )
            )
            AND subjectType IN (:subjectTypes)
            AND (:unlockedOnly = 0 OR EXISTS (
                SELECT 1 FROM assignments WHERE assignments.subjectId = subjects.id AND assignments.hidden = 0
            ))
        LIMIT 200
        """
    )
    fun observeSearch(query: String, subjectTypes: List<String>, unlockedOnly: Boolean): Flow<List<SubjectEntity>>

    /** Every context sentence containing [query], in Japanese or (case-insensitively) in English, with
     *  the subject it belongs to — the search overlay's Sentences tab. [query] is pre-escaped and
     *  lowercased exactly as for [observeSearch], and the type/unlocked filters mean the same. Each
     *  sentence is unpacked through `json_each`, so a query can't match across the JSON between two
     *  sentences or between a sentence's `en` and `ja`. Only vocabulary has sentences, so this scans
     *  the same table [observeSearch] does with one `json_each` per row; lowest level first, so the
     *  sentences a learner can read come before the ones they can't yet. */
    @Query(
        """
        SELECT subjects.id AS subjectId, subjects.subjectType AS subjectType, subjects.characters AS characters,
            json_extract(sentence.value, '$.ja') AS japanese, json_extract(sentence.value, '$.en') AS english
        FROM subjects, json_each(subjects.contextSentences) AS sentence
        WHERE (
                json_extract(sentence.value, '$.ja') LIKE '%' || :query || '%' ESCAPE '\'
                OR lower(json_extract(sentence.value, '$.en')) LIKE '%' || :query || '%' ESCAPE '\'
            )
            AND subjects.subjectType IN (:subjectTypes)
            AND (:unlockedOnly = 0 OR EXISTS (
                SELECT 1 FROM assignments WHERE assignments.subjectId = subjects.id AND assignments.hidden = 0
            ))
        ORDER BY subjects.level, subjects.id, sentence.key
        LIMIT 200
        """
    )
    fun observeSentenceSearch(
        query: String,
        subjectTypes: List<String>,
        unlockedOnly: Boolean
    ): Flow<List<SentenceMatchRow>>

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
