package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.shared.database.LevelProgressionDao
import com.crazyfluff.shellfstudy.shared.database.LevelProgressionEntity
import com.crazyfluff.shellfstudy.shared.database.ReviewStatisticDao
import com.crazyfluff.shellfstudy.shared.database.ReviewStatisticEntity
import com.crazyfluff.shellfstudy.shared.database.SrsSystemDao
import com.crazyfluff.shellfstudy.shared.database.SrsSystemEntity
import com.crazyfluff.shellfstudy.shared.database.SyncStateDao
import com.crazyfluff.shellfstudy.shared.database.SyncStateEntity
import com.crazyfluff.shellfstudy.shared.database.outbox.OutboxDao
import com.crazyfluff.shellfstudy.shared.database.outbox.OutboxStatus
import com.crazyfluff.shellfstudy.shared.database.outbox.PendingLessonStartEntity
import com.crazyfluff.shellfstudy.shared.database.outbox.PendingReviewSubmissionEntity
import com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDao
import com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDayEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSrsSystemDao : SrsSystemDao {
    private val systems = MutableStateFlow<Map<Long, SrsSystemEntity>>(emptyMap())

    override suspend fun upsertAll(systems: List<SrsSystemEntity>) {
        this.systems.value = this.systems.value + systems.associateBy { it.id }
    }

    override fun observeAll(): Flow<List<SrsSystemEntity>> = systems.map { it.values.toList() }

    override suspend fun getById(id: Long): SrsSystemEntity? = systems.value[id]

    /** Test-only synchronous seed helper — avoids every call site needing `runTest`/suspend just to
     *  set up SRS system fixtures before exercising optimistic-grading logic. */
    fun seed(vararg entities: SrsSystemEntity) {
        systems.value = systems.value + entities.associateBy { it.id }
    }
}

class FakeReviewStatisticDao : ReviewStatisticDao {
    private val statistics = MutableStateFlow<Map<Long, ReviewStatisticEntity>>(emptyMap())

    override suspend fun upsertAll(statistics: List<ReviewStatisticEntity>) {
        this.statistics.value = this.statistics.value + statistics.associateBy { it.id }
    }

    override fun observeAll(): Flow<List<ReviewStatisticEntity>> = statistics.map { it.values.toList() }

    override fun observeBySubjectId(subjectId: Long): Flow<ReviewStatisticEntity?> = statistics.map { map ->
        map.values.firstOrNull { it.subjectId == subjectId }
    }
}

class FakeLevelProgressionDao : LevelProgressionDao {
    private val progressions = MutableStateFlow<Map<Long, LevelProgressionEntity>>(emptyMap())

    override suspend fun upsertAll(progressions: List<LevelProgressionEntity>) {
        this.progressions.value = this.progressions.value + progressions.associateBy { it.id }
    }

    override fun observeAll(): Flow<List<LevelProgressionEntity>> = progressions.map { it.values.toList() }
}

class FakeSyncStateDao : SyncStateDao {
    private val state = mutableMapOf<String, SyncStateEntity>()

    override suspend fun get(resource: String): SyncStateEntity? = state[resource]

    override suspend fun upsert(state: SyncStateEntity) {
        this.state[state.resource] = state
    }

    override suspend fun clearAll() {
        state.clear()
    }
}

class FakeStudyActivityDao : StudyActivityDao {
    private val activeDays = MutableStateFlow<Set<String>>(emptySet())

    override suspend fun markActive(entity: StudyActivityDayEntity) {
        activeDays.value = activeDays.value + entity.date
    }

    override fun observeActiveDays(): Flow<List<String>> = activeDays.map { it.sortedDescending() }
}

class FakeOutboxDao : OutboxDao {
    private val reviewSubmissions = MutableStateFlow<Map<Long, PendingReviewSubmissionEntity>>(emptyMap())
    private val lessonStarts = MutableStateFlow<Map<Long, PendingLessonStartEntity>>(emptyMap())
    private var nextReviewId = 1L
    private var nextLessonId = 1L

    override suspend fun insertReviewSubmission(entity: PendingReviewSubmissionEntity): Long {
        val id = nextReviewId++
        reviewSubmissions.value = reviewSubmissions.value + (id to entity.copy(id = id))
        return id
    }

    override suspend fun insertLessonStart(entity: PendingLessonStartEntity): Long {
        val id = nextLessonId++
        lessonStarts.value = lessonStarts.value + (id to entity.copy(id = id))
        return id
    }

    override suspend fun getPendingReviewSubmissions(): List<PendingReviewSubmissionEntity> =
        reviewSubmissions.value.values.filter { it.status == OutboxStatus.PENDING.name }.sortedBy { it.id }

    override suspend fun getPendingLessonStarts(): List<PendingLessonStartEntity> =
        lessonStarts.value.values.filter { it.status == OutboxStatus.PENDING.name }.sortedBy { it.id }

    override suspend fun deleteReviewSubmission(entity: PendingReviewSubmissionEntity) {
        reviewSubmissions.value = reviewSubmissions.value - entity.id
    }

    override suspend fun deleteLessonStart(entity: PendingLessonStartEntity) {
        lessonStarts.value = lessonStarts.value - entity.id
    }

    override suspend fun markReviewSubmissionTerminal(id: Long, message: String?) {
        val entity = reviewSubmissions.value[id] ?: return
        reviewSubmissions.value = reviewSubmissions.value + (id to entity.copy(status = OutboxStatus.FAILED_TERMINAL.name, lastErrorMessage = message))
    }

    override suspend fun markLessonStartTerminal(id: Long, message: String?) {
        val entity = lessonStarts.value[id] ?: return
        lessonStarts.value = lessonStarts.value + (id to entity.copy(status = OutboxStatus.FAILED_TERMINAL.name, lastErrorMessage = message))
    }

    override fun observePendingReviewSubmissionCount(): Flow<Int> =
        reviewSubmissions.map { map -> map.values.count { it.status == OutboxStatus.PENDING.name } }

    override fun observePendingLessonStartCount(): Flow<Int> =
        lessonStarts.map { map -> map.values.count { it.status == OutboxStatus.PENDING.name } }

    /** Test-only: every row regardless of status, for asserting on terminal/deleted state. */
    fun allReviewSubmissions(): List<PendingReviewSubmissionEntity> = reviewSubmissions.value.values.sortedBy { it.id }
    fun allLessonStarts(): List<PendingLessonStartEntity> = lessonStarts.value.values.sortedBy { it.id }
}
