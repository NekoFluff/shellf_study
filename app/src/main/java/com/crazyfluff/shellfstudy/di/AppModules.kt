package com.crazyfluff.shellfstudy.di

import com.crazyfluff.shellfstudy.core.audio.audioModule
import com.crazyfluff.shellfstudy.core.coroutines.coroutineScopeModule
import com.crazyfluff.shellfstudy.core.data.dataStoreModule
import com.crazyfluff.shellfstudy.core.data.repositoryModule
import com.crazyfluff.shellfstudy.core.data.strokeorder.strokeOrderModule
import com.crazyfluff.shellfstudy.core.database.databaseModule
import com.crazyfluff.shellfstudy.core.lifecycle.appForegroundTrackerModule
import com.crazyfluff.shellfstudy.core.network.networkModule
import com.crazyfluff.shellfstudy.core.network.weblio.weblioNetworkModule
import com.crazyfluff.shellfstudy.core.notifications.notificationModule
import com.crazyfluff.shellfstudy.core.sync.syncModule

/** Every Koin module the app needs, passed to `startKoin { modules(appModules) }` in
 *  ShellfStudyApplication. [viewModelModule] and [workerModule] are kept separate above since
 *  they're conceptually different registration kinds (`viewModel { }`/`worker { }` vs `single { }`). */
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
