package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.shared.database.AssignmentDao
import com.crazyfluff.shellfstudy.shared.database.ContextSentenceDao
import com.crazyfluff.shellfstudy.shared.database.AssignmentEntity
import com.crazyfluff.shellfstudy.shared.database.UpcomingAssignmentRow
import com.crazyfluff.shellfstudy.shared.database.KanjiLevelUpRow
import com.crazyfluff.shellfstudy.shared.database.LevelUpPathRow
import com.crazyfluff.shellfstudy.shared.database.LevelUpProjectionRow
import com.crazyfluff.shellfstudy.shared.database.LevelProgressItemRow
import com.crazyfluff.shellfstudy.shared.database.SentenceMatchRow
import com.crazyfluff.shellfstudy.shared.database.SrsStageTypeCount
import com.crazyfluff.shellfstudy.shared.database.StudyMaterialEntity
import com.crazyfluff.shellfstudy.shared.database.SubjectDao
import com.crazyfluff.shellfstudy.shared.database.SubjectAudioRow
import com.crazyfluff.shellfstudy.shared.database.SubjectEntity
import com.crazyfluff.shellfstudy.shared.database.SubjectReadingKeyRow
import com.crazyfluff.shellfstudy.shared.database.SubjectTypeCount
import com.crazyfluff.shellfstudy.shared.database.friends.FriendStatsDao
import com.crazyfluff.shellfstudy.shared.database.friends.FriendStatsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** In-memory stand-in for [SubjectDao] used by repository/ViewModel unit tests. */
/** [studyMaterials] stands in for the `study_materials` table the real search query also matches
 *  synonyms in; a DAO built without one searches subjects alone. */
class FakeSubjectDao(
    private val writeLog: SyncWriteLog? = null,
    private val studyMaterials: Flow<List<StudyMaterialEntity>> = flowOf(emptyList())
) : SubjectDao {
    private val subjects = MutableStateFlow<Map<Long, SubjectEntity>>(emptyMap())

    /** Stands in for the `assignments` table the "Unlocked" filter checks; wired to a
     *  [FakeAssignmentDao] by `buildTestRepositories`, which is built after this DAO. */
    var unlockedSubjectIds: Flow<Set<Long>> = flowOf(emptySet())

    override suspend fun upsertAll(subjects: List<SubjectEntity>) {
        writeLog?.record("subjects", subjects.size)
        this.subjects.value = this.subjects.value + subjects.associateBy { it.id }
    }

    override fun observeByIds(ids: List<Long>): Flow<List<SubjectEntity>> =
        subjects.map { map -> ids.mapNotNull { map[it] } }

    override suspend fun getById(id: Long): SubjectEntity? = subjects.value[id]

    override fun observeSearch(query: String, subjectTypes: List<String>): Flow<List<SubjectEntity>> =
        search(query, subjectTypes, unlockedOnly = false)

    override fun observeUnlockedSearch(query: String, subjectTypes: List<String>): Flow<List<SubjectEntity>> =
        search(query, subjectTypes, unlockedOnly = true)

    private fun search(query: String, subjectTypes: List<String>, unlockedOnly: Boolean): Flow<List<SubjectEntity>> {
        val unescaped = unescapeLike(query)
        return combine(subjects, studyMaterials, unlockedSubjectIds) { map, materials, unlocked ->
            val synonymMatches = materials
                .filter { material -> material.meaningSynonyms.any { it.contains(unescaped, ignoreCase = true) } }
                .map { it.subjectId }
                .toSet()
            map.values
                .filter { it.searchTarget.contains(unescaped, ignoreCase = true) || it.id in synonymMatches }
                .filter { it.subjectType in subjectTypes && (!unlockedOnly || it.id in unlocked) }
                .take(200)
        }
    }

    /** Every stored subject — what [FakeContextSentenceDao] derives its sentences from, as the real
     *  `context_sentences` table is derived from `subjects` by triggers. */
    fun observeAll(): Flow<Collection<SubjectEntity>> = subjects.map { it.values }

    override fun observeByPrimaryReadingKeys(readingKeys: List<String>): Flow<List<SubjectReadingKeyRow>> =
        subjects.map { map ->
            map.values
                .filter { it.primaryReadingKey.isNotEmpty() && it.primaryReadingKey in readingKeys }
                .sortedWith(compareBy({ it.level }, { it.id }))
                .map { SubjectReadingKeyRow(it.id, it.subjectType, it.primaryReadingKey) }
        }

    override fun observeTotalCount(): Flow<Int> = subjects.map { it.size }

    override fun observeTotalCountsByType(): Flow<List<SubjectTypeCount>> = subjects.map { map ->
        map.values.groupingBy { it.subjectType }.eachCount().map { (type, count) -> SubjectTypeCount(type, count) }
    }

    /** How many times [getAudioRows] ran — it decodes every vocabulary item's clips on a real device. */
    var audioRowsLoads = 0
        private set

    override suspend fun getAudioRows(): List<SubjectAudioRow> =
        subjects.value.values.also { audioRowsLoads++ }
            .filter { it.subjectType == "vocabulary" || it.subjectType == "kana_vocabulary" }
            .map { SubjectAudioRow(it.level, it.pronunciationAudios) }

    override suspend fun getCharacterImageUrlsAtLevel(level: Int): List<String> =
        subjects.value.values.filter { it.level == level }.mapNotNull { it.characterImageUrl }

    /** Test-only helper so [FakeAssignmentDao] can enumerate every (non-hidden) subject at a level
     *  for its subject-driven, left-join-style level progress/level-up queries — mirroring the real
     *  DAO's need to surface subjects with no assignment row yet as locked. */
    fun subjectsAtLevel(level: Int): List<SubjectEntity> =
        subjects.value.values.filter { it.level == level && it.hiddenAt == null }
}

