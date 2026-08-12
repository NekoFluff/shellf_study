package com.crazyfluff.shellfstudy.core.coroutines

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class ApplicationScope

/** A scope that outlives any single screen's ViewModel, for durability-critical writes that must
 *  not be cancelled just because the user navigated away mid-write — see [ApplicationScope]'s
 *  usage in [com.crazyfluff.shellfstudy.feature.review.ReviewViewModel] and
 *  [com.crazyfluff.shellfstudy.feature.lesson.LessonViewModel]. */
@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
