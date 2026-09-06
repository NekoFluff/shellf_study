package com.crazyfluff.shellfstudy.di

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.session.PersistedSessionStore
import io.ktor.client.engine.HttpClientEngine
import com.crazyfluff.shellfstudy.shared.di.appForegroundTrackerModule
import com.crazyfluff.shellfstudy.shared.di.coroutineScopeModule
import com.crazyfluff.shellfstudy.shared.di.networkModule
import com.crazyfluff.shellfstudy.shared.di.repositoryModule
import com.crazyfluff.shellfstudy.shared.di.strokeOrderModule
import com.crazyfluff.shellfstudy.shared.di.viewModelModule
import com.crazyfluff.shellfstudy.shared.di.weblioNetworkModule
import com.crazyfluff.shellfstudy.core.data.dataStoreModule
import com.crazyfluff.shellfstudy.core.database.databaseModule
import com.crazyfluff.shellfstudy.core.notifications.notificationModule
import com.crazyfluff.shellfstudy.core.sync.syncModule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.koin.test.verify.verify
import org.robolectric.annotation.Config

/**
 * Static graph verification: confirms every constructor dependency across the core modules has a
 * registered provider, catching "forgot to wire X" bugs without starting a real Koin context.
 *
 * Two modules are intentionally excluded:
 * - [audioModule]: ExoPlayer uses a builder pattern — the `SimpleCache` and `ExoPlayer` constructors
 *   have parameters (File, CacheEvictor, Builder internals) that aren't Koin bindings, so static
 *   analysis would produce false negatives. Audio wiring is exercised in integration instead.
 * - [workerModule]: Worker factories construct [OutboxDrainer] inline (rather than via Koin) to
 *   avoid a context-chain issue with Koin 4.x's KoinWorkerFactory. Worker correctness is covered
 *   by the dedicated SyncWorkerTest / OutboxSyncWorkerTest suites.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AppModulesVerificationTest {

    @Test
    fun `all non-worker non-audio module bindings are fully connected`() {
        // verifyAll() runs each module in isolation, so cross-module deps aren't visible. A single
        // wrapper module that includes all the modules under test gives the verifier the full graph.
        val allModules = module {
            includes(
                networkModule,
                weblioNetworkModule,
                databaseModule,
                dataStoreModule,
                repositoryModule,
                strokeOrderModule,
                coroutineScopeModule,
                appForegroundTrackerModule,
                syncModule,
                notificationModule,
                viewModelModule,
            )
        }
        allModules.verify(
            extraTypes = listOf(
                // Provided at runtime via androidContext() — declared as external so the verifier
                // treats it as always-available rather than a missing binding.
                Context::class,
                // PronunciationAudioPlayer comes from audioModule, which is excluded above.
                // Declaring it as extra lets the verifier confirm the ViewModels that inject it
                // still have a complete graph (the binding itself is wired, just not verified here).
                PronunciationAudioPlayer::class,
                // Ktor's HttpClient constructor takes HttpClientEngine internally; the actual engine
                // (OkHttp) is supplied at construction time by createWaniKaniHttpClient(), not via Koin.
                HttpClientEngine::class,
                // LessonSessionController's/ReviewSessionController's constructor takes the generic
                // PersistedSessionStore<T> interface, but the verifier's reflection only sees the
                // erased raw interface — the real dependency is supplied by the explicitly-typed
                // get<LessonSessionRepository>()/get<ReviewSessionRepository>() calls in each
                // controller registration in repositoryModule, which the verifier can't see since it
                // doesn't evaluate lambda bodies. Each feature is its own concrete controller
                // subclass (never two QuizSessionController<T> generics — Koin indexes by the erased
                // class, so those would collide), and that resolution is exercised end-to-end in
                // SessionControllerDiTest.
                PersistedSessionStore::class,
            )
        )
    }
}
