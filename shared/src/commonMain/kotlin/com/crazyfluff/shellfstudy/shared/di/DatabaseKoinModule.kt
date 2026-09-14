package com.crazyfluff.shellfstudy.shared.di

import androidx.room.RoomDatabase
import com.crazyfluff.shellfstudy.shared.database.AppDatabase
import com.crazyfluff.shellfstudy.shared.database.buildAppDatabase
import com.crazyfluff.shellfstudy.shared.database.friends.FriendsDatabase
import com.crazyfluff.shellfstudy.shared.database.friends.buildFriendsDatabase
import com.crazyfluff.shellfstudy.shared.database.outbox.OutboxDatabase
import com.crazyfluff.shellfstudy.shared.database.outbox.buildOutboxDatabase
import com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDatabase
import com.crazyfluff.shellfstudy.shared.database.studyactivity.buildStudyActivityDatabase
import org.koin.core.module.Module
import org.koin.core.scope.Scope

/**
 * Registers all four Room databases and their DAOs into a Koin [Module] — shared by the Android
 * (`app/di/DatabaseModule.kt`) and iOS (`IosModules.kt`) Koin setups, which previously duplicated
 * this exact set of `single { get<X>().yDao() }` bindings verbatim, differing only in how each
 * platform builds a [RoomDatabase.Builder] (Android needs a `Context`, iOS doesn't).
 *
 * Each builder is passed as a [Scope]-receiver lambda, evaluated lazily inside this function's own
 * `single { }` blocks, rather than as an already-built value — Android's builder construction calls
 * `androidContext()`, which (like any Koin-provided value) is only available inside a Koin
 * definition lambda at resolution time, not as a plain eager expression when the module is built.
 */
fun Module.registerDatabases(
    appDatabaseBuilder: Scope.() -> RoomDatabase.Builder<AppDatabase>,
    studyActivityDatabaseBuilder: Scope.() -> RoomDatabase.Builder<StudyActivityDatabase>,
    outboxDatabaseBuilder: Scope.() -> RoomDatabase.Builder<OutboxDatabase>,
    friendsDatabaseBuilder: Scope.() -> RoomDatabase.Builder<FriendsDatabase>
) {
    single { buildAppDatabase(appDatabaseBuilder()) }
    single { get<AppDatabase>().subjectDao() }
    single { get<AppDatabase>().assignmentDao() }
    single { get<AppDatabase>().srsSystemDao() }
    single { get<AppDatabase>().reviewStatisticDao() }
    single { get<AppDatabase>().levelProgressionDao() }
    single { get<AppDatabase>().syncStateDao() }

    // Not destructive — this is the only local record of study activity and can't be re-fetched
    // from the API (see StudyActivityDatabase's doc comment). Physical file name is unchanged from
    // this database's original review-history-log incarnation, so the Migration(1, 2) that shrinks
    // it runs in place rather than needing a cross-file move.
    single { buildStudyActivityDatabase(studyActivityDatabaseBuilder()) }
    single { get<StudyActivityDatabase>().studyActivityDao() }

    // Not destructive — pending mutations must survive a schema bump (see OutboxDatabase's doc
    // comment). Version 1 has no back-compat burden yet, so no Migration is needed.
    single { buildOutboxDatabase(outboxDatabaseBuilder()) }
    single { get<OutboxDatabase>().outboxDao() }

    // Pure cache — always re-fetchable from the WaniKani API.
    single { buildFriendsDatabase(friendsDatabaseBuilder()) }
    single { get<FriendsDatabase>().friendStatsDao() }
}
