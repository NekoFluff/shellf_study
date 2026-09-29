package com.crazyfluff.shellfstudy.core.database

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.database.AppDatabase
import com.crazyfluff.shellfstudy.shared.database.SubjectEntity
import com.crazyfluff.shellfstudy.shared.database.buildAppDatabase
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

    /** The same escaping the repository applies, on the same query string the DAO is annotated with. */
    private suspend fun AppDatabase.search(query: String): List<Long> =
        subjectDao().observeSearch(query).first().map { it.id }

    private suspend fun seed(db: AppDatabase, vararg meanings: String) {
        db.subjectDao().upsertAll(meanings.mapIndexed { index, meaning -> subject(index + 1L, meaning) })
    }

    private fun subject(id: Long, meaning: String) = SubjectEntity(
        id = id,
        subjectType = "vocabulary",
        level = 1,
        slug = "test-$id",
        characters = "水",
        meanings = listOf(MeaningData(meaning = meaning, primary = true)),
        readings = listOf(ReadingData(reading = "みず", primary = true, type = "onyomi")),
        documentUrl = "https://www.wanikani.com/vocabulary/test-$id",
        // Real searchTargets are a lowercase concat of characters, slug, meanings and readings.
        searchTarget = "水 test-$id $meaning みず".lowercase()
    )

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

}
