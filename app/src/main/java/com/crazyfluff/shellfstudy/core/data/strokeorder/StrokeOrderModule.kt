package com.crazyfluff.shellfstudy.core.data.strokeorder

import com.crazyfluff.shellfstudy.shared.data.StrokeOrderRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StrokeOrderModule {
    @Binds
    @Singleton
    abstract fun bindStrokeOrderRepository(impl: AndroidStrokeOrderRepository): StrokeOrderRepository
}
