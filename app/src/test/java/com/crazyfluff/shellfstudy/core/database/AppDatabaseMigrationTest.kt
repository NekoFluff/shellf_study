package com.crazyfluff.shellfstudy.core.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.database.APP_DATABASE_MIGRATIONS
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs [APP_DATABASE_MIGRATIONS] against a real SQLite database.
 *
 * Migrations had no test before this, and their failure mode is quiet rather than loud:
 * `buildAppDatabase` falls back to a destructive migration, so a migration whose DDL does not
 * reproduce the schema Room expects does not crash — it drops the tables and re-downloads the user's
 * whole subject library instead. The migration list is public so this test, which has to live in
 * `:app` (see below), can apply the real list rather than a copy.
 *
 * It lives here rather than in `:shared`'s commonTest because the driver this project uses at runtime,
 * `BundledSQLiteDriver`, publishes no JVM variant — on a host the native library is simply absent, and
 * `:shared`'s host tests run on a plain JVM. Robolectric is already configured here and provides the
 * Android framework SQLite, so `AndroidSQLiteDriver` can execute the DDL on the same machine as the
 * rest of the suite.
 *
 * The stand-in table holds only the columns the statements touch, which is deliberate: it keeps this
 * test from parsing the exported schema JSON, and each migration is checked against the objects it
 * names. What it does not cover is Room's own post-migration schema validation, which needs a full
 * v10 database and runs on device.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @Test
    fun `migration 10 to 11 swaps the redundant availableAt index for the burnedAt one`() {
        withDatabase { connection ->
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_assignments_availableAt` ON `assignments` (`availableAt`)")

            migrationFrom(10).migrate(connection)

            val indexes = connection.indexNames("assignments")
            assertThat(indexes).contains("index_assignments_hidden_burnedAt")
            assertThat(indexes).doesNotContain("index_assignments_availableAt")
        }
    }

    /** A migration has to be re-runnable, which is what the `IF EXISTS`/`IF NOT EXISTS` are for. */
    @Test
    fun `migration 10 to 11 tolerates being applied twice`() {
        withDatabase { connection ->
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_assignments_availableAt` ON `assignments` (`availableAt`)")

            migrationFrom(10).migrate(connection)
            migrationFrom(10).migrate(connection)

            assertThat(connection.indexNames("assignments")).contains("index_assignments_hidden_burnedAt")
        }
    }

    @Test
    fun `migration 11 to 12 creates the study materials table with its unique subject index`() {
        withDatabase { connection ->
            migrationFrom(11).migrate(connection)
            migrationFrom(11).migrate(connection)

            assertThat(connection.indexNames("study_materials")).contains("index_study_materials_subjectId")
            // A second row for the same subject replaces the first, as the unique index promises.
            val columns = "study_materials (id, subjectId, meaningSynonyms, hidden)"
            connection.execSQL("INSERT INTO $columns VALUES (1, 440, '[\"aqua\"]', 0)")
            connection.execSQL("INSERT OR REPLACE INTO $columns VALUES (2, 440, '[]', 0)")
            val count = connection.prepare("SELECT COUNT(*) FROM study_materials")
            try {
                count.step()
                assertThat(count.getLong(0)).isEqualTo(1)
            } finally {
                count.close()
            }
        }
    }

    @Test
    fun `migration 12 to 13 appends whitelisted auxiliary meanings to the search target`() {
        withDatabase { connection ->
            connection.execSQL(
                "CREATE TABLE `subjects` (`id` INTEGER NOT NULL, `auxiliaryMeanings` TEXT NOT NULL, " +
                    "`searchTarget` TEXT NOT NULL, PRIMARY KEY(`id`))"
            )
            val columns = "subjects (id, auxiliaryMeanings, searchTarget)"
            connection.execSQL(
                "INSERT INTO $columns VALUES (1, '[{\"meaning\":\"Uno\",\"type\":\"whitelist\"}," +
                    "{\"meaning\":\"First\",\"type\":\"blacklist\"},{\"meaning\":\"Ichi\",\"type\":\"whitelist\"}]', " +
                    "'一 一 one いち')"
            )
            connection.execSQL(
                "INSERT INTO $columns VALUES (2, '[{\"meaning\":\"Decoy\",\"type\":\"blacklist\"}]', '二 two')"
            )
            connection.execSQL("INSERT INTO $columns VALUES (3, '[]', '三 three')")

            migrationFrom(12).migrate(connection)

            val statement = connection.prepare("SELECT searchTarget FROM subjects ORDER BY id")
            val targets = try {
                buildList { while (statement.step()) add(statement.getText(0)) }
            } finally {
                statement.close()
            }
            // Matches what SubjectRepository's buildSearchTarget writes for a freshly synced subject.
            assertThat(targets).containsExactly("一 一 one いち uno ichi", "二 two", "三 three").inOrder()
        }
    }

    @Test
    fun `migration 13 to 14 backfills context sentences and keeps them in step with later writes`() {
        withDatabase { connection ->
            connection.execSQL(
                "CREATE TABLE `subjects` (`id` INTEGER NOT NULL, `level` INTEGER NOT NULL, " +
                    "`contextSentences` TEXT NOT NULL, PRIMARY KEY(`id`))"
            )
            connection.execSQL(
                "INSERT INTO subjects VALUES (1, 3, '[{\"en\":\"I drink Water.\",\"ja\":\"水を飲む。\"}," +
                    "{\"en\":\"Cold.\",\"ja\":\"冷たい。\"}]')"
            )
            connection.execSQL("INSERT INTO subjects VALUES (2, 1, '[]')")

            migrationFrom(13).migrate(connection)

            assertThat(connection.sentenceRows()).containsExactly(
                "1|0|3|水を飲む。|I drink Water.|i drink water.",
                "1|1|3|冷たい。|Cold.|cold."
            ).inOrder()
            assertThat(connection.indexNames("context_sentences"))
                .contains("index_context_sentences_level_subjectId_position")

            // The triggers it installs: a REPLACE swaps the sentences, a delete drops them.
            connection.execSQL("INSERT OR REPLACE INTO subjects VALUES (1, 4, '[{\"en\":\"New.\",\"ja\":\"新。\"}]')")
            connection.execSQL("INSERT INTO subjects VALUES (3, 2, '[{\"en\":\"Three.\",\"ja\":\"三。\"}]')")
            assertThat(connection.sentenceRows()).containsExactly(
                "1|0|4|新。|New.|new.",
                "3|0|2|三。|Three.|three."
            ).inOrder()

            connection.execSQL("DELETE FROM subjects WHERE id = 3")
            assertThat(connection.sentenceRows()).containsExactly("1|0|4|新。|New.|new.")
        }
    }

    /**
     * Room refuses to open a database whose version no migration covers, and the destructive fallback
     * then wipes it — so a gap in this list is a silent full re-download for whoever is on the older
     * version.
     */
    @Test
    fun `the migration list has no version gaps`() {
        val chain = APP_DATABASE_MIGRATIONS.sortedBy { it.startVersion }
            .joinToString(" -> ") { "${it.startVersion}->${it.endVersion}" }

        assertThat(APP_DATABASE_MIGRATIONS.sortedBy { it.startVersion }.zipWithNext().all { (older, newer) ->
            older.endVersion == newer.startVersion
        }).isTrue()
        assertThat(chain).isNotEmpty()
    }

    private fun withDatabase(block: (SQLiteConnection) -> Unit) {
        val connection = AndroidSQLiteDriver().open(":memory:")
        try {
            // Only the objects the migrations touch need to exist: every statement so far is index DDL.
            connection.execSQL(
                "CREATE TABLE `assignments` (`id` INTEGER NOT NULL, `hidden` INTEGER NOT NULL, " +
                    "`availableAt` TEXT, `burnedAt` TEXT, PRIMARY KEY(`id`))"
            )
            block(connection)
        } finally {
            connection.close()
        }
    }

    private fun SQLiteConnection.sentenceRows(): List<String> {
        val statement = prepare(
            "SELECT subjectId || '|' || position || '|' || level || '|' || japanese || '|' || english || '|' || " +
                "englishLower FROM context_sentences ORDER BY subjectId, position"
        )
        return try {
            buildList { while (statement.step()) add(statement.getText(0)) }
        } finally {
            statement.close()
        }
    }

    private fun migrationFrom(startVersion: Int) =
        APP_DATABASE_MIGRATIONS.first { it.startVersion == startVersion }

    private fun SQLiteConnection.indexNames(table: String): Set<String> {
        val statement = prepare("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = ?")
        return try {
            statement.bindText(1, table)
            buildSet { while (statement.step()) add(statement.getText(0).orEmpty()) }
        } finally {
            statement.close()
        }
    }
}
