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
    private val guard by lazy {
        LocalHistoryGuard(
            PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile("guard.preferences_pb") }),
            studyActivityDao,
            studyTimeDao
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
}
