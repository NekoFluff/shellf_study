package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.core.database.AssignmentDao
import com.crazyfluff.shellfstudy.core.database.AssignmentEntity
import com.crazyfluff.shellfstudy.core.database.KanjiLevelUpRow
import com.crazyfluff.shellfstudy.core.database.LevelProgressRow
import com.crazyfluff.shellfstudy.core.database.SrsStageCount
import com.crazyfluff.shellfstudy.core.database.SubjectDao
import com.crazyfluff.shellfstudy.core.database.SubjectEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory stand-in for [SubjectDao] used by repository/ViewModel unit tests. */
class FakeSubjectDao : SubjectDao {
    private val subjects = MutableStateFlow<Map<Long, SubjectEntity>>(emptyMap())

    override suspend fun upsertAll(subjects: List<SubjectEntity>) {
        this.subjects.value = this.subjects.value + subjects.associateBy { it.id }
    }

    override fun observeByIds(ids: List<Long>): Flow<List<SubjectEntity>> =
        subjects.map { map -> ids.mapNotNull { map[it] } }

    override suspend fun getById(id: Long): SubjectEntity? = subjects.value[id]

    override fun observeAll(): Flow<List<SubjectEntity>> = subjects.map { it.values.toList() }

    override fun observeSearch(query: String): Flow<List<SubjectEntity>> =
        subjects.map { map -> map.values.filter { it.searchTarget.contains(query, ignoreCase = true) }.take(200) }

    override fun observeTotalCount(): Flow<Int> = subjects.map { it.size }

    /** Test-only helper so [FakeAssignmentDao] can resolve a subject's level for join-style queries. */
    fun levelOf(subjectId: Long): Int? = subjects.value[subjectId]?.level
}

/** In-memory stand-in for [AssignmentDao] used by repository/ViewModel unit tests. */
class FakeAssignmentDao(
    private val subjectLevelLookup: (Long) -> Int? = { null }
) : AssignmentDao {
    private val assignments = MutableStateFlow<Map<Long, AssignmentEntity>>(emptyMap())

    override suspend fun upsertAll(assignments: List<AssignmentEntity>) {
        this.assignments.value = this.assignments.value + assignments.associateBy { it.id }
    }

    override fun observeDueForReview(nowIso: String): Flow<List<AssignmentEntity>> = assignments.map { map ->
        map.values.filter { !it.hidden && it.availableAt != null && it.availableAt <= nowIso }
    }

    override fun observeDueForLesson(): Flow<List<AssignmentEntity>> = assignments.map { map ->
        map.values.filter { !it.hidden && it.unlockedAt != null && it.startedAt == null }
    }

    override fun observeUpcoming(nowIso: String): Flow<List<AssignmentEntity>> = assignments.map { map ->
        map.values.filter { !it.hidden && it.availableAt != null && it.availableAt > nowIso }
    }

    override fun observeSrsStageCounts(): Flow<List<SrsStageCount>> = assignments.map { map ->
        map.values.filter { !it.hidden && it.startedAt != null }
            .groupingBy { it.srsStage }
            .eachCount()
            .map { (stage, count) -> SrsStageCount(stage, count) }
    }

    override fun observeLevelProgressRows(level: Int): Flow<List<LevelProgressRow>> = assignments.map { map ->
        map.values
            .filter { !it.hidden && it.unlockedAt != null && subjectLevelLookup(it.subjectId) == level }
            .map { LevelProgressRow(it.subjectType, it.passedAt) }
    }

    override fun observeItemsSeenCount(): Flow<Int> = assignments.map { map ->
        map.values.count { !it.hidden && it.startedAt != null }
    }

    override fun observeStartedTodayCount(startOfDayIso: String): Flow<Int> = assignments.map { map ->
        map.values.count { !it.hidden && it.startedAt != null && it.startedAt >= startOfDayIso }
    }

    override fun observeKanjiLevelUpRows(level: Int): Flow<List<KanjiLevelUpRow>> = assignments.map { map ->
        map.values
            .filter { !it.hidden && it.subjectType == "kanji" && subjectLevelLookup(it.subjectId) == level }
            .map { KanjiLevelUpRow(it.srsStage) }
    }
}
