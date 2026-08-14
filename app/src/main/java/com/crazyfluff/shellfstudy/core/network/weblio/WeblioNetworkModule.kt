package com.crazyfluff.shellfstudy.core.network.weblio

import com.crazyfluff.shellfstudy.shared.network.weblio.KtorWeblioApi
import com.crazyfluff.shellfstudy.shared.network.weblio.WeblioApi
import com.crazyfluff.shellfstudy.shared.network.weblio.createWeblioHttpClient
import org.koin.dsl.module

val weblioNetworkModule = module {
    single<WeblioApi> { KtorWeblioApi(createWeblioHttpClient()) }
}
