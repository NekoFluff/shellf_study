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
 * Schema migrations for [AppDatabase], newest last.
 *
 * [buildAppDatabase] still falls back to a destructive migration, but that fallback is a safety net
 * for an unexpected version jump — not the normal path. Without a real migration here, every schema
 * change would drop the local mirror and force a full re-download of the user's entire subject
 * library (~9.5k subjects, ~40 MB of JSON at level 30) plus every assignment on the next launch,
 * which is a far larger cost than the schema change it was made for.
 *
 * Migrations are additive and data-preserving; assert that with `MigrationTestHelper` when one is
 * added, since a silently-dropped table would be invisible until the user notices missing cards.
 */
internal val APP_DATABASE_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_9_10)
