package com.crazyfluff.shellfstudy.core.database

import androidx.room.Room
import app.cash.turbine.test
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.database.AppDatabase
import com.crazyfluff.shellfstudy.shared.database.AssignmentEntity
import com.crazyfluff.shellfstudy.shared.database.SentenceMatchRow
import com.crazyfluff.shellfstudy.shared.database.StudyMaterialEntity
import com.crazyfluff.shellfstudy.shared.database.SubjectEntity
import com.crazyfluff.shellfstudy.shared.database.buildAppDatabase
import com.crazyfluff.shellfstudy.shared.network.ContextSentenceData
import com.crazyfluff.shellfstudy.shared.network.MeaningData
import com.crazyfluff.shellfstudy.shared.network.ReadingData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Runs `SubjectDao.observeSearch` against real SQLite, through Robolectric's Android SQLite.
 *
 * The repository escapes the user's literal `%`, `_` and `\` before handing the query to
 * `LIKE ... ESCAPE '\'`, while every other search test runs against `FakeSubjectDao`, which
 * re-implements that literal-match behaviour in Kotlin with `contains`. Those tests would all keep
 * passing if the escaping — or the escape clause itself — were wrong; these are the ones that run the
 * actual SQL.
 *
 * The database is built through the app's own [buildAppDatabase] so the query runs against the real
 * schema, with the bundled SQLite driver swapped for the Android one, which is what a host JVM can
 * execute. What the scan behind this query costs — and why no index is declared for it — is measured
 * and written down on `SubjectDao.observeSearch`; `EXPLAIN QUERY PLAN` itself cannot be run from a
 * test here, because the Android driver routes a non-SELECT statement to `execSQL`, which Android
 * refuses for anything that returns rows.
 */
@RunWith(AndroidJUnit4::class)
class SubjectSearchQueryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `an escaped percent matches that character, where an unescaped one is a wildcard`() = withDatabase { db ->
        seed(db, "50% off", "5012 off")

