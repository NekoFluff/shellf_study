package com.crazyfluff.shellfstudy.fakes

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder

/**
 * Builds a worker of type [W] with its collaborators supplied directly, bypassing Koin.
 *
 * Every worker test needs the same ceremony — a [TestListenableWorkerBuilder] plus a [WorkerFactory]
 * that hands the worker real dependencies — and five of them wrote it out in full. The worker itself
 * is what differs, so that is all the caller provides.
 */
inline fun <reified W : ListenableWorker> buildTestWorker(
    context: Context,
    inputData: Data? = null,
    noinline create: (appContext: Context, params: WorkerParameters) -> W,
): W {
    val builder = TestListenableWorkerBuilder<W>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = create(appContext, workerParameters)
        })
    inputData?.let { builder.setInputData(it) }
    return builder.build()
}
