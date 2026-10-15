package com.crazyfluff.shellfstudy

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.WorkerFactory
import com.crazyfluff.shellfstudy.shared.lifecycle.AppForegroundTracker
import com.crazyfluff.shellfstudy.core.notifications.AndroidNotificationChannels
import com.crazyfluff.shellfstudy.di.appModules
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class ShellfStudyApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(get<WorkerFactory>()).build()

    override fun onCreate() {
        super.onCreate()
        // Robolectric constructs a fresh Application (and re-runs onCreate) per test, but Koin's
        // GlobalContext is a process-wide singleton that outlives any one test — without this
        // guard, the second Robolectric-backed screen test in the same test task run would crash
        // with KoinApplicationAlreadyStartedException.
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(this@ShellfStudyApplication)
                workManagerFactory()
                modules(appModules)
            }
        }

        AndroidNotificationChannels.ensureCreated(this)
        val appForegroundTracker: AppForegroundTracker = get()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appForegroundTracker)
        // The bundled dictionaries are preloaded from MainActivity, not here: this runs for every
        // process start, including the many that only exist to run a background worker.
    }
}
