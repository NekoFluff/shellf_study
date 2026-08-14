package com.crazyfluff.shellfstudy.core.network.weblio

import com.crazyfluff.shellfstudy.shared.network.weblio.KtorWeblioApi
import com.crazyfluff.shellfstudy.shared.network.weblio.WeblioApi
import com.crazyfluff.shellfstudy.shared.network.weblio.createWeblioHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WeblioNetworkModule {

    @Provides
    @Singleton
    fun provideWeblioApi(): WeblioApi = KtorWeblioApi(createWeblioHttpClient())
}
