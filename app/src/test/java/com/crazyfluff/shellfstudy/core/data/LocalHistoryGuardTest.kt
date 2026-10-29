package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.crazyfluff.shellfstudy.fakes.FakeStudyActivityDao
import com.crazyfluff.shellfstudy.fakes.FakeStudyTimeDao
import com.crazyfluff.shellfstudy.shared.data.LocalHistoryGuard
import com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDayEntity
import com.crazyfluff.shellfstudy.shared.database.studytime.StudyTimeSegmentEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalHistoryGuardTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val studyActivityDao = FakeStudyActivityDao()
    private val studyTimeDao = FakeStudyTimeDao()
    private var ownerChanges = 0
    private val guard by lazy {
        LocalHistoryGuard(
            PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile("guard.preferences_pb") }),
            studyActivityDao,
            studyTimeDao,
            onOwnerChanged = { ownerChanges++ }
        )
    }

    private suspend fun seedHistory() {
        studyActivityDao.markActive(StudyActivityDayEntity(date = "2026-09-24"))
        studyTimeDao.insert(
            StudyTimeSegmentEntity(
                kind = "REVIEW", startedAtMs = 0L, durationMs = 60_000L, level = 8, itemsAnswered = 6
            )
        )
    }

    private suspend fun historyIsKept() {
        assertThat(studyActivityDao.observeActiveDays().first()).containsExactly("2026-09-24")
        assertThat(studyTimeDao.all).hasSize(1)
    }

    @Test
    fun `the first account seen adopts history recorded before owners were tracked`() = runTest {
        seedHistory()

        guard.claimFor("user-a")

        historyIsKept()
    }

    @Test
    fun `the same account signing back in keeps its history`() = runTest {
        guard.claimFor("user-a")
        seedHistory()

        // A logout and sign-in, or a regenerated API key, comes back as the same WaniKani user.
        guard.claimFor("user-a")

        historyIsKept()
    }

    @Test
    fun `a different account signing in clears the previous account's history`() = runTest {
        guard.claimFor("user-a")
        seedHistory()

        guard.claimFor("user-b")

        assertThat(studyActivityDao.observeActiveDays().first()).isEmpty()
        assertThat(studyTimeDao.all).isEmpty()
    }

    @Test
    fun `history recorded by the new account is kept after the switch`() = runTest {
        guard.claimFor("user-a")
        guard.claimFor("user-b")
        seedHistory()

        guard.claimFor("user-b")

        historyIsKept()
    }

    @Test
    fun `owner is unknown until an account is seen, then that account`() = runTest {
        assertThat(guard.owner()).isNull()

        guard.claimFor("user-a")

        assertThat(guard.owner()).isEqualTo("user-a")
    }

    @Test
    fun `only a switch to another account counts as an owner change`() = runTest {
        guard.claimFor("user-a")
        guard.claimFor("user-a")
        assertThat(ownerChanges).isEqualTo(0)

        guard.claimFor("user-b")

        assertThat(ownerChanges).isEqualTo(1)
        assertThat(guard.owner()).isEqualTo("user-b")
    }
}
