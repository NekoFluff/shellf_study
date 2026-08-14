package com.crazyfluff.shellfstudy.core.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Qualifier for the app-wide [CoroutineScope] registered below — Koin has no annotation-based
 *  qualifier system, so call sites request it explicitly via `get(qualifier = APPLICATION_SCOPE)`. */
val APPLICATION_SCOPE = named("applicationScope")

/** A scope that outlives any single screen's ViewModel, for durability-critical writes that must
 *  not be cancelled just because the user navigated away mid-write — see its usage in
 *  [com.crazyfluff.shellfstudy.feature.review.ReviewViewModel] and
 *  [com.crazyfluff.shellfstudy.feature.lesson.LessonViewModel]. */
val coroutineScopeModule = module {
    single(APPLICATION_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
}
