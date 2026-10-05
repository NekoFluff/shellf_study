package com.crazyfluff.shellfstudy.core.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.AssignmentStatsRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.database.AssignmentEntity
import com.crazyfluff.shellfstudy.fakes.FakeAssignmentDao
import com.crazyfluff.shellfstudy.fakes.FakeLevelProgressionDao
import com.crazyfluff.shellfstudy.shared.database.LevelProgressionEntity
import com.crazyfluff.shellfstudy.shared.database.SubjectEntity
import com.crazyfluff.shellfstudy.fakes.FakeNotificationPoster
import com.crazyfluff.shellfstudy.fakes.FakeNotificationScheduler
import com.crazyfluff.shellfstudy.fakes.FakeSubjectDao
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.shared.notifications.DefaultNotificationCoordinator
import com.crazyfluff.shellfstudy.shared.notifications.DeferredNotificationCategory
import com.crazyfluff.shellfstudy.shared.notifications.NotificationChannels
import com.crazyfluff.shellfstudy.shared.notifications.NotificationStateRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Clock
import kotlin.time.Instant

class DefaultNotificationCoordinatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var subjectDao: FakeSubjectDao
    private lateinit var assignmentDao: FakeAssignmentDao
    private lateinit var assignmentRepository: AssignmentRepository
    private lateinit var assignmentStatsRepository: AssignmentStatsRepository
    private lateinit var statsRepository: StatsRepository
    private lateinit var levelProgressionDao: FakeLevelProgressionDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var notificationStateRepository: NotificationStateRepository
    private lateinit var notificationScheduler: FakeNotificationScheduler
    private lateinit var notificationPoster: FakeNotificationPoster
    private lateinit var coordinator: DefaultNotificationCoordinator

    @Before
    fun setUp() {
        // The standard repository graph (in-memory DAOs + MockWebServer API) — the coordinator only
        // needs assignmentRepository/statsRepository, so reuse the shared factory instead of
        // hand-wiring a second copy of the graph here.
        val repos = buildTestRepositories("http://localhost/")
        subjectDao = repos.subjectDao
        assignmentDao = repos.assignmentDao
        assignmentRepository = repos.assignmentRepository
        assignmentStatsRepository = repos.assignmentStatsRepository
        statsRepository = repos.statsRepository
        levelProgressionDao = repos.levelProgressionDao

        val settingsDataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile("settings.preferences_pb") })
        settingsRepository = SettingsRepository(settingsDataStore)

        val notifStateDataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile("notif_state.preferences_pb") })
        notificationStateRepository = NotificationStateRepository(notifStateDataStore)

        notificationScheduler = FakeNotificationScheduler()
        notificationPoster = FakeNotificationPoster()

        coordinator = DefaultNotificationCoordinator(
            assignmentStatsRepository,
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
        val nowHour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
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
        assertThat(notificationPoster.cancelled).hasSize(4)
        assertThat(notificationStateRepository.state.first().lastNotifiedReviewCount).isEqualTo(0)
    }

    @Test
    fun `evaluateStudyReminder posts when not quiet and streak is inactive today`() = runTest {
        enableNotifications()
        settingsRepository.setQuietHoursEnabled(false)

        coordinator.evaluateStudyReminder()

        assertThat(notificationPoster.posted.map { it.channelId }).contains(NotificationChannels.STUDY_REMINDER)
    }

    @Test
    fun `evaluateStudyReminder does nothing once the streak is already active today`() = runTest {
        enableNotifications()
        settingsRepository.setQuietHoursEnabled(false)
        statsRepository.markStudyActivityToday()

        coordinator.evaluateStudyReminder()

        assertThat(notificationPoster.posted).isEmpty()
    }

    @Test
    fun `evaluateStudyReminder does not re-post on a second evaluation the same day`() = runTest {
        enableNotifications()
        settingsRepository.setQuietHoursEnabled(false)

        coordinator.evaluateStudyReminder()
        coordinator.evaluateStudyReminder()

        assertThat(notificationPoster.posted.count { it.channelId == NotificationChannels.STUDY_REMINDER }).isEqualTo(1)
    }

    @Test
    fun `evaluateStudyReminder defers past quiet hours instead of silently dropping forever`() = runTest {
        enableNotifications()
        // A 23-hour window starting at the current hour always covers "now", regardless of when
        // this test runs — this is the case where the user's fixed reminder hour happens to fall
        // inside quiet hours every single day.
        val nowHour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
        settingsRepository.setQuietHoursEnabled(true)
        settingsRepository.setQuietHoursStartHour(nowHour)
        settingsRepository.setQuietHoursEndHour((nowHour + 23) % 24)

        coordinator.evaluateStudyReminder()

        assertThat(notificationPoster.posted).isEmpty()
        assertThat(notificationScheduler.deferredNotifications.map { it.first })
            .containsExactly(DeferredNotificationCategory.STUDY_REMINDER)
    }

    /** Level 5 in progress, with one kanji at Apprentice IV: its next review is the only deciding
     *  session, and passing it levels up. */
    private suspend fun seedLevelUpKanji(availableAt: Instant) {
        levelProgressionDao.upsertAll(
            listOf(
                LevelProgressionEntity(
                    id = 1, level = 5, createdAt = "2026-01-01T00:00:00Z", unlockedAt = null,
                    startedAt = null, passedAt = null, completedAt = null, abandonedAt = null
                )
            )
        )
        subjectDao.upsertAll(
            listOf(
                SubjectEntity(
                    id = 50, subjectType = "kanji", level = 5, slug = "力", characters = "力",
                    meanings = emptyList(), readings = emptyList(), documentUrl = null
                )
            )
        )
        assignmentDao.upsertAll(
            listOf(
                assignment(
                    1, 50, srsStage = 4, availableAt = availableAt.toString(), unlockedAt = "2026-01-01T00:00:00Z"
                ).copy(subjectType = "kanji")
            )
        )
    }

    private fun hourFromNow(hours: Int): Instant {
        val now = Clock.System.now()
        return Instant.fromEpochSeconds((now.epochSeconds / 3600 + hours) * 3600)
    }

    private fun levelUpDeferrals() =
        notificationScheduler.deferredNotifications.filter { it.first == DeferredNotificationCategory.LEVEL_UP }

    @Test
    fun `rescheduleLevelUpReminder schedules a wakeup for the next deciding review`() = runTest {
        enableNotifications()
        val due = hourFromNow(3)
        seedLevelUpKanji(availableAt = due)

        coordinator.rescheduleLevelUpReminder()

        assertThat(levelUpDeferrals()).containsExactly(DeferredNotificationCategory.LEVEL_UP to due)
    }

    @Test
    fun `rescheduleLevelUpReminder does nothing when the setting is off`() = runTest {
        enableNotifications()
        settingsRepository.setLevelUpRemindersEnabled(false)
        seedLevelUpKanji(availableAt = hourFromNow(3))

        coordinator.rescheduleLevelUpReminder()

        assertThat(levelUpDeferrals()).isEmpty()
    }

    @Test
    fun `evaluateLevelUpReminder posts once for a due deciding review and never reschedules it`() = runTest {
        enableNotifications()
        settingsRepository.setQuietHoursEnabled(false)
        seedLevelUpKanji(availableAt = hourFromNow(-2))

        coordinator.evaluateLevelUpReminder()
        coordinator.evaluateLevelUpReminder()
        // Already announced, so another sync's reschedule must not wake the worker for it again.
        coordinator.rescheduleLevelUpReminder()

        assertThat(notificationPoster.posted.map { it.channelId }).containsExactly(NotificationChannels.LEVEL_UP)
        assertThat(notificationPoster.posted.single().body).startsWith("1 kanji review is ready.")
        assertThat(levelUpDeferrals()).isEmpty()
    }

    @Test
    fun `evaluateLevelUpReminder woken early moves the wakeup instead of posting`() = runTest {
        enableNotifications()
        settingsRepository.setQuietHoursEnabled(false)
        val due = hourFromNow(5)
        seedLevelUpKanji(availableAt = due)

        coordinator.evaluateLevelUpReminder()

        assertThat(notificationPoster.posted).isEmpty()
        assertThat(levelUpDeferrals()).containsExactly(DeferredNotificationCategory.LEVEL_UP to due)
    }

    @Test
    fun `evaluateLevelUpReminder defers past quiet hours`() = runTest {
        enableNotifications()
        val nowHour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
        settingsRepository.setQuietHoursEnabled(true)
        settingsRepository.setQuietHoursStartHour(nowHour)
        settingsRepository.setQuietHoursEndHour((nowHour + 23) % 24)
        seedLevelUpKanji(availableAt = hourFromNow(-2))

        coordinator.evaluateLevelUpReminder()

        assertThat(notificationPoster.posted).isEmpty()
        assertThat(levelUpDeferrals()).hasSize(1)
    }
}
