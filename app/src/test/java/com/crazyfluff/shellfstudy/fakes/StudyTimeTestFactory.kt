package com.crazyfluff.shellfstudy.fakes

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.crazyfluff.shellfstudy.shared.data.DashboardCacheRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Clock

/**
 * A [StudyTimeRepository] over [dao], for tests.
 *
 * The day ticker is a single emission: the real one loops on `delay` until each midnight, which
 * `advanceUntilIdle()` would chase forever. [minSegmentMs] defaults to 0 because a ViewModel under
 * test reads the wall clock, so its sessions last milliseconds and the production one-second floor
 * would drop every one of them.
 */
fun buildTestStudyTimeRepository(
    dao: FakeStudyTimeDao,
    dataStore: DataStore<Preferences>,
    applicationScope: CoroutineScope,
    defaultDispatcher: CoroutineDispatcher,
    settingsRepository: SettingsRepository = SettingsRepository(dataStore),
    reviewStatisticDao: FakeReviewStatisticDao = FakeReviewStatisticDao(),
    assignmentDao: FakeAssignmentDao = FakeAssignmentDao(),
    clock: Clock = Clock.System,
    minSegmentMs: Long = 0L
) = StudyTimeRepository(
    studyTimeDao = dao,
    settingsRepository = settingsRepository,
    dashboardCacheRepository = DashboardCacheRepository(dataStore),
    reviewStatisticDao = reviewStatisticDao,
    assignmentDao = assignmentDao,
    applicationScope = applicationScope,
    defaultDispatcher = defaultDispatcher,
    clock = clock,
    minSegmentMs = minSegmentMs,
    dayTicks = { flowOf(Unit) }
)
