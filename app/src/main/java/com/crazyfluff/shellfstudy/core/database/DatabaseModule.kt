package com.crazyfluff.shellfstudy.core.database

import com.crazyfluff.shellfstudy.shared.database.AppDatabase
import com.crazyfluff.shellfstudy.shared.database.buildAppDatabase
import com.crazyfluff.shellfstudy.shared.database.getAppDatabaseBuilder
import com.crazyfluff.shellfstudy.shared.database.outbox.OutboxDatabase
import com.crazyfluff.shellfstudy.shared.database.outbox.buildOutboxDatabase
import com.crazyfluff.shellfstudy.shared.database.outbox.getOutboxDatabaseBuilder
import com.crazyfluff.shellfstudy.shared.database.pitchaccent.PitchAccentDatabase
import com.crazyfluff.shellfstudy.shared.database.pitchaccent.buildPitchAccentDatabase
import com.crazyfluff.shellfstudy.shared.database.pitchaccent.getPitchAccentDatabaseBuilder
import com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDatabase
import com.crazyfluff.shellfstudy.shared.database.studyactivity.buildStudyActivityDatabase
import com.crazyfluff.shellfstudy.shared.database.studyactivity.getStudyActivityDatabaseBuilder
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    single { buildAppDatabase(getAppDatabaseBuilder(androidContext())) }
    single { get<AppDatabase>().subjectDao() }
    single { get<AppDatabase>().assignmentDao() }
    single { get<AppDatabase>().srsSystemDao() }
    single { get<AppDatabase>().reviewStatisticDao() }
    single { get<AppDatabase>().studyMaterialDao() }
    single { get<AppDatabase>().levelProgressionDao() }
    single { get<AppDatabase>().syncStateDao() }

    // Not destructive — this is the only local record of study activity and can't be re-fetched
    // from the API (see StudyActivityDatabase's doc comment). Physical file name is unchanged from
    // this database's original review-history-log incarnation, so the Migration(1, 2) that shrinks
    // it runs in place rather than needing a cross-file move.
    single { buildStudyActivityDatabase(getStudyActivityDatabaseBuilder(androidContext())) }
    single { get<StudyActivityDatabase>().studyActivityDao() }

    // Not destructive — pending mutations must survive a schema bump (see OutboxDatabase's doc
    // comment). Version 1 has no back-compat burden yet, so no Migration is needed.
    single { buildOutboxDatabase(getOutboxDatabaseBuilder(androidContext())) }
    single { get<OutboxDatabase>().outboxDao() }

    // Re-derivable by re-scraping weblio.jp (see PitchAccentDatabase's doc comment), but a
    // destructive migration would force needless re-scraping, so this uses normal migrations.
    single { buildPitchAccentDatabase(getPitchAccentDatabaseBuilder(androidContext())) }
    single { get<PitchAccentDatabase>().pitchAccentCacheDao() }
}
