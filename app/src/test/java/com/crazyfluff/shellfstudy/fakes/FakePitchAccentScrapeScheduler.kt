package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.core.sync.PitchAccentScrapeScheduler

/** No-op stand-in for [PitchAccentScrapeScheduler] — the real one needs WorkManager/a real Context to run. */
class FakePitchAccentScrapeScheduler : PitchAccentScrapeScheduler {
    var scheduleCallCount = 0
        private set
    var cancelCallCount = 0
        private set

    override fun schedulePeriodicScrape() {
        scheduleCallCount++
    }

    override fun cancelPeriodicScrape() {
        cancelCallCount++
    }
}
