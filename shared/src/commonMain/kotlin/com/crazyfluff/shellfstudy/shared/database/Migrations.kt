package com.crazyfluff.shellfstudy.shared.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Adds the dashboard's filtered indexes — see [AssignmentEntity]'s KDoc for why each one is declared.
 *
 * Pure DDL: no table is rewritten and no row is touched, so this is safe to run on the existing
 * ~27 MB database. SQLite builds each index by scanning the table once, which happens inside the
 * migration transaction on first open after the upgrade.
 */
private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_assignments_hidden_startedAt` ON `assignments` (`hidden`, `startedAt`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_assignments_hidden_availableAt` ON `assignments` (`hidden`, `availableAt`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_assignments_hidden_srsStage_subjectType` ON `assignments` (`hidden`, `srsStage`, `subjectType`)")
    }
}

/**
 * Swaps one index for another, both of them assignment-table indices described in [AssignmentEntity]'s
 * KDoc: `(hidden, burnedAt)` for the self-stats query that had no index at all, and the removal of the
 * bare `availableAt` index, which every one of its queries superseded with `(hidden, availableAt)`.
 *
 * Like the migration before it, pure DDL — no table rewritten and no row touched. Dropping an index
 * only marks its pages free, so this is cheap in both directions.
 */
private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_assignments_hidden_burnedAt` ON `assignments` (`hidden`, `burnedAt`)")
        connection.execSQL("DROP INDEX IF EXISTS `index_assignments_availableAt`")
    }
}

/**
 * Adds the `study_materials` table, the user's own meaning synonyms. A new, empty table: the next
 * sync finds no cursor for it and fetches every study material in full.
 */
private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `study_materials` (`id` INTEGER NOT NULL, `subjectId` INTEGER NOT NULL, " +
                "`meaningSynonyms` TEXT NOT NULL, `hidden` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_study_materials_subjectId` ON `study_materials` (`subjectId`)"
        )
    }
}

/**
 * Makes WaniKani's whitelisted auxiliary meanings searchable for subjects synced before they were:
 * appends them to `searchTarget`, the column the search box matches. Without it they only land as each
 * subject is next updated upstream, since the subject sync is incremental. A data-only migration — no
 * schema change — reading the `auxiliaryMeanings` JSON column (`[{"meaning":…,"type":…}]`) in SQL, so
 * the library isn't re-downloaded. `lower` only folds ASCII, which is all an English meaning needs.
 *
 * Not re-runnable like the DDL ones: a second pass would append the meanings again. Room runs each
 * migration once, inside the version bump's transaction.
 */
private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "UPDATE subjects SET searchTarget = searchTarget || (" +
                "SELECT ' ' || group_concat(lower(json_extract(value, '$.meaning')), ' ') " +
                "FROM json_each(subjects.auxiliaryMeanings) WHERE json_extract(value, '$.type') = 'whitelist') " +
                "WHERE EXISTS (SELECT 1 FROM json_each(subjects.auxiliaryMeanings) " +
                "WHERE json_extract(value, '$.type') = 'whitelist')"
        )
    }
}

/**
 * Schema migrations for [AppDatabase], newest last.
 *
 * [buildAppDatabase] still falls back to a destructive migration, but that fallback is a safety net
 * for an unexpected version jump — not the normal path. Without a real migration here, every schema
 * change would drop the local mirror and force a full re-download of the user's entire subject
 * library (~9.5k subjects, ~40 MB of JSON at level 30) plus every assignment on the next launch,
 * which is a far larger cost than the schema change it was made for.
 *
 * Migrations are additive and data-preserving, and each one is asserted by
 * `AppDatabaseMigrationTest` — a silently-dropped table would be invisible until the user notices
 * missing cards, and the destructive fallback means a wrong migration wipes the cache instead of
 * failing. The list is public rather than internal only so that test, which lives in `:app` because
 * that is where an Android-side SQLite driver can run on the JVM, can apply the real list.
 */
val APP_DATABASE_MIGRATIONS: Array<Migration> =
    arrayOf(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
