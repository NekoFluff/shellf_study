package com.crazyfluff.shellfstudy.core.data.strokeorder

import com.crazyfluff.shellfstudy.shared.data.StrokeOrderRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

val strokeOrderModule = module {
    single { AndroidStrokeOrderRepository(androidContext()) } bind StrokeOrderRepository::class
}
