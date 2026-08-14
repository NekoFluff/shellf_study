package com.crazyfluff.shellfstudy.core.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.core.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.SubjectRepository
import com.crazyfluff.shellfstudy.shared.database.AssignmentEntity
import com.crazyfluff.shellfstudy.fakes.FakeAssignmentDao
import com.crazyfluff.shellfstudy.fakes.FakeLevelProgressionDao
import com.crazyfluff.shellfstudy.fakes.FakeNotificationPoster
import com.crazyfluff.shellfstudy.fakes.FakeNotificationScheduler
import com.crazyfluff.shellfstudy.fakes.FakePitchAccentBundledSource
import com.crazyfluff.shellfstudy.fakes.FakePitchAccentCacheDao
import com.crazyfluff.shellfstudy.fakes.FakeReviewStatisticDao
import com.crazyfluff.shellfstudy.fakes.FakeSrsSystemDao
import com.crazyfluff.shellfstudy.fakes.FakeStudyActivityDao
import com.crazyfluff.shellfstudy.fakes.FakeStudyMaterialDao
import com.crazyfluff.shellfstudy.fakes.FakeSubjectDao
import com.crazyfluff.shellfstudy.fakes.FakeSyncStateDao
import com.crazyfluff.shellfstudy.fakes.FakeWeblioApi
import com.crazyfluff.shellfstudy.fakes.buildTestApi
import com.crazyfluff.shellfstudy.core.data.PitchAccentRepository
import com.crazyfluff.shellfstudy.core.data.WeblioPitchAccentParser
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DefaultNotificationCoordinatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var subjectDao: FakeSubjectDao
    private lateinit var assignmentDao: FakeAssignmentDao
    private lateinit var assignmentRepository: AssignmentRepository
    private lateinit var statsRepository: StatsRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var notificationStateRepository: NotificationStateRepository
    private lateinit var notificationScheduler: FakeNotificationScheduler
    private lateinit var notificationPoster: FakeNotificationPoster
    private lateinit var coordinator: DefaultNotificationCoordinator

    @Before
    fun setUp() {
        val api = buildTestApi("http://localhost/")
        subjectDao = FakeSubjectDao()
        assignmentDao = FakeAssignmentDao(subjectLevelLookup = subjectDao::levelOf, subjectLookup = subjectDao::entityOf)
        val syncStateDao = FakeSyncStateDao()
        val pitchAccentRepository = PitchAccentRepository(
            FakePitchAccentBundledSource(emptyMap()), FakePitchAccentCacheDao(), FakeWeblioApi(), WeblioPitchAccentParser()
        )
        val srsSystemDao = FakeSrsSystemDao()
        val subjectRepository = SubjectRepository(api, subjectDao, srsSystemDao, FakeStudyMaterialDao(), syncStateDao, pitchAccentRepository)
        assignmentRepository = AssignmentRepository(api, assignmentDao, subjectDao, syncStateDao, subjectRepository, srsSystemDao)
        statsRepository = StatsRepository(api, FakeReviewStatisticDao(), FakeLevelProgressionDao(), FakeStudyActivityDao(), syncStateDao)

        val settingsDataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile("settings.preferences_pb") })
        settingsRepository = SettingsRepository(settingsDataStore)

        val notifStateDataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile("notif_state.preferences_pb") })
        notificationStateRepository = NotificationStateRepository(notifStateDataStore)

        notificationScheduler = FakeNotificationScheduler()
        notificationPoster = FakeNotificationPoster()

        coordinator = DefaultNotificationCoordinator(
            assignmentRepository,
            statsRepository,
            settingsRepository,
            notificationStateRepository,
            notificationScheduler,
            notificationPoster
        )
    }

    private suspend fun enableNotifications() {
        settingsRepository.setNotificationsEnabled(true)
    }

    private fun assignment(
        id: Long,
        subjectId: Long,
        srsStage: Int = 4,
        availableAt: String? = null,
        unlockedAt: String? = null,
        startedAt: String? = null
    ) = AssignmentEntity(
        id = id,
        subjectId = subjectId,
        subjectType = "vocabulary",
        srsStage = srsStage,
        createdAt = "2026-01-01T00:00:00Z",
        unlockedAt = unlockedAt,
        startedAt = startedAt,
        passedAt = null,
        burnedAt = null,
        availableAt = availableAt,
        resurrectedAt = null,
        hidden = false
    )

    @Test
    fun `does nothing when notifications are disabled`() = runTest {
        assignmentDao.upsertAll(listOf(assignment(1, 1, availableAt = "2020-01-01T00:00:00Z")))

        coordinator.evaluateReviewsAndBacklog()

        assertThat(notificationPoster.posted).isEmpty()
    }

    @Test
    fun `posts a reviews-available notification when the due-now count rises`() = runTest {
        enableNotifications()
        settingsRepository.setQuietHoursEnabled(false)
        assignmentDao.upsertAll(listOf(assignment(1, 1, availableAt = "2020-01-01T00:00:00Z")))

        coordinator.evaluateReviewsAndBacklog()

        assertThat(notificationPoster.posted).hasSize(1)
        assertThat(notificationPoster.posted.first().channelId).isEqualTo(NotificationChannels.REVIEWS_AVAILABLE)
    }

    @Test
    fun `does not re-notify for the same unaddressed batch on a second evaluation`() = runTest {
        enableNotifications()
        settingsRepository.setQuietHoursEnabled(false)
        assignmentDao.upsertAll(listOf(assignment(1, 1, availableAt = "2020-01-01T00:00:00Z")))

        coordinator.evaluateReviewsAndBacklog()
        coordinator.evaluateReviewsAndBacklog()

        assertThat(notificationPoster.posted).hasSize(1)
    }

    @Test
    fun `notifies again once a new batch increases the count past the watermark`() = runTest {
        enableNotifications()
        settingsRepository.setQuietHoursEnabled(false)
        assignmentDao.upsertAll(listOf(assignment(1, 1, availableAt = "2020-01-01T00:00:00Z")))
        coordinator.evaluateReviewsAndBacklog()

        assignmentDao.upsertAll(listOf(assignment(2, 2, availableAt = "2020-01-01T00:00:00Z")))
        coordinator.evaluateReviewsAndBacklog()

        assertThat(notificationPoster.posted).hasSize(2)
    }

    @Test
    fun `defers rather than posts during quiet hours`() = runTest {
        enableNotifications()
        // A 23-hour window starting at the current hour always covers "now", regardless of when
        // this test runs — keeps the assertion time-independent without injecting a clock.
        val nowHour = java.time.LocalTime.now(java.time.ZoneId.systemDefault()).hour
        settingsRepository.setQuietHoursEnabled(true)
        settingsRepository.setQuietHoursStartHour(nowHour)
        settingsRepository.setQuietHoursEndHour((nowHour + 23) % 24)
        assignmentDao.upsertAll(listOf(assignment(1, 1, availableAt = "2020-01-01T00:00:00Z")))

        coordinator.evaluateReviewsAndBacklog()

        assertThat(notificationPoster.posted).isEmpty()
        assertThat(notificationScheduler.nextReviewCheckInstant).isNotNull()
    }

    @Test
    fun `posts a backlog warning once the threshold is crossed`() = runTest {
        enableNotifications()
        settingsRepository.setQuietHoursEnabled(false)
        settingsRepository.setBacklogThreshold(5)
        assignmentDao.upsertAll((1..6L).map { assignment(it, it, availableAt = "2020-01-01T00:00:00Z") })

        coordinator.evaluateReviewsAndBacklog()

        assertThat(notificationPoster.posted.map { it.channelId }).contains(NotificationChannels.REVIEWS_BACKLOG)
    }

    @Test
    fun `withholds a repeat backlog warning inside the cooldown window`() = runTest {
        enableNotifications()
        settingsRepository.setQuietHoursEnabled(false)
        settingsRepository.setBacklogThreshold(5)
        assignmentDao.upsertAll((1..6L).map { assignment(it, it, availableAt = "2020-01-01T00:00:00Z") })

        coordinator.evaluateReviewsAndBacklog()
        val firstPostCount = notificationPoster.posted.count { it.channelId == NotificationChannels.REVIEWS_BACKLOG }
        coordinator.evaluateReviewsAndBacklog()
        val secondPostCount = notificationPoster.posted.count { it.channelId == NotificationChannels.REVIEWS_BACKLOG }

        assertThat(firstPostCount).isEqualTo(1)
        assertThat(secondPostCount).isEqualTo(1)
    }

    @Test
    fun `onLogin schedules future work without posting anything`() = runTest {
        enableNotifications()

        coordinator.onLogin()

        assertThat(notificationPoster.posted).isEmpty()
    }

    @Test
    fun `onLogout cancels all scheduled work, clears the tray, and resets dedupe state`() = runTest {
        enableNotifications()
        settingsRepository.setQuietHoursEnabled(false)
        assignmentDao.upsertAll(listOf(assignment(1, 1, availableAt = "2020-01-01T00:00:00Z")))
        coordinator.evaluateReviewsAndBacklog()

        coordinator.onLogout()

        assertThat(notificationScheduler.cancelAllCallCount).isEqualTo(1)
        assertThat(notificationPoster.cancelled).hasSize(3)
        assertThat(notificationStateRepository.state.first().lastNotifiedReviewCount).isEqualTo(0)
    }
}
