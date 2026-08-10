package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.core.database.LevelProgressionDao
import com.crazyfluff.shellfstudy.core.database.LevelProgressionEntity
import com.crazyfluff.shellfstudy.core.database.ReviewStatisticDao
import com.crazyfluff.shellfstudy.core.database.ReviewStatisticEntity
import com.crazyfluff.shellfstudy.core.database.SrsSystemDao
import com.crazyfluff.shellfstudy.core.database.SrsSystemEntity
import com.crazyfluff.shellfstudy.core.database.StudyMaterialDao
import com.crazyfluff.shellfstudy.core.database.StudyMaterialEntity
import com.crazyfluff.shellfstudy.core.database.SyncStateDao
import com.crazyfluff.shellfstudy.core.database.SyncStateEntity
import com.crazyfluff.shellfstudy.core.database.reviewhistory.DailyReviewCount
import com.crazyfluff.shellfstudy.core.database.reviewhistory.ReviewLogDao
import com.crazyfluff.shellfstudy.core.database.reviewhistory.ReviewLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSrsSystemDao : SrsSystemDao {
    private val systems = MutableStateFlow<Map<Long, SrsSystemEntity>>(emptyMap())

    override suspend fun upsertAll(systems: List<SrsSystemEntity>) {
        this.systems.value = this.systems.value + systems.associateBy { it.id }
    }

    override fun observeAll(): Flow<List<SrsSystemEntity>> = systems.map { it.values.toList() }
}

class FakeReviewStatisticDao : ReviewStatisticDao {
    private val statistics = MutableStateFlow<Map<Long, ReviewStatisticEntity>>(emptyMap())

    override suspend fun upsertAll(statistics: List<ReviewStatisticEntity>) {
        this.statistics.value = this.statistics.value + statistics.associateBy { it.id }
    }

    override suspend fun getBySubjectId(subjectId: Long): ReviewStatisticEntity? =
        statistics.value.values.firstOrNull { it.subjectId == subjectId }

    override fun observeAll(): Flow<List<ReviewStatisticEntity>> = statistics.map { it.values.toList() }
}

class FakeStudyMaterialDao : StudyMaterialDao {
    private val materials = MutableStateFlow<Map<Long, StudyMaterialEntity>>(emptyMap())

    override suspend fun upsertAll(materials: List<StudyMaterialEntity>) {
        this.materials.value = this.materials.value + materials.associateBy { it.id }
    }

    override suspend fun getBySubjectId(subjectId: Long): StudyMaterialEntity? =
        materials.value.values.firstOrNull { it.subjectId == subjectId }

    override fun observeAll(): Flow<List<StudyMaterialEntity>> = materials.map { it.values.toList() }
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
}

class FakeReviewLogDao : ReviewLogDao {
    private val entries = MutableStateFlow<List<ReviewLogEntity>>(emptyList())

    override suspend fun insert(entry: ReviewLogEntity) {
        entries.value = entries.value + entry.copy(id = entries.value.size + 1L)
    }

    override fun observeDailyCounts(sinceIso: String): Flow<List<DailyReviewCount>> = entries.map { list ->
        list.filter { it.reviewedAt >= sinceIso }
            .groupingBy { it.reviewedAt.substring(0, 10) }
            .eachCount()
            .map { (day, count) -> DailyReviewCount(day, count) }
    }

    override fun observeCountSince(sinceIso: String): Flow<Int> = entries.map { list ->
        list.count { it.reviewedAt >= sinceIso }
    }

    override fun observeTotalCount(): Flow<Int> = entries.map { it.size }
}
