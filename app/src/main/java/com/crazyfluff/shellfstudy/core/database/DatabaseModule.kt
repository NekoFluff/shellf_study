package com.crazyfluff.shellfstudy.core.database

import com.crazyfluff.shellfstudy.shared.database.getAppDatabaseBuilder
import com.crazyfluff.shellfstudy.shared.database.friends.getFriendsDatabaseBuilder
import com.crazyfluff.shellfstudy.shared.database.outbox.getOutboxDatabaseBuilder
import com.crazyfluff.shellfstudy.shared.database.studyactivity.getStudyActivityDatabaseBuilder
import com.crazyfluff.shellfstudy.shared.di.registerDatabases
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    registerDatabases(
        appDatabaseBuilder = { getAppDatabaseBuilder(androidContext()) },
        studyActivityDatabaseBuilder = { getStudyActivityDatabaseBuilder(androidContext()) },
        outboxDatabaseBuilder = { getOutboxDatabaseBuilder(androidContext()) },
        friendsDatabaseBuilder = { getFriendsDatabaseBuilder(androidContext()) }
    )
}
