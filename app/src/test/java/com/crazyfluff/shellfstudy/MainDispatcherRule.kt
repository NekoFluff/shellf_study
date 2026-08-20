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
        settleRealThreadHandoffs()
        Dispatchers.resetMain()
    }

    /**
     * Real worker threads can still be resuming coroutines when the test body ends: Ktor's OkHttp
     * engine delivers responses on Dispatchers.IO, and DataStore performs its writes on its own
     * actor threads. If such a handoff lands after [Dispatchers.resetMain] below, the continuation
     * resumes onto a Main dispatcher that has already been torn down and dies with the fatal
     * `CompletedContinuation cannot be cast to DispatchedContinuation` ClassCastException in
     * `CoroutineDispatcher.releaseInterceptedContinuation` (the kotlinx.coroutines
     * unconfined-test-dispatcher race, see Kotlin/kotlinx.coroutines#3773/#3493) — the exception
     * then surfaces in whichever test happens to run next.
     *
     * Give in-flight handoffs a bounded chance to land *while this test's dispatcher is still
     * installed*, then drain whatever they dispatched onto the test scheduler. 2 x 25ms is plenty
     * for localhost MockWebServer round-trips without noticeably slowing the suite.
     */
    private fun settleRealThreadHandoffs() {
        repeat(2) {
            Thread.sleep(25)
            dispatcher.scheduler.advanceUntilIdle()
        }
    }
}
