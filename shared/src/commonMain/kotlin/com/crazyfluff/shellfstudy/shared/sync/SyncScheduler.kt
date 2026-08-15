package com.crazyfluff.shellfstudy.shared.sync

interface SyncScheduler {
    fun schedulePeriodicSync()
    fun cancelPeriodicSync()
}
