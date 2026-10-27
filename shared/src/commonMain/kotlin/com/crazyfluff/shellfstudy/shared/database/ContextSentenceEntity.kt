package com.crazyfluff.shellfstudy.shared.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One context sentence of a subject, unpacked from [SubjectEntity.contextSentences] so the search
 * overlay's Sentences tab can scan plain text instead of parsing every subject's JSON on each query.
 *
 * Nothing in Kotlin writes this table. The triggers in [CONTEXT_SENTENCE_TRIGGERS] keep it in step
 * with `subjects` inside the same statement that writes a subject, so a sync can't leave the two out
 * of step, and [MIGRATION_13_14] backfills it with the same SQL. [englishLower] is stored already
 * folded so the query does no `lower()` per row; like the rest of search, `lower` only folds ASCII.
 *
 * The `(level, subjectId, position)` index is the query's `ORDER BY`, so `LIMIT` stops the scan once
 * it has enough matches instead of collecting and sorting every one.
 *
 * Measured on the host against 9,000 subjects (6,500 vocabulary, 19,500 sentences), median of warm
 * runs: the previous query — `json_each` over `subjects.contextSentences` with `LIKE` — took 26-49 ms
 * whatever the query, since its `ORDER BY` had to find and sort every match. This table takes ~1 ms
 * for a common word ("water", "の") and 7 ms for a query matching nothing, which is the full scan.
 * `instr` rather than `LIKE '%…%'` is what brings that last case down from 19 ms: the query and
 * [englishLower] are both already lowercase, so a plain substring search is all it needs, and it has
 * no wildcards for a typed `%` or `_` to be mistaken for.
 */
@Entity(
    tableName = "context_sentences",
    primaryKeys = ["subjectId", "position"],
    indices = [Index("level", "subjectId", "position")]
)
data class ContextSentenceEntity(
    val subjectId: Long,
    val position: Int,
    val level: Int,
    val japanese: String,
    val english: String,
    val englishLower: String
)

/** One context sentence matching a search — the Sentences tab's row. */
data class SentenceMatchRow(
    val subjectId: Long,
    val characters: String?,
    val level: Int,
    val japanese: String,
    val english: String
)

@Dao
interface ContextSentenceDao {
    /** Sentences containing [query] in Japanese or, case-insensitively, in English, lowest level
     *  first. The caller lowercases [query]; unlike [SubjectDao.observeSearch] it isn't escaped, since
     *  `instr` has no wildcards.
     *
     *  This query deliberately doesn't name `assignments` — Room re-runs a query whenever a table it
     *  names is written, so naming it here would re-run the search on every review submission while
     *  the overlay is open. [observeUnlockedSearch] is the variant that does. */
    @Query(
        """
        SELECT context_sentences.subjectId, subjects.characters, context_sentences.level,
            context_sentences.japanese, context_sentences.english
        FROM context_sentences JOIN subjects ON subjects.id = context_sentences.subjectId
        WHERE instr(context_sentences.japanese, :query) > 0
            OR instr(context_sentences.englishLower, :query) > 0
        ORDER BY context_sentences.level, context_sentences.subjectId, context_sentences.position
        LIMIT 200
        """
    )
    fun observeSearch(query: String): Flow<List<SentenceMatchRow>>

    /** [observeSearch], kept to subjects with a visible assignment — the "Unlocked" filter. */
    @Query(
        """
        SELECT context_sentences.subjectId, subjects.characters, context_sentences.level,
            context_sentences.japanese, context_sentences.english
        FROM context_sentences JOIN subjects ON subjects.id = context_sentences.subjectId
        WHERE (
                instr(context_sentences.japanese, :query) > 0
                OR instr(context_sentences.englishLower, :query) > 0
            )
            AND EXISTS (
                SELECT 1 FROM assignments
                WHERE assignments.subjectId = context_sentences.subjectId AND assignments.hidden = 0
            )
        ORDER BY context_sentences.level, context_sentences.subjectId, context_sentences.position
        LIMIT 200
        """
    )
    fun observeUnlockedSearch(query: String): Flow<List<SentenceMatchRow>>
}

/** The rows [subject] contributes to `context_sentences`, as an `INSERT … SELECT` over its JSON — a
 *  trigger's `NEW` row, or every row of `subjects` when [from] joins it in. Shared by the triggers and
 *  the backfill so a migrated row and a freshly synced one are identical. The `coalesce`s keep a
 *  sentence missing a field from failing the subject write it rides along with. */
private fun insertSentencesOf(subject: String, from: String = "") =
    "INSERT INTO context_sentences (subjectId, position, level, japanese, english, englishLower) " +
        "SELECT $subject.id, sentence.key, $subject.level, " +
        "coalesce(json_extract(sentence.value, '$.ja'), ''), " +
        "coalesce(json_extract(sentence.value, '$.en'), ''), " +
        "lower(coalesce(json_extract(sentence.value, '$.en'), '')) " +
        "FROM $from json_each($subject.contextSentences) AS sentence"

/** Keeps `context_sentences` in step with `subjects`. Room writes subjects with `INSERT OR REPLACE`,
 *  whose implicit delete fires no delete trigger, so the insert trigger clears the old rows itself. */
internal val CONTEXT_SENTENCE_TRIGGERS = listOf(
    "CREATE TRIGGER IF NOT EXISTS context_sentences_after_subject_insert AFTER INSERT ON subjects BEGIN " +
        "DELETE FROM context_sentences WHERE subjectId = NEW.id; ${insertSentencesOf("NEW")}; END",
    "CREATE TRIGGER IF NOT EXISTS context_sentences_after_subject_update " +
        "AFTER UPDATE OF level, contextSentences ON subjects BEGIN " +
        "DELETE FROM context_sentences WHERE subjectId = NEW.id; ${insertSentencesOf("NEW")}; END",
    "CREATE TRIGGER IF NOT EXISTS context_sentences_after_subject_delete AFTER DELETE ON subjects BEGIN " +
        "DELETE FROM context_sentences WHERE subjectId = OLD.id; END"
)

/** Backfills `context_sentences` for every subject already stored, with the triggers' own SQL. */
internal val BACKFILL_CONTEXT_SENTENCES = insertSentencesOf("subjects", from = "subjects,")
