package com.crazyfluff.shellfstudy.core.data.strokeorder

import com.crazyfluff.shellfstudy.shared.data.StrokeOrderRepository
import com.crazyfluff.shellfstudy.shared.data.strokeorder.CmpStrokeOrderRepository
import org.koin.dsl.bind
import org.koin.dsl.module

val strokeOrderModule = module {
    single { CmpStrokeOrderRepository() } bind StrokeOrderRepository::class
}
