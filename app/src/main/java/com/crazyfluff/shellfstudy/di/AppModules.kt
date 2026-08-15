package com.crazyfluff.shellfstudy.di

import com.crazyfluff.shellfstudy.core.audio.audioModule
import com.crazyfluff.shellfstudy.core.data.dataStoreModule
import com.crazyfluff.shellfstudy.core.database.databaseModule
import com.crazyfluff.shellfstudy.core.notifications.notificationModule
import com.crazyfluff.shellfstudy.core.sync.syncModule
import com.crazyfluff.shellfstudy.shared.di.appForegroundTrackerModule
import com.crazyfluff.shellfstudy.shared.di.coroutineScopeModule
import com.crazyfluff.shellfstudy.shared.di.networkModule
import com.crazyfluff.shellfstudy.shared.di.repositoryModule
import com.crazyfluff.shellfstudy.shared.di.strokeOrderModule
import com.crazyfluff.shellfstudy.shared.di.viewModelModule
import com.crazyfluff.shellfstudy.shared.di.weblioNetworkModule

val appModules = listOf(
    networkModule,
    weblioNetworkModule,
    databaseModule,
    dataStoreModule,
    repositoryModule,
    strokeOrderModule,
    audioModule,
    coroutineScopeModule,
    appForegroundTrackerModule,
    notificationModule,
    syncModule,
    viewModelModule,
    workerModule
)
