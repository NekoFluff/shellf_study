package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.crazyfluff.shellfstudy.fakes.FakeHistoryBackend
import com.crazyfluff.shellfstudy.fakes.FakeStudyActivityDao
import com.crazyfluff.shellfstudy.fakes.FakeStudyTimeDao
import com.crazyfluff.shellfstudy.fakes.FakeTokenCipher
import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.LocalHistoryGuard
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.ThemeMode
import com.crazyfluff.shellfstudy.shared.data.model.ReviewPriority
import com.crazyfluff.shellfstudy.shared.data.TokenRepository
import com.crazyfluff.shellfstudy.shared.data.historysync.HistorySyncOutcome
import com.crazyfluff.shellfstudy.shared.data.historysync.HistorySyncScheduler
import com.crazyfluff.shellfstudy.shared.data.historysync.HistorySyncStore
import com.crazyfluff.shellfstudy.shared.data.historysync.HistorySyncer
import com.crazyfluff.shellfstudy.shared.data.historysync.SettingsSync
import com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDayEntity
import com.crazyfluff.shellfstudy.shared.database.studytime.StudyTimeSegmentEntity
import com.crazyfluff.shellfstudy.shared.network.historysync.HistorySyncApi
import com.crazyfluff.shellfstudy.shared.network.historysync.createHistorySyncHttpClient
import com.crazyfluff.shellfstudy.shared.network.waniKaniJson
import com.google.common.truth.Truth.assertThat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Clock
import kotlin.time.Instant

private const val USER_A = "user-a"
private const val WK_TOKEN_A = "wk-token-a"
private const val WK_TOKEN_B = "wk-token-b"

class HistorySyncerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val backend = FakeHistoryBackend(mapOf(WK_TOKEN_A to USER_A, WK_TOKEN_B to "user-b"))

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = backend
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    /** One install: its own preferences file and history tables, syncing through [backend]. */
    private inner class Device(name: String) {
        /** This device's clock, in epoch ms — settings sync orders changes by it. */
        var nowMs = 1_000_000L
        private val clock = object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(nowMs)
        }
        val dataStore =
            PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile("$name.preferences_pb") })
        val studyTimeDao = FakeStudyTimeDao()
        val studyActivityDao = FakeStudyActivityDao()
        val tokenRepository = TokenRepository(dataStore, FakeTokenCipher())
        val store = HistorySyncStore(dataStore, FakeTokenCipher())
        val guard =
            LocalHistoryGuard(dataStore, studyActivityDao, studyTimeDao, onOwnerChanged = { store.clearAccountState() })
        var syncRequests = 0
        val settings = SettingsRepository(dataStore, clock = clock)
        val settingsSync = SettingsSync(dataStore, settings, waniKaniJson(), clock)
        val syncer = HistorySyncer(
            api = HistorySyncApi(createHistorySyncHttpClient(waniKaniJson()), server.url("/").toString().trimEnd('/')),
            store = store,
            tokenRepository = tokenRepository,
            localHistoryGuard = guard,
            studyTimeDao = studyTimeDao,
            studyActivityDao = studyActivityDao,
            settingsSync = settingsSync,
            scheduler = HistorySyncScheduler { syncRequests++ },
            json = waniKaniJson()
        )

        /** Signed in as [USER_A], history claimed, sync turned on: the state after a dashboard load. */
        suspend fun signedInWithSyncOn(wkToken: String = WK_TOKEN_A, owner: String = USER_A): Device {
            tokenRepository.saveToken(wkToken)
            guard.claimFor(owner)
            store.setEnabled(true)
            return this
        }

        suspend fun recordSegment(syncId: String, startedAtMs: Long = 1_000L) = studyTimeDao.insert(
            StudyTimeSegmentEntity(
                kind = "REVIEW", startedAtMs = startedAtMs, durationMs = 60_000L, level = 7, itemsAnswered = 10,
                syncId = syncId
            )
        )

        suspend fun recordDay(date: String) = studyActivityDao.markActive(StudyActivityDayEntity(date))
    }

    @Test
    fun `uploads local history and marks it uploaded, so the next pass sends nothing`() = runTest {
        val phone = Device("phone").signedInWithSyncOn()
        phone.recordSegment("s1")
        phone.recordSegment("s2")
        phone.recordDay("2026-10-28")

        assertThat(phone.syncer.sync()).isEqualTo(HistorySyncOutcome.SUCCESS)

        assertThat(backend.recordsFor(USER_A).map { it.recordId }).containsExactly("s1", "s2", "2026-10-28")
        assertThat(phone.studyTimeDao.getNotUploaded(10)).isEmpty()
        assertThat(phone.studyActivityDao.getNotUploaded(10)).isEmpty()

        val pushesBefore = backend.pushSizes.size
        assertThat(phone.syncer.sync()).isEqualTo(HistorySyncOutcome.SUCCESS)
        assertThat(backend.pushSizes.size).isEqualTo(pushesBefore)
    }

    @Test
    fun `a second device gets the first device's history and does not send it back`() = runTest {
        val phone = Device("phone").signedInWithSyncOn()
        phone.recordSegment("s1", startedAtMs = 5_000L)
        phone.recordDay("2026-10-28")
        phone.syncer.sync()
        val pushesBefore = backend.pushSizes.size

        val tablet = Device("tablet").signedInWithSyncOn()
        assertThat(tablet.syncer.sync()).isEqualTo(HistorySyncOutcome.SUCCESS)

        val pulled = tablet.studyTimeDao.all.single()
        assertThat(pulled.syncId).isEqualTo("s1")
        assertThat(pulled.startedAtMs).isEqualTo(5_000L)
        assertThat(pulled.durationMs).isEqualTo(60_000L)
        assertThat(pulled.level).isEqualTo(7)
        assertThat(pulled.itemsAnswered).isEqualTo(10)
        assertThat(pulled.uploaded).isTrue()
        assertThat(tablet.studyActivityDao.observeActiveDays().first()).containsExactly("2026-10-28")
        assertThat(backend.pushSizes.size).isEqualTo(pushesBefore)
    }

    @Test
    fun `both devices end up with the union of their history`() = runTest {
        val phone = Device("phone").signedInWithSyncOn()
        val tablet = Device("tablet").signedInWithSyncOn()
        phone.recordSegment("from-phone")
        tablet.recordSegment("from-tablet")

        phone.syncer.sync()
        tablet.syncer.sync()
        phone.syncer.sync()

        assertThat(phone.studyTimeDao.all.map { it.syncId }).containsExactly("from-phone", "from-tablet")
        assertThat(tablet.studyTimeDao.all.map { it.syncId }).containsExactly("from-phone", "from-tablet")
    }

    @Test
    fun `uploads in batches the backend accepts`() = runTest {
        val phone = Device("phone").signedInWithSyncOn()
        repeat(450) { phone.recordSegment("s$it", startedAtMs = it.toLong()) }

        phone.syncer.sync()

        assertThat(backend.pushSizes).containsExactly(200, 200, 50).inOrder()
        assertThat(backend.recordsFor(USER_A)).hasSize(450)
    }

    @Test
    fun `a pull that fails part-way resumes from the last page it saved`() = runTest {
        val phone = Device("phone").signedInWithSyncOn()
        listOf("s1", "s2", "s3").forEach { phone.recordSegment(it) }
        phone.syncer.sync()
        val tablet = Device("tablet").signedInWithSyncOn()
        backend.pageSize = 1
        backend.pullCursors.clear()
        backend.failingPullIndexes += 1 // the first page lands, then the second request fails

        assertThat(tablet.syncer.sync()).isEqualTo(HistorySyncOutcome.RETRY)
        assertThat(tablet.studyTimeDao.all.map { it.syncId }).containsExactly("s1")
        val cursorAfterFirstPage = backend.pullCursors[1]

        assertThat(tablet.syncer.sync()).isEqualTo(HistorySyncOutcome.SUCCESS)
        assertThat(tablet.studyTimeDao.all.map { it.syncId }).containsExactly("s1", "s2", "s3")
        // The second pass started where the first one stopped, not from zero.
        assertThat(backend.pullCursors[2]).isEqualTo(cursorAfterFirstPage)
    }

    @Test
    fun `nothing is sent while sync is off`() = runTest {
        val phone = Device("phone").signedInWithSyncOn()
        phone.store.setEnabled(false)
        phone.recordSegment("s1")

        assertThat(phone.syncer.sync()).isEqualTo(HistorySyncOutcome.SKIPPED)

        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `nothing is sent before local history has been matched to an account`() = runTest {
        val phone = Device("phone")
        phone.tokenRepository.saveToken(WK_TOKEN_A)
        phone.store.setEnabled(true)
        phone.recordSegment("s1")

        assertThat(phone.syncer.sync()).isEqualTo(HistorySyncOutcome.SKIPPED)

        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `history is never uploaded under an account other than the one that made it`() = runTest {
        // User B's token is signed in, but the guard hasn't yet moved this device's history off user A.
        val phone = Device("phone").signedInWithSyncOn(wkToken = WK_TOKEN_B, owner = USER_A)
        phone.recordSegment("s1")

        assertThat(phone.syncer.sync()).isEqualTo(HistorySyncOutcome.RETRY)

        assertThat(backend.recordsFor("user-b")).isEmpty()
        assertThat(backend.recordsFor(USER_A)).isEmpty()
        assertThat(phone.store.session()).isNull()
    }

    @Test
    fun `a rejected WaniKani token is an auth failure`() = runTest {
        val phone = Device("phone").signedInWithSyncOn(wkToken = "revoked")

        assertThat(phone.syncer.sync()).isEqualTo(HistorySyncOutcome.AUTH_FAILURE)
    }

    @Test
    fun `the backend token is reused across passes`() = runTest {
        val phone = Device("phone").signedInWithSyncOn()

        phone.syncer.sync()
        phone.syncer.sync()

        assertThat(backend.authCalls).isEqualTo(1)
    }

    @Test
    fun `a backend token that stops working is replaced once, and the pass still succeeds`() = runTest {
        val phone = Device("phone").signedInWithSyncOn()
        phone.syncer.sync()
        backend.rotateSecret()
        phone.recordSegment("after-rotation")

        assertThat(phone.syncer.sync()).isEqualTo(HistorySyncOutcome.SUCCESS)

        assertThat(backend.authCalls).isEqualTo(2)
        assertThat(backend.recordsFor(USER_A).map { it.recordId }).contains("after-rotation")
    }

    @Test
    fun `records this build can't read are skipped without stopping the pass`() = runTest {
        backend.seed(USER_A, "study_segment", "future", buildJsonObject { put("v", 2); put("shape", "new") })
        backend.seed(USER_A, "activity_day", "not-a-date", JsonObject(emptyMap()))
        backend.seed(USER_A, "activity_day", "2026-10-01", JsonObject(emptyMap()))
        val phone = Device("phone").signedInWithSyncOn()

        assertThat(phone.syncer.sync()).isEqualTo(HistorySyncOutcome.SUCCESS)

        assertThat(phone.studyTimeDao.all).isEmpty()
        assertThat(phone.studyActivityDao.observeActiveDays().first()).containsExactly("2026-10-01")
    }

    @Test
    fun `deleting synced history empties the backend and opting back in uploads it all again`() = runTest {
        val phone = Device("phone").signedInWithSyncOn()
        phone.recordSegment("s1")
        phone.recordDay("2026-10-28")
        phone.syncer.sync()

        assertThat(phone.syncer.deleteSyncedHistory()).isInstanceOf(ApiResult.Success::class.java)

        assertThat(backend.recordsFor(USER_A)).isEmpty()
        assertThat(phone.store.isEnabled()).isFalse()
        assertThat(phone.studyTimeDao.all.map { it.syncId }).containsExactly("s1")

        phone.syncer.setEnabled(true)
        assertThat(phone.syncRequests).isEqualTo(1)
        phone.syncer.sync()
        assertThat(backend.recordsFor(USER_A).map { it.recordId }).containsExactly("s1", "2026-10-28")
    }

    @Test
    fun `a failed delete keeps sync on and says so`() = runTest {
        val phone = Device("phone").signedInWithSyncOn(wkToken = "revoked")

        val result = phone.syncer.deleteSyncedHistory()

        assertThat(result).isInstanceOf(ApiResult.Error::class.java)
        assertThat((result as ApiResult.Error).message).isEqualTo("Couldn't delete synced data. Try again later.")
        assertThat(phone.store.isEnabled()).isTrue()
    }

    @Test
    fun `another account signing in forgets the previous account's sync state`() = runTest {
        val phone = Device("phone").signedInWithSyncOn()
        phone.recordSegment("s1")
        phone.syncer.sync()

        phone.guard.claimFor("user-b")

        assertThat(phone.store.isEnabled()).isFalse()
        assertThat(phone.store.cursor()).isEqualTo(0L)
        assertThat(phone.store.session()).isNull()
    }

    @Test
    fun `a missing backend URL makes sync unavailable and every pass a no-op`() = runTest {
        val phone = Device("phone").signedInWithSyncOn()
        val unavailable = HistorySyncer(
            api = null,
            store = phone.store,
            tokenRepository = phone.tokenRepository,
            localHistoryGuard = phone.guard,
            studyTimeDao = phone.studyTimeDao,
            studyActivityDao = phone.studyActivityDao,
            settingsSync = phone.settingsSync,
            scheduler = HistorySyncScheduler.None,
            json = waniKaniJson()
        )

        assertThat(unavailable.isAvailable).isFalse()
        assertThat(unavailable.sync()).isEqualTo(HistorySyncOutcome.SKIPPED)
        assertThat(server.requestCount).isEqualTo(0)
    }

    // --- Settings ---

    @Test
    fun `a setting changed on one device reaches the other`() = runTest {
        val phone = Device("phone").signedInWithSyncOn()
        val tablet = Device("tablet").signedInWithSyncOn()

        phone.settings.setDailyLessonGoal(25)
        phone.settings.setReviewPriority(ReviewPriority.RANK_UP)
        phone.syncer.sync()
        tablet.syncer.sync()

        val synced = tablet.settings.settings.first()
        assertThat(synced.dailyLessonGoal).isEqualTo(25)
        assertThat(synced.reviewPriority).isEqualTo(ReviewPriority.RANK_UP)
    }

    @Test
    fun `the later change wins, whichever device uploads first`() = runTest {
        val phone = Device("phone").signedInWithSyncOn()
        val tablet = Device("tablet").signedInWithSyncOn()
        // The phone changes the goal first but is offline; the tablet changes it later and syncs.
        phone.nowMs = 1_000L
        phone.settings.setDailyLessonGoal(10)
        tablet.nowMs = 2_000L
        tablet.settings.setDailyLessonGoal(20)
        tablet.syncer.sync()

        // The phone's older change arrives late.
        phone.syncer.sync()
        tablet.syncer.sync()

        assertThat(phone.settings.settings.first().dailyLessonGoal).isEqualTo(20)
        assertThat(tablet.settings.settings.first().dailyLessonGoal).isEqualTo(20)
    }

    @Test
    fun `a device joining sync adopts the account's settings, and uploads only those the account lacks`() = runTest {
        val phone = Device("phone").signedInWithSyncOn()
        phone.settings.setDailyLessonGoal(25)
        phone.syncer.sync()
        // Settings the tablet changed before sync existed: values with no change time.
        val tablet = Device("tablet")
        tablet.dataStore.edit {
            it[intPreferencesKey("daily_lesson_goal")] = 5
            it[booleanPreferencesKey("show_stroke_order")] = false
        }
        tablet.signedInWithSyncOn()

        tablet.syncer.sync()
        phone.syncer.sync()

        assertThat(tablet.settings.settings.first().dailyLessonGoal).isEqualTo(25)
        assertThat(phone.settings.settings.first().showStrokeOrder).isFalse()
        assertThat(phone.settings.settings.first().dailyLessonGoal).isEqualTo(25)
    }

    @Test
    fun `per-device settings and untouched defaults are never uploaded`() = runTest {
        val phone = Device("phone").signedInWithSyncOn()
        phone.settings.setThemeMode(ThemeMode.EINK)
        phone.settings.setAudioMuted(true)
        phone.settings.setNotificationsEnabled(true)

        phone.syncer.sync()

        assertThat(backend.recordsFor(USER_A).filter { it.type == "setting" }).isEmpty()
    }

    @Test
    fun `values this build can't accept are refused or clamped`() = runTest {
        fun change(name: String, value: kotlinx.serialization.json.JsonPrimitive) = buildJsonObject {
            put("v", 1); put("name", name); put("value", value); put("changedAtMs", 5_000L)
        }
        backend.seed(
            USER_A, "setting", "review-priority-5000", change("review_priority", JsonPrimitive("FROM_THE_FUTURE"))
        )
        backend.seed(USER_A, "setting", "daily-lesson-goal-5000", change("daily_lesson_goal", JsonPrimitive(999)))
        backend.seed(USER_A, "setting", "theme-mode-5000", change("theme_mode", JsonPrimitive("DARK")))
        val phone = Device("phone").signedInWithSyncOn()

        assertThat(phone.syncer.sync()).isEqualTo(HistorySyncOutcome.SUCCESS)

        val settings = phone.settings.settings.first()
        assertThat(settings.reviewPriority).isEqualTo(ReviewPriority.DEFAULT)
        assertThat(settings.dailyLessonGoal).isEqualTo(99)
        assertThat(settings.themeMode).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun `a change made while an upload is in flight stays pending`() = runTest {
        val phone = Device("phone")
        phone.settings.setDailyLessonGoal(10)
        val inFlight = phone.settingsSync.pendingRecords()
        phone.nowMs += 1
        phone.settings.setDailyLessonGoal(11)

        phone.settingsSync.markUploaded(inFlight)

        assertThat(phone.settingsSync.pendingRecords().map { it.recordId }).containsExactly("daily-lesson-goal-1000001")
    }

    @Test
    fun `uploading the same change twice stores it once`() = runTest {
        val phone = Device("phone").signedInWithSyncOn()
        phone.settings.setDailyLessonGoal(10)
        val records = phone.settingsSync.pendingRecords()
        phone.syncer.sync()

        // As if the first upload's response was lost and the pass re-sent it.
        phone.settingsSync.markAllPending()
        phone.syncer.sync()

        assertThat(backend.recordsFor(USER_A).filter { it.type == "setting" }.map { it.recordId })
            .containsExactly(records.single().recordId)
    }

    @Test
    fun `settings come back after the synced copy is deleted and sync is turned back on`() = runTest {
        val phone = Device("phone").signedInWithSyncOn()
        phone.settings.setDailyLessonGoal(25)
        phone.syncer.sync()

        phone.syncer.deleteSyncedHistory()
        assertThat(backend.recordsFor(USER_A)).isEmpty()
        phone.syncer.setEnabled(true)
        phone.syncer.sync()

        assertThat(backend.recordsFor(USER_A).filter { it.type == "setting" }).hasSize(1)
        assertThat(phone.settings.settings.first().dailyLessonGoal).isEqualTo(25)
    }
}
