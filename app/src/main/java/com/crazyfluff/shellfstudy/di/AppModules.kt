package com.crazyfluff.shellfstudy.di

import com.crazyfluff.shellfstudy.core.audio.audioModule
import com.crazyfluff.shellfstudy.core.data.dataStoreModule
import com.crazyfluff.shellfstudy.core.database.databaseModule
import com.crazyfluff.shellfstudy.core.notifications.notificationModule
import com.crazyfluff.shellfstudy.core.sync.syncModule
import com.crazyfluff.shellfstudy.shared.di.sharedAppModules

/**
 * The shared graph plus Android's beans. Anything both platforms need lives in [sharedAppModules],
 * so this list and iOS's cannot drift apart in the shared half — the only difference between the two
 * is what is genuinely platform-specific, which is what a reader should see when comparing them.
 */
val appModules = sharedAppModules + listOf(
    databaseModule,
    dataStoreModule,
    audioModule,
    notificationModule,
    syncModule,
    workerModule,
)
