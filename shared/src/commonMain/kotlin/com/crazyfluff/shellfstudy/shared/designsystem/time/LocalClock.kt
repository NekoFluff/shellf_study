package com.crazyfluff.shellfstudy.shared.designsystem.time

import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.time.Clock

/**
 * The source of "now" for composables.
 *
 * A composable has no constructor and no injected dependency, so a `Clock.System.now()` read inside
 * one is unreachable from a test: `SubjectStatsSection`'s "Available now" was decided at render time
 * from wall-clock, which meant no test could pin which side of the boundary a review sat on. This is
 * the only way to hand a composable a time source.
 *
 * It is *not* provided by `ShellfStudyApp`, unlike [com.crazyfluff.shellfstudy.shared.designsystem.settings.LocalDisplaySettings]:
 * the default below is the production value, so there is nothing for the app to override and a
 * provider at the root would be ceremony. Tests provide their own, and a composable composed with no
 * provider at all still reads the real clock.
 *
 * Note this does not cover ViewModels, which read [Clock.System] directly (nine call sites) and stay
 * on real time in tests. Injecting a clock into each of those is a larger change than adding this
 * seam was, so composable time is pinned and ViewModel time is not.
 */
val LocalClock = staticCompositionLocalOf<Clock> { Clock.System }
