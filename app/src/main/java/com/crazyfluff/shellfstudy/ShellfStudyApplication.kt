package com.crazyfluff.shellfstudy

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import com.crazyfluff.shellfstudy.core.coroutines.ApplicationScope
import com.crazyfluff.shellfstudy.shared.data.StrokeOrderRepository
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.SvgCssVariableInterceptor
import com.crazyfluff.shellfstudy.core.lifecycle.AppForegroundTracker
import com.crazyfluff.shellfstudy.core.notifications.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@HiltAndroidApp
class ShellfStudyApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var strokeOrderRepository: StrokeOrderRepository
    @Inject lateinit var appForegroundTracker: AppForegroundTracker
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    // Radicals with no Unicode glyph (e.g. "Death Star") render via character_images, which the
    // WaniKani API only ever supplies as SVG — the decoder must be registered explicitly here.
    // A dedicated OkHttpClient (rather than the default one coil-network-okhttp would otherwise
    // use) carries SvgCssVariableInterceptor, which works around AndroidSVG's lack of CSS var()
    // support — see that class for why these SVGs render blank without it.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { svgOkHttpClient }))
                add(SvgDecoder.Factory())
            }
            .build()

    private val svgOkHttpClient by lazy {
        OkHttpClient.Builder().addInterceptor(SvgCssVariableInterceptor).build()
    }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(appForegroundTracker)
        // Parses the ~8MB bundled stroke-order dictionary ahead of the first subject detail sheet
        // open, so that open doesn't pay a ~600ms parse cost inline (see StrokeOrderRepository).
        applicationScope.launch { strokeOrderRepository.preload() }
    }
}
