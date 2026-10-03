package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.ContextSentence
import com.crazyfluff.shellfstudy.shared.data.model.SubjectDetail
import com.crazyfluff.shellfstudy.shared.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.shared.data.model.toPronunciationAudios
import com.crazyfluff.shellfstudy.shared.database.SrsSystemDao
import com.crazyfluff.shellfstudy.shared.database.SrsSystemEntity
import com.crazyfluff.shellfstudy.shared.database.SubjectDao
import com.crazyfluff.shellfstudy.shared.database.SubjectEntity
import com.crazyfluff.shellfstudy.shared.database.SubjectReadingKeyRow
import com.crazyfluff.shellfstudy.shared.database.SyncStateDao
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentUiState
import com.crazyfluff.shellfstudy.shared.network.CharacterImageData
import com.crazyfluff.shellfstudy.shared.network.ReadingData
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.crazyfluff.shellfstudy.shared.network.WaniKaniApi
import com.crazyfluff.shellfstudy.shared.network.collectAllPages
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.days

private val SUBJECTS_STALENESS = 1.days

/** Owns subjects and SRS systems — the full WaniKani content library. */
class SubjectRepository(
    private val api: WaniKaniApi,
    private val subjectDao: SubjectDao,
    private val srsSystemDao: SrsSystemDao,
    private val syncStateDao: SyncStateDao,
    private val pitchAccentRepository: PitchAccentRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val _isSyncingSubjectLibrary = MutableStateFlow(false)
    fun observeIsSyncingSubjectLibrary(): Flow<Boolean> = _isSyncingSubjectLibrary.asStateFlow()

    suspend fun syncSubjects(force: Boolean = false): ApiResult<Unit> = safeApiCall {
        val sync = fetchSubjects(force) ?: return@safeApiCall
        withSubjectLibrarySyncFlag { completeResourceSync(syncStateDao, SyncResources.SUBJECTS, sync) }
    }

    /**
     * Fetches the subject library without writing it — the first half of [syncSubjects], exposed so
     * [com.crazyfluff.shellfstudy.shared.sync.SyncOrchestrator] can fetch several resources and then
     * write them all inside one transaction. Null when the library is fresh enough to skip.
     */
    internal suspend fun fetchSubjects(force: Boolean = false): ResourceSync<List<SubjectEntity>>? =
        fetchResourceSync(
            syncStateDao = syncStateDao,
            resource = SyncResources.SUBJECTS,
            force = force,
            staleness = SUBJECTS_STALENESS,
            countRows = { it.size },
            fetch = { cursor ->
                collectAllPages(
                    firstPage = { api.getSubjects(updatedAfter = cursor) },
                    nextPage = { url -> api.getSubjectsPage(url) }
                ).map { item ->
                    SubjectEntity(
                        id = item.id,
                        subjectType = item.objectType,
                        level = item.data.level,
                        slug = item.data.slug,
                        characters = item.data.characters,
                        characterImageUrl = selectCharacterImageUrl(item.data.characterImages),
                        meanings = item.data.meanings,
                        readings = item.data.readings,
                        auxiliaryMeanings = item.data.auxiliaryMeanings,
                        documentUrl = item.data.documentUrl,
                        // Raw WK markup (<radical>/<kanji>/<reading>/etc.) is preserved here — it's
                        // parsed into colored spans at render time (see WkMnemonicText) rather than
                        // being stripped at sync time as it was previously.
                        meaningMnemonic = item.data.meaningMnemonic,
                        readingMnemonic = item.data.readingMnemonic,
                        meaningHint = item.data.meaningHint,
                        readingHint = item.data.readingHint,
                        lessonPosition = item.data.lessonPosition,
                        srsSystemId = item.data.srsSystemId,
                        componentSubjectIds = item.data.componentSubjectIds,
                        amalgamationSubjectIds = item.data.amalgamationSubjectIds,
                        visuallySimilarSubjectIds = item.data.visuallySimilarSubjectIds,
                        partsOfSpeech = item.data.partsOfSpeech,
                        contextSentences = item.data.contextSentences,
                        pronunciationAudios = item.data.pronunciationAudios,
                        hiddenAt = item.data.hiddenAt,
                        searchTarget = buildSearchTarget(
                            item.data.characters, item.data.slug, item.data.meanings.map { it.meaning },
                            item.data.readings.map { it.reading }
                        ),
                        primaryReadingKey = primaryReadingKey(item.data.readings)
                    )
                }
            },
            write = { subjectDao.upsertAll(it) }
        )

    /** Runs [block] with the subject-library syncing indicator held true for its duration. */
    private suspend fun <T> withSubjectLibrarySyncFlag(block: suspend () -> T): T {
        _isSyncingSubjectLibrary.value = true
        return try {
            block()
        } finally {
            _isSyncingSubjectLibrary.value = false
        }
    }

    /**
     * Fetches SRS systems without writing them — see [fetchSubjects]. SRS systems must land before
     * subjects (subjects reference `spaced_repetition_system_id`), which the orchestrator orders.
     */
    internal suspend fun fetchSrsSystems(force: Boolean = false): ResourceSync<List<SrsSystemEntity>>? =
        fetchResourceSync(
            syncStateDao = syncStateDao,
            resource = SyncResources.SRS_SYSTEMS,
            force = force,
            staleness = SUBJECTS_STALENESS,
            countRows = { it.size },
            fetch = { cursor ->
                api.getSpacedRepetitionSystems(updatedAfter = cursor).data.map { item ->
                    SrsSystemEntity(
                        id = item.id,
                        name = item.data.name,
                        unlockingStagePosition = item.data.unlockingStagePosition,
                        startingStagePosition = item.data.startingStagePosition,
                        passingStagePosition = item.data.passingStagePosition,
                        burningStagePosition = item.data.burningStagePosition,
                        stages = item.data.stages
                    )
                }
            },
            write = { srsSystemDao.upsertAll(it) }
        )

    fun observeSearch(query: String): Flow<List<SubjectSummary>> =
        subjectDao.observeSearch(escapeLikeWildcards(query.lowercase())).map { entities -> entities.map { it.toSubjectSummary() } }

    fun observeTotalSubjectCount(): Flow<Int> = subjectDao.observeTotalCount()

    /** Resolves a set of related-subject IDs (components/amalgamations/visually-similar) into tiles. */
    fun observeSubjectSummaries(ids: List<Long>): Flow<List<SubjectSummary>> =
        chunkedIds(ids) { subjectDao.observeByIds(it) }.map { entities -> entities.map { it.toSubjectSummary() } }

    /**
     * Flow-based so an open detail sheet live-updates if a background sync refreshes this subject.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSubjectDetail(subjectId: Long): Flow<SubjectDetail?> =
        subjectDao.observeByIds(listOf(subjectId)).flatMapLatest { entities ->
            val entity = entities.firstOrNull()
            val type = entity?.let { SubjectType.fromWkString(it.subjectType) }
            val characters = entity?.characters
            val pitchAccentsFlow: Flow<PitchAccentUiState> =
                if (characters != null && (type == SubjectType.VOCABULARY || type == SubjectType.KANA_VOCABULARY)) {
                    pitchAccentRepository.observePitchAccents(characters)
                } else {
                    // Kanji/radicals have no pitch accent to look up — a confirmed absence, not a
                    // pending one, so the detail view doesn't offer to wait for data that can't come.
                    flowOf(PitchAccentUiState.Unavailable)
                }
            val phoneticallySimilarFlow: Flow<List<Long>> =
                if (entity != null &&
                    entity.subjectType in PHONETICALLY_MATCHED_TYPES &&
                    entity.primaryReadingKey.isNotEmpty()
                ) {
                    subjectDao.observeByPrimaryReadingKeys(listOf(entity.primaryReadingKey))
                        .map { candidates -> phoneticallySimilarIds(entity, candidates) }
                } else {
                    // Kanji/radicals don't get the section — a confirmed absence, same as pitch
                    // accents above.
                    flowOf(emptyList())
                }
            combine(pitchAccentsFlow, phoneticallySimilarFlow) { pitchAccents, phoneticallySimilarIds ->
                entity?.toSubjectDetail(pitchAccents, phoneticallySimilarIds)
            }
        }.flowOn(defaultDispatcher)

    /**
     * The "phonetically similar" subject ids for each of [subjectIds], keyed by subject id — the
     * batch form of what [observeSubjectDetail] carries for one subject, for screens showing several
     * subjects at once (the lesson study cards). Live off Room, so a background sync that adds a
     * newly-cached match reaches the caller in place. A subject that isn't cached is absent from the
     * map; one with no matches maps to an empty list.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observePhoneticallySimilarIds(subjectIds: List<Long>): Flow<Map<Long, List<Long>>> =
        chunkedIds(subjectIds) { subjectDao.observeByIds(it) }.flatMapLatest { entities ->
            val readingKeys = entities
                .filter { it.subjectType in PHONETICALLY_MATCHED_TYPES && it.primaryReadingKey.isNotEmpty() }
                .map { it.primaryReadingKey }
                .distinct()
            chunkedIds(readingKeys) { subjectDao.observeByPrimaryReadingKeys(it) }.map { candidates ->
                val candidatesByKey = candidates.groupBy { it.primaryReadingKey }
                entities.associate { entity ->
                    entity.id to phoneticallySimilarIds(entity, candidatesByKey[entity.primaryReadingKey].orEmpty())
                }
            }
        }.flowOn(defaultDispatcher)
}

/** The subject types (WK object type strings) that get a "phonetically similar" section, and that
 *  count as a match for one — vocabulary of either kind. Kanji and radicals get none. */
private val PHONETICALLY_MATCHED_TYPES = setOf("vocabulary", "kana_vocabulary")

/** The ids among [candidates] that are phonetically similar to [subject]: vocabulary with the same
 *  primary reading, other than [subject] itself. Empty for anything but vocabulary. */
private fun phoneticallySimilarIds(subject: SubjectEntity, candidates: List<SubjectReadingKeyRow>): List<Long> =
    if (subject.subjectType !in PHONETICALLY_MATCHED_TYPES || subject.primaryReadingKey.isEmpty()) {
        emptyList()
    } else {
        candidates
            .filter {
                it.id != subject.id && it.subjectType in PHONETICALLY_MATCHED_TYPES &&
                    it.primaryReadingKey == subject.primaryReadingKey
            }
            .map { it.id }
    }

private fun buildSearchTarget(characters: String?, slug: String, meanings: List<String>, readings: List<String>): String =
    (listOfNotNull(characters) + slug + meanings + readings).joinToString(" ").lowercase()

/** Escapes a raw search string for use inside [SubjectDao.observeSearch]'s `LIKE ... ESCAPE '\'`
 *  pattern, so a literal `%` or `_` typed by the user (e.g. searching for a romanization
 *  containing an underscore) matches that literal character instead of being treated as a SQL
 *  wildcard. Order matters: the escape character itself must be escaped first, or a `\` inserted
 *  by escaping a later `%`/`_` would itself be re-escaped — which is the case
 *  EscapeLikeWildcardsTest pins down, alongside SubjectSearchQueryTest running the escaped pattern
 *  through real SQLite. */
internal fun escapeLikeWildcards(query: String): String =
    query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

/** Katakana-normalized primary reading, for exact-match "phonetically similar" lookups — falls back
 *  to the first reading if none is marked primary, and to "" if there are no readings at all. */
private fun primaryReadingKey(readings: List<ReadingData>): String =
    (readings.firstOrNull { it.primary } ?: readings.firstOrNull())?.reading?.toKatakana() ?: ""

/** WaniKani only ever supplies character_images as SVG; the ImageLoader has an SvgDecoder registered. */
private fun selectCharacterImageUrl(images: List<CharacterImageData>): String? =
    images.firstOrNull { it.contentType == "image/svg+xml" }?.url

private fun SubjectEntity.toSubjectSummary(): SubjectSummary = SubjectSummary(
    subjectId = id,
    subjectType = SubjectType.fromWkString(subjectType),
    characters = characters,
    characterImageUrl = characterImageUrl,
    level = level,
    meanings = meanings.map { it.meaning },
    readings = readings.map { it.reading }
)

private fun SubjectEntity.toSubjectDetail(
    pitchAccents: PitchAccentUiState = PitchAccentUiState.Unavailable,
    phoneticallySimilarSubjectIds: List<Long> = emptyList()
): SubjectDetail {
    val readingsByType = readings.groupBy { it.type }
    return SubjectDetail(
        subjectId = id,
        subjectType = SubjectType.fromWkString(subjectType),
        characters = characters,
        characterImageUrl = characterImageUrl,
        level = level,
        meanings = meanings.map { it.meaning },
        auxiliaryMeanings = auxiliaryMeanings.map { it.meaning },
        readings = readings.map { it.reading },
        onyomiReadings = readingsByType["onyomi"].orEmpty().map { it.reading },
        kunyomiReadings = readingsByType["kunyomi"].orEmpty().map { it.reading },
        nanoriReadings = readingsByType["nanori"].orEmpty().map { it.reading },
        documentUrl = documentUrl,
        meaningMnemonic = meaningMnemonic,
        meaningHint = meaningHint,
        readingMnemonic = readingMnemonic,
        readingHint = readingHint,
        partsOfSpeech = partsOfSpeech,
        contextSentences = contextSentences.map { ContextSentence(japanese = it.ja, english = it.en) },
        componentSubjectIds = componentSubjectIds,
        amalgamationSubjectIds = amalgamationSubjectIds,
        visuallySimilarSubjectIds = visuallySimilarSubjectIds,
        phoneticallySimilarSubjectIds = phoneticallySimilarSubjectIds,
        pitchAccents = pitchAccents,
        pronunciationAudios = toPronunciationAudios()
    )
}
