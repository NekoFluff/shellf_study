package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.shared.database.AssignmentDao
import com.crazyfluff.shellfstudy.shared.database.AssignmentEntity
import com.crazyfluff.shellfstudy.shared.database.KanjiLevelUpRow
import com.crazyfluff.shellfstudy.shared.database.LevelProgressItemRow
import com.crazyfluff.shellfstudy.shared.database.SrsStageTypeCount
import com.crazyfluff.shellfstudy.shared.database.SubjectDao
import com.crazyfluff.shellfstudy.shared.database.SubjectEntity
import com.crazyfluff.shellfstudy.shared.database.SubjectTypeCount
import com.crazyfluff.shellfstudy.shared.database.friends.FriendStatsDao
import com.crazyfluff.shellfstudy.shared.database.friends.FriendStatsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

    /** Test-only helper so [FakeAssignmentDao] can enumerate every (non-hidden) subject at a level
     *  for its subject-driven, left-join-style level progress/level-up queries — mirroring the real
     *  DAO's need to surface subjects with no assignment row yet as locked. */
    fun subjectsAtLevel(level: Int): List<SubjectEntity> =
        subjects.value.values.filter { it.level == level && it.hiddenAt == null }
}

/** In-memory stand-in for [AssignmentDao] used by repository/ViewModel unit tests. */
class FakeAssignmentDao(
    private val subjectsAtLevel: (Int) -> List<SubjectEntity> = { emptyList() }
) : AssignmentDao {
    private val assignments = MutableStateFlow<Map<Long, AssignmentEntity>>(emptyMap())

    override suspend fun upsertAll(assignments: List<AssignmentEntity>) {
        this.assignments.value = this.assignments.value + assignments.associateBy { it.id }
    }

    override suspend fun clearAll() {
        assignments.value = emptyMap()
    }

    override suspend fun getById(id: Long): AssignmentEntity? = assignments.value[id]

    override suspend fun getByIds(ids: List<Long>): List<AssignmentEntity> =
        ids.mapNotNull { assignments.value[it] }

    override fun observeBySubjectId(subjectId: Long): Flow<AssignmentEntity?> = assignments.map { map ->
        map.values.firstOrNull { it.subjectId == subjectId }
    }

    override fun observeDueForReview(nowIso: String): Flow<List<AssignmentEntity>> = assignments.map { map ->
        map.values.filter {
            val availableAt = it.availableAt
            !it.hidden && availableAt != null && availableAt <= nowIso
        }
    }

    override fun observeDueForLesson(): Flow<List<AssignmentEntity>> = assignments.map { map ->
        map.values.filter { !it.hidden && it.unlockedAt != null && it.startedAt == null }
    }

    override fun observeUpcoming(nowIso: String): Flow<List<AssignmentEntity>> = assignments.map { map ->
        map.values.filter {
            val availableAt = it.availableAt
            !it.hidden && availableAt != null && availableAt > nowIso
        }
    }

    override fun observeSrsStageAndTypeCounts(): Flow<List<SrsStageTypeCount>> = assignments.map { map ->
        map.values.filter { !it.hidden && it.startedAt != null }
            .groupingBy { it.srsStage to it.subjectType }
            .eachCount()
            .map { (key, count) -> SrsStageTypeCount(key.first, key.second, count) }
    }

    private fun visibleAssignmentFor(subjectId: Long, map: Map<Long, AssignmentEntity>): AssignmentEntity? =
        map.values.firstOrNull { it.subjectId == subjectId && !it.hidden }

    override fun observeLevelProgressItemRows(level: Int): Flow<List<LevelProgressItemRow>> = assignments.map { map ->
        subjectsAtLevel(level).map { subject ->
            val assignment = visibleAssignmentFor(subject.id, map)
            LevelProgressItemRow(
                subjectId = subject.id,
                subjectType = subject.subjectType,
                characters = subject.characters,
                characterImageUrl = subject.characterImageUrl,
                slug = subject.slug,
                passedAt = assignment?.passedAt,
                srsStage = assignment?.srsStage ?: 0
            )
        }
    }

    override fun observeItemsSeenCount(): Flow<Int> = assignments.map { map ->
        map.values.count { !it.hidden && it.startedAt != null }
    }

    override fun observeKanjiLevelUpRows(level: Int): Flow<List<KanjiLevelUpRow>> = assignments.map { map ->
        subjectsAtLevel(level)
            .filter { it.subjectType == "kanji" }
            .map { subject -> KanjiLevelUpRow(visibleAssignmentFor(subject.id, map)?.srsStage ?: 0) }
    }

    override fun observeAllStartedTimestamps(): Flow<List<String>> = assignments.map { map ->
        map.values.mapNotNull { if (!it.hidden) it.startedAt else null }
    }

    override fun observeAllBurnedTimestamps(): Flow<List<String>> = assignments.map { map ->
        map.values.mapNotNull { if (!it.hidden && it.srsStage == 9) it.passedAt else null }
    }
}

class FakeFriendStatsDao : FriendStatsDao {
    private val entities = MutableStateFlow<Map<String, FriendStatsEntity>>(emptyMap())

    override suspend fun upsert(entity: FriendStatsEntity) {
        entities.value = entities.value + (entity.friendId to entity)
    }

    override suspend fun getById(id: String): FriendStatsEntity? = entities.value[id]

    override fun observeAll(): Flow<List<FriendStatsEntity>> = entities.map { it.values.toList() }

    override suspend fun deleteById(id: String) {
        entities.value = entities.value - id
    }
}
