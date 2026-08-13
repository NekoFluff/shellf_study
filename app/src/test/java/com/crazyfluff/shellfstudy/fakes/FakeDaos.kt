package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.core.database.AssignmentDao
import com.crazyfluff.shellfstudy.core.database.AssignmentEntity
import com.crazyfluff.shellfstudy.core.database.KanjiLevelUpRow
import com.crazyfluff.shellfstudy.core.database.LevelProgressItemRow
import com.crazyfluff.shellfstudy.core.database.SrsStageTypeCount
import com.crazyfluff.shellfstudy.core.database.SubjectDao
import com.crazyfluff.shellfstudy.core.database.SubjectEntity
import com.crazyfluff.shellfstudy.core.database.SubjectTypeCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory stand-in for [SubjectDao] used by repository/ViewModel unit tests. */
class FakeSubjectDao : SubjectDao {
    private val subjects = MutableStateFlow<Map<Long, SubjectEntity>>(emptyMap())
    private val unlockedIds = MutableStateFlow<Set<Long>>(emptySet())

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

    override fun observeTotalCountsByType(): Flow<List<SubjectTypeCount>> = subjects.map { map ->
        map.values.groupingBy { it.subjectType }.eachCount().map { (type, count) -> SubjectTypeCount(type, count) }
    }

    override suspend fun getUnlockedVocabularyCharacters(): List<String> = subjects.value.values
        .filter { (it.subjectType == "vocabulary" || it.subjectType == "kana_vocabulary") && it.id in unlockedIds.value }
        .mapNotNull { it.characters }

    /** Test-only helper mirroring the real DAO's join against assignments.unlockedAt/hidden. */
    fun markUnlocked(vararg ids: Long) {
        unlockedIds.value = unlockedIds.value + ids.toSet()
    }

    /** Test-only helper so [FakeAssignmentDao] can resolve a subject's level for join-style queries. */
    fun levelOf(subjectId: Long): Int? = subjects.value[subjectId]?.level

    /** Test-only helper so [FakeAssignmentDao] can resolve a subject's display fields for join-style queries. */
    fun entityOf(subjectId: Long): SubjectEntity? = subjects.value[subjectId]
}

/** In-memory stand-in for [AssignmentDao] used by repository/ViewModel unit tests. */
class FakeAssignmentDao(
    private val subjectLevelLookup: (Long) -> Int? = { null },
    private val subjectLookup: (Long) -> SubjectEntity? = { null }
) : AssignmentDao {
    private val assignments = MutableStateFlow<Map<Long, AssignmentEntity>>(emptyMap())

    override suspend fun upsertAll(assignments: List<AssignmentEntity>) {
        this.assignments.value = this.assignments.value + assignments.associateBy { it.id }
    }

    override suspend fun getById(id: Long): AssignmentEntity? = assignments.value[id]

    override fun observeBySubjectId(subjectId: Long): Flow<AssignmentEntity?> = assignments.map { map ->
        map.values.firstOrNull { it.subjectId == subjectId }
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

    override fun observeSrsStageAndTypeCounts(): Flow<List<SrsStageTypeCount>> = assignments.map { map ->
        map.values.filter { !it.hidden && it.startedAt != null }
            .groupingBy { it.srsStage to it.subjectType }
            .eachCount()
            .map { (key, count) -> SrsStageTypeCount(key.first, key.second, count) }
    }

    override fun observeLevelProgressItemRows(level: Int): Flow<List<LevelProgressItemRow>> = assignments.map { map ->
        map.values
            .filter { !it.hidden && it.unlockedAt != null && subjectLevelLookup(it.subjectId) == level }
            .map { assignment ->
                val subject = subjectLookup(assignment.subjectId)
                LevelProgressItemRow(
                    subjectId = assignment.subjectId,
                    subjectType = assignment.subjectType,
                    characters = subject?.characters,
                    characterImageUrl = subject?.characterImageUrl,
                    slug = subject?.slug.orEmpty(),
                    passedAt = assignment.passedAt,
                    srsStage = assignment.srsStage
                )
            }
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