        assertThat(db.search("50\\% off")).containsExactly(1L)
        assertThat(db.search("50% off")).containsExactly(1L, 2L)
    }

    @Test
    fun `an escaped underscore matches that character, where an unescaped one is a wildcard`() = withDatabase { db ->
        seed(db, "under_score", "underXscore")

        assertThat(db.search("under\\_score")).containsExactly(1L)
        assertThat(db.search("under_score")).containsExactly(1L, 2L)
    }

    /** The escape character needs escaping first, or escaping a later `%`/`_` would re-escape it. */
    @Test
    fun `an escaped backslash matches a backslash`() = withDatabase { db ->
        seed(db, "back\\slash", "backslash")

        assertThat(db.search("back\\\\slash")).containsExactly(1L)
    }

    @Test
    fun `a user synonym finds its subject, case-insensitively`() = withDatabase { db ->
        seed(db, "water", "fire")
        db.studyMaterialDao().upsertAll(listOf(material(subjectId = 2, "Blaze", "Inferno")))

        assertThat(db.search("infer")).containsExactly(2L)
        assertThat(db.search("blaze")).containsExactly(2L)
    }

    /** Each synonym is matched on its own, not the stored JSON, so the `","` between two can't match. */
    @Test
    fun `a query spanning two synonyms matches neither`() = withDatabase { db ->
        seed(db, "fire")
        db.studyMaterialDao().upsertAll(listOf(material(subjectId = 1, "blaze", "inferno")))

        assertThat(db.search("ze\",\"in")).isEmpty()
    }

    @Test
    fun `the search re-runs when a synonym is added`() = withDatabase { db ->
        seed(db, "fire")
        db.subjectDao().observeSearch("blaze", ALL_TYPES, unlockedOnly = false).test {
            assertThat(awaitItem()).isEmpty()
            db.studyMaterialDao().upsertAll(listOf(material(subjectId = 1, "blaze")))
            assertThat(awaitItem().map { it.id }).containsExactly(1L)
        }
    }

    /** The same escaping the repository applies, on the same query string the DAO is annotated with. */
    @Test
    fun `the type filter keeps only the listed types`() = withDatabase { db ->
        db.subjectDao().upsertAll(
            listOf(subject(1, "water", type = "kanji"), subject(2, "water", type = "vocabulary"))
        )

        assertThat(db.search("water", types = listOf("kanji"))).containsExactly(1L)
        assertThat(db.search("water")).containsExactly(1L, 2L)
    }

    @Test
    fun `unlocked only keeps subjects with a visible assignment`() = withDatabase { db ->
        seed(db, "water", "water", "water")
        db.assignmentDao().upsertAll(
            listOf(assignment(id = 10, subjectId = 1), assignment(id = 11, subjectId = 2, hidden = true))
        )

        assertThat(db.search("water", unlockedOnly = true)).containsExactly(1L)
        assertThat(db.search("water", unlockedOnly = false)).containsExactly(1L, 2L, 3L)
    }

    @Test
    fun `sentence search matches Japanese, and English case-insensitively`() = withDatabase { db ->
        db.subjectDao().upsertAll(
            listOf(
                subject(1, "water", sentences = listOf(ContextSentenceData(en = "I drink Water.", ja = "水を飲みます。"))),
                subject(2, "fire", sentences = listOf(ContextSentenceData(en = "The fire is hot.", ja = "火は熱い。")))
            )
        )

        assertThat(db.sentenceSearch("飲み").map { it.subjectId }).containsExactly(1L)
        assertThat(db.sentenceSearch("drink water").single().japanese).isEqualTo("水を飲みます。")
        assertThat(db.sentenceSearch("hot").single().english).isEqualTo("The fire is hot.")
    }

    @Test
    fun `sentence search returns each matching sentence of a subject`() = withDatabase { db ->
        db.subjectDao().upsertAll(
            listOf(
                subject(
                    1,
                    "water",
                    sentences = listOf(
                        ContextSentenceData(en = "Cold water.", ja = "冷たい水。"),
                        ContextSentenceData(en = "Hot water.", ja = "お湯。")
                    )
                )
            )
        )

        assertThat(db.sentenceSearch("water").map { it.english }).containsExactly("Cold water.", "Hot water.").inOrder()
    }

    /** Each sentence is matched on its own fields, so the JSON between `en` and `ja` can't match. */
    @Test
    fun `a sentence query spanning the JSON between fields matches nothing`() = withDatabase { db ->
        val sentence = ContextSentenceData(en = "water", ja = "水")
        db.subjectDao().upsertAll(listOf(subject(1, "water", sentences = listOf(sentence))))

        assertThat(db.sentenceSearch("\",\"ja\":\"")).isEmpty()
    }

    @Test
    fun `sentence search honours the escaped percent and the filters`() = withDatabase { db ->
        db.subjectDao().upsertAll(
            listOf(
                subject(1, "a", sentences = listOf(ContextSentenceData(en = "100% sure.", ja = "絶対。"))),
                subject(
                    2,
                    "b",
                    sentences = listOf(ContextSentenceData(en = "100 times sure.", ja = "百回。")),
                    type = "kanji"
                )
            )
        )
        db.assignmentDao().upsertAll(listOf(assignment(id = 10, subjectId = 2)))

        assertThat(db.sentenceSearch("100\\% sure").map { it.subjectId }).containsExactly(1L)
        assertThat(db.sentenceSearch("sure", types = listOf("kanji")).map { it.subjectId }).containsExactly(2L)
        assertThat(db.sentenceSearch("sure", unlockedOnly = true).map { it.subjectId }).containsExactly(2L)
    }

    /** The same escaping the repository applies, on the same query string the DAO is annotated with. */
    private suspend fun AppDatabase.search(
        query: String,
        types: List<String> = ALL_TYPES,
        unlockedOnly: Boolean = false
    ): List<Long> = subjectDao().observeSearch(query, types, unlockedOnly).first().map { it.id }

    private suspend fun AppDatabase.sentenceSearch(
        query: String,
        types: List<String> = ALL_TYPES,
        unlockedOnly: Boolean = false
    ): List<SentenceMatchRow> = subjectDao().observeSentenceSearch(query, types, unlockedOnly).first()

    private suspend fun seed(db: AppDatabase, vararg meanings: String) {
        db.subjectDao().upsertAll(meanings.mapIndexed { index, meaning -> subject(index + 1L, meaning) })
    }

    private fun subject(
        id: Long,
        meaning: String,
        type: String = "vocabulary",
        sentences: List<ContextSentenceData> = emptyList()
    ) = SubjectEntity(
        id = id,
        subjectType = type,
        level = 1,
        slug = "test-$id",
        characters = "水",
        meanings = listOf(MeaningData(meaning = meaning, primary = true)),
        readings = listOf(ReadingData(reading = "みず", primary = true, type = "onyomi")),
        documentUrl = "https://www.wanikani.com/vocabulary/test-$id",
        contextSentences = sentences,
        // Real searchTargets are a lowercase concat of characters, slug, meanings and readings.
        searchTarget = "水 test-$id $meaning みず".lowercase()
    )

    private fun assignment(id: Long, subjectId: Long, hidden: Boolean = false) = AssignmentEntity(
        id = id,
        subjectId = subjectId,
        subjectType = "vocabulary",
        srsStage = 1,
        createdAt = "2026-01-01T00:00:00.000000Z",
        hidden = hidden
    )

    private fun material(subjectId: Long, vararg synonyms: String) =
        StudyMaterialEntity(id = subjectId, subjectId = subjectId, meaningSynonyms = synonyms.toList(), hidden = false)

    private fun withDatabase(block: suspend (AppDatabase) -> Unit) = runTest {
        val dbFile = tempFolder.newFile("search-test.db")
        val db = buildAppDatabase(
            Room.databaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java,
                dbFile.absolutePath
            ),
            AndroidSQLiteDriver()
        )
        try {
            block(db)
        } finally {
            db.close()
        }
    }

    private companion object {
        val ALL_TYPES = listOf("radical", "kanji", "vocabulary", "kana_vocabulary")
    }
}
