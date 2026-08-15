package com.crazyfluff.shellfstudy.shared.sync

interface PitchAccentScrapeScheduler {
    fun schedulePeriodicScrape()
    fun cancelPeriodicScrape()
}
