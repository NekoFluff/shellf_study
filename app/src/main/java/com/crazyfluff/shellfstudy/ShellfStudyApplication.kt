package com.crazyfluff.shellfstudy

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.WorkerFactory
import com.crazyfluff.shellfstudy.shared.di.APPLICATION_SCOPE
import com.crazyfluff.shellfstudy.shared.lifecycle.AppForegroundTracker
import com.crazyfluff.shellfstudy.core.notifications.AndroidNotificationChannels
import com.crazyfluff.shellfstudy.di.appModules
import com.crazyfluff.shellfstudy.shared.data.PitchAccentBundledSource
import com.crazyfluff.shellfstudy.shared.data.StrokeOrderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
        // Parses the ~8MB bundled stroke-order dictionary ahead of the first subject detail sheet
        // open, so that open doesn't pay a ~600ms parse cost inline (see StrokeOrderRepository).
        val applicationScope: CoroutineScope = get(APPLICATION_SCOPE)
        val strokeOrderRepository: StrokeOrderRepository = get()
        applicationScope.launch { strokeOrderRepository.preload() }
        // Parses the ~1MB bundled pitch-accent dictionary ahead of the first reading answer, so
        // grading that answer doesn't pay the parse cost inline (see PitchAccentBundledSource).
        val pitchAccentBundledSource: PitchAccentBundledSource = get()
        applicationScope.launch { pitchAccentBundledSource.preload() }
    }
}
