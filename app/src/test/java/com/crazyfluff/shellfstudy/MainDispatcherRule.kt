package com.crazyfluff.shellfstudy

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Points Dispatchers.Main at a test dispatcher so ViewModel coroutines run synchronously in tests. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        // Drain anything still queued on this dispatcher (e.g. the cancellation of a ViewModel's
        // viewModelScope collectors triggered by a test's own tearDown()) before handing
        // Dispatchers.Main back. Left undrained, that cancellation can complete asynchronously
        // after this test has ended, landing an uncaught exception on kotlinx-coroutines-test's
        // process-wide ExceptionCollector, which then surfaces as `UncaughtExceptionsBeforeTest`
        // in whichever test happens to call runTest next.
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }
}
