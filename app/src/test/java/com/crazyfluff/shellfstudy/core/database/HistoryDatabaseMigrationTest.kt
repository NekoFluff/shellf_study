package com.crazyfluff.shellfstudy.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.database.studyactivity.STUDY_ACTIVITY_MIGRATION_1_2
import com.crazyfluff.shellfstudy.shared.database.studyactivity.STUDY_ACTIVITY_MIGRATION_2_3
import com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDatabase
import com.crazyfluff.shellfstudy.shared.database.studytime.STUDY_TIME_MIGRATION_1_2
import com.crazyfluff.shellfstudy.shared.database.studytime.StudyTimeDatabase
import com.crazyfluff.shellfstudy.shared.database.studytime.StudyTimeSegmentEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Upgrades the two history databases — the only local copy of study time and study days, which the
 * WaniKani API can't give back — through their history-sync migrations, opening the result with Room
 * itself so its post-migration schema validation runs too. A migration that leaves the schema off by
 * one default value or index fails that validation, and since these databases have no destructive
 * fallback, it would fail on every launch rather than quietly wiping.
 *
 * Each old database is written with the exact DDL its exported schema JSON records, so this is the
 * file a real install would be upgrading.
 */
@RunWith(AndroidJUnit4::class)
class HistoryDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun freshFile(name: String): File = context.getDatabasePath(name).also {
        it.parentFile?.mkdirs()
        it.delete()
    }

    @Test
    fun `study time 1 to 2 keeps every stretch and gives each its own sync id, not yet uploaded`() = runTest {
        val file = freshFile("study_time_migration_test.db")
        AndroidSQLiteDriver().open(file.path).apply {
            execSQL(
                "CREATE TABLE IF NOT EXISTS `study_time_segments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`kind` TEXT NOT NULL, `startedAtMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                    "`level` INTEGER, `itemsAnswered` INTEGER NOT NULL)"
            )
            execSQL(
                "CREATE INDEX IF NOT EXISTS `index_study_time_segments_startedAtMs` " +
                    "ON `study_time_segments` (`startedAtMs`)"
            )
            val insert = "INSERT INTO study_time_segments (kind, startedAtMs, durationMs, level, itemsAnswered) VALUES"
            execSQL("$insert ('REVIEW', 1000, 60000, 5, 12)")
            execSQL("$insert ('LESSON', 2000, 30000, NULL, 3)")
            execSQL("PRAGMA user_version = 1")
            close()
        }

        val database = Room.databaseBuilder(context, StudyTimeDatabase::class.java, file.path)
            .setDriver(AndroidSQLiteDriver())
            .addMigrations(STUDY_TIME_MIGRATION_1_2)
            .build()
        try {
            val dao = database.studyTimeDao()
            val rows = dao.observeAll().first()

            assertThat(rows.map { it.kind to it.durationMs })
                .containsExactly("REVIEW" to 60_000L, "LESSON" to 30_000L)
                .inOrder()
            assertThat(rows.map { it.syncId }.toSet()).hasSize(2)
            assertThat(rows.all { it.syncId.matches(Regex("[0-9a-f]{32}")) }).isTrue()
            assertThat(rows.none { it.uploaded }).isTrue()
            assertThat(dao.getNotUploaded(10)).hasSize(2)

            // Rows recorded after the upgrade get their sync id from the entity, and the unique index holds.
            dao.insert(
                StudyTimeSegmentEntity(
                    kind = "REVIEW", startedAtMs = 3000, durationMs = 1000, level = 5, itemsAnswered = 1
                )
            )
            dao.markUploaded(listOf(rows.first().syncId))
            assertThat(dao.getNotUploaded(10)).hasSize(2)
            dao.insertFromRemote(listOf(rows.first().copy(id = 0, uploaded = true)))
            assertThat(dao.observeAll().first()).hasSize(3)
        } finally {
            database.close()
        }
    }

    @Test
    fun `study activity 2 to 3 keeps every day and marks each not yet uploaded`() = runTest {
        val file = freshFile("study_activity_migration_test.db")
        AndroidSQLiteDriver().open(file.path).apply {
            execSQL("CREATE TABLE IF NOT EXISTS `study_activity_days` (`date` TEXT NOT NULL, PRIMARY KEY(`date`))")
            execSQL("INSERT INTO study_activity_days (date) VALUES ('2026-10-27'), ('2026-10-28')")
            execSQL("PRAGMA user_version = 2")
            close()
        }

        val database = Room.databaseBuilder(context, StudyActivityDatabase::class.java, file.path)
            .setDriver(AndroidSQLiteDriver())
            .addMigrations(STUDY_ACTIVITY_MIGRATION_1_2, STUDY_ACTIVITY_MIGRATION_2_3)
            .build()
        try {
            val dao = database.studyActivityDao()

            assertThat(dao.observeActiveDays().first()).containsExactly("2026-10-28", "2026-10-27").inOrder()
            assertThat(dao.getNotUploaded(10).map { it.date }).containsExactly("2026-10-27", "2026-10-28").inOrder()

            dao.markUploaded(listOf("2026-10-27"))
            assertThat(dao.getNotUploaded(10).map { it.date }).containsExactly("2026-10-28")
        } finally {
            database.close()
        }
    }
}
