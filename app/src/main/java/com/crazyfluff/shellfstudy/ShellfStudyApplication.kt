package com.crazyfluff.shellfstudy

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.crazyfluff.shellfstudy.core.coroutines.ApplicationScope
import com.crazyfluff.shellfstudy.core.data.strokeorder.StrokeOrderRepository
import com.crazyfluff.shellfstudy.core.notifications.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class ShellfStudyApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var strokeOrderRepository: StrokeOrderRepository
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        // Parses the ~8MB bundled stroke-order dictionary ahead of the first subject detail sheet
        // open, so that open doesn't pay a ~600ms parse cost inline (see StrokeOrderRepository).
        applicationScope.launch { strokeOrderRepository.preload() }
    }
}
