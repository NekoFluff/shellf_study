package com.crazyfluff.shellfstudy.di

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkerParameters
import com.crazyfluff.shellfstudy.core.audio.audioModule
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.di.sharedAppModules
import com.crazyfluff.shellfstudy.shared.session.PersistedSessionStore
import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.HttpClientEngine
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.koin.test.verify.verify

/**
 * Static graph verification: confirms every constructor dependency in the modules the app actually
 * installs has a registered provider, catching "forgot to wire X" without starting a real Koin
 * context.
 *
 * The modules under test are [appModules] itself rather than a hand-written list, and that distinction
 * is the point: a list copied into the test drifts from the one the app installs, and the test keeps
 * passing while the real graph breaks. It also means the shared half — [sharedAppModules], which iOS
 * installs too — is covered here, where the previous version checked only a hand-picked subset.
 *
 * One module is still excluded: [audioModule]. ExoPlayer uses a builder pattern — `SimpleCache(File,
 * CacheEvictor)` and `ExoPlayer.Builder` take parameters that are not Koin bindings, so static analysis
 * reports them as missing. Audio wiring is exercised in integration instead.
 */
@RunWith(AndroidJUnit4::class)
class AppModulesVerificationTest {

    @Test
    fun `every module the app installs is fully connected`() {
        // verifyAll() runs each module in isolation, so cross-module deps aren't visible. A single
        // wrapper module that includes all the modules under test gives the verifier the full graph.
        val allModules = module {
            appModules.filterNot { it === audioModule }.forEach { includes(it) }
        }
        allModules.verify(
            extraTypes = listOf(
                // Provided at runtime via androidContext() — declared as external so the verifier
                // treats it as always-available rather than a missing binding.
                Context::class,
                // Supplied by KoinWorkerFactory from its own arguments rather than by a definition —
                // see workerModule's comment. This is the last unresolved parameter workerModule has,
                // now that OutboxDrainer is a registered bean instead of being built inline: the
                // module used to be excluded from this test for exactly that reason.
                WorkerParameters::class,
                // Declared by the excluded audioModule above; naming it here still lets the verifier
                // confirm the ViewModels that inject it have a complete graph.
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

    /**
     * The shared half has to be a prefix, not merely a subset: that is what makes it structurally
     * impossible for a module to be installed on one platform and missing from the other.
     */
    @Test
    fun `the shared modules lead the app's list`() {
        assertThat(appModules.take(sharedAppModules.size)).isEqualTo(sharedAppModules)
    }
}
