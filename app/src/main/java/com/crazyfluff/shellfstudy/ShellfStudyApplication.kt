package com.crazyfluff.shellfstudy

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.WorkerFactory
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import com.crazyfluff.shellfstudy.core.coroutines.APPLICATION_SCOPE
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.SvgCssVariableInterceptor
import com.crazyfluff.shellfstudy.core.lifecycle.AppForegroundTracker
import com.crazyfluff.shellfstudy.core.notifications.AndroidNotificationChannels
import com.crazyfluff.shellfstudy.di.appModules
import com.crazyfluff.shellfstudy.shared.data.StrokeOrderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class ShellfStudyApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(get<WorkerFactory>()).build()

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
    }
}