/** In-memory stand-in for [AssignmentDao] used by repository/ViewModel unit tests. */
class FakeAssignmentDao(
    private val subjectsAtLevel: (Int) -> List<SubjectEntity> = { emptyList() },
    private val writeLog: SyncWriteLog? = null
) : AssignmentDao {
    private val assignments = MutableStateFlow<Map<Long, AssignmentEntity>>(emptyMap())

    override suspend fun upsertAll(assignments: List<AssignmentEntity>) {
        writeLog?.record("assignments", assignments.size)
        this.assignments.value = this.assignments.value + assignments.associateBy { it.id }
    }

    override suspend fun clearAll() {
        assignments.value = emptyMap()
    }

    override suspend fun getById(id: Long): AssignmentEntity? = assignments.value[id]

    /** Subjects with a visible assignment — what the real search's "Unlocked" `EXISTS` matches. */
    fun observeVisibleSubjectIds(): Flow<Set<Long>> =
        assignments.map { map -> map.values.filter { !it.hidden }.map { it.subjectId }.toSet() }

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

    override fun observeDueForReviewCount(nowIso: String): Flow<Int> =
        observeDueForReview(nowIso).map { it.size }

    override fun observeDueForLesson(): Flow<List<AssignmentEntity>> = assignments.map { map ->
        map.values.filter { !it.hidden && it.unlockedAt != null && it.startedAt == null }
    }

    override fun observeDueForLessonCount(): Flow<Int> =
        observeDueForLesson().map { it.size }

    override fun observeUpcoming(nowIso: String): Flow<List<UpcomingAssignmentRow>> = assignments.map { map ->
        map.values.filter {
            val availableAt = it.availableAt
            !it.hidden && availableAt != null && availableAt > nowIso
        }.map { UpcomingAssignmentRow(it.availableAt, it.subjectType, it.srsStage) }
    }

    override fun observeAvailableNow(nowIso: String): Flow<List<UpcomingAssignmentRow>> = assignments.map { map ->
        map.values.filter {
            val availableAt = it.availableAt
            !it.hidden && availableAt != null && availableAt <= nowIso
        }.map { UpcomingAssignmentRow(it.availableAt, it.subjectType, it.srsStage) }
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

    override fun observeLevelUpPathRows(level: Int): Flow<List<LevelUpPathRow>> = assignments.map { map ->
        subjectsAtLevel(level)
            .filter { it.subjectType == "radical" || it.subjectType == "kanji" }
            .map { subject ->
                val assignment = visibleAssignmentFor(subject.id, map)
                LevelUpPathRow(
                    subjectId = subject.id,
                    subjectType = subject.subjectType,
                    srsSystemId = subject.srsSystemId,
                    componentSubjectIds = subject.componentSubjectIds,
                    srsStage = assignment?.srsStage ?: 0,
                    unlockedAt = assignment?.unlockedAt,
                    availableAt = assignment?.availableAt
                )
            }
    }

    override fun observeLevelUpProjectionRows(fromLevel: Int, toLevel: Int): Flow<List<LevelUpProjectionRow>> =
        assignments.map {
            (fromLevel..toLevel).flatMap { level ->
                subjectsAtLevel(level)
                    .filter { it.subjectType == "radical" || it.subjectType == "kanji" }
                    .map { LevelUpProjectionRow(level, it.id, it.subjectType, it.srsSystemId, it.componentSubjectIds) }
            }
        }

    override fun observeAllStartedTimestamps(): Flow<List<String>> = assignments.map { map ->
        map.values.mapNotNull { if (!it.hidden) it.startedAt else null }
    }

    /** Mirrors the real DAO's lexicographic `startedAt >= :sinceIso` comparison — canonical
     *  WaniKani timestamps sort correctly as strings, so a plain string compare is faithful. */
    override fun observeStartedSinceCount(sinceIso: String): Flow<Int> = assignments.map { map ->
        map.values.count { !it.hidden && it.startedAt?.let { startedAt -> startedAt >= sinceIso } == true }
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

/** In-memory stand-in for [ContextSentenceDao], reading the sentences straight off [subjectDao]'s
 *  subjects — the real table is kept in step with `subjects` by triggers, so the two can't differ. */
class FakeContextSentenceDao(
    private val subjectDao: FakeSubjectDao,
    private val unlockedSubjectIds: Flow<Set<Long>> = flowOf(emptySet())
) : ContextSentenceDao {
    override fun observeSearch(query: String): Flow<List<SentenceMatchRow>> = search(query, unlockedOnly = false)

    override fun observeUnlockedSearch(query: String): Flow<List<SentenceMatchRow>> = search(query, unlockedOnly = true)

    // The real query takes the lowercased query as-is (`instr`, no wildcards), so no unescaping here.
    private fun search(query: String, unlockedOnly: Boolean): Flow<List<SentenceMatchRow>> =
        combine(subjectDao.observeAll(), unlockedSubjectIds) { subjects, unlocked ->
            subjects
                .filter { !unlockedOnly || it.id in unlocked }
                .sortedWith(compareBy({ it.level }, { it.id }))
                .flatMap { subject ->
                    subject.contextSentences
                        .filter { it.ja.contains(query) || it.en.contains(query, ignoreCase = true) }
                        .map { SentenceMatchRow(subject.id, subject.characters, subject.level, it.ja, it.en) }
                }
                .take(200)
        }
}

// The real subject queries take a caller-pre-escaped string (backslash-escaped %, _ and \) meant for
// `LIKE ... ESCAPE '\'`, matching those characters literally instead of as SQL wildcards — unescape
// first so the fake's plain `contains` mirrors that same literal-match behavior instead of searching
// for the escape backslashes themselves.
private fun unescapeLike(query: String) = query.replace("\\_", "_").replace("\\%", "%").replace("\\\\", "\\")
