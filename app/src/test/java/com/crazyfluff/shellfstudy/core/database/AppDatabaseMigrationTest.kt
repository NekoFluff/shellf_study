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
