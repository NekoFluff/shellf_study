package com.crazyfluff.shellfstudy.core.data

import com.crazyfluff.shellfstudy.core.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.core.database.SrsSystemDao
import com.crazyfluff.shellfstudy.core.database.SrsSystemEntity
import com.crazyfluff.shellfstudy.core.database.StudyMaterialDao
import com.crazyfluff.shellfstudy.core.database.StudyMaterialEntity
import com.crazyfluff.shellfstudy.core.database.SubjectDao
import com.crazyfluff.shellfstudy.core.database.SubjectEntity
import com.crazyfluff.shellfstudy.core.database.SyncStateDao
import com.crazyfluff.shellfstudy.core.network.SubjectType
import com.crazyfluff.shellfstudy.core.network.WaniKaniApi
import com.crazyfluff.shellfstudy.core.network.collectAllPages
import com.crazyfluff.shellfstudy.core.util.stripWkMarkup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val RESOURCE_SUBJECTS = "subjects"
private const val RESOURCE_SRS_SYSTEMS = "srs_systems"
private const val RESOURCE_STUDY_MATERIALS = "study_materials"
private val SUBJECTS_STALENESS = Duration.ofDays(1)
private val STUDY_MATERIALS_STALENESS = Duration.ofHours(1)

/** Owns subjects, SRS systems, and study materials — the full WaniKani content library. */
@Singleton
class SubjectRepository @Inject constructor(
    private val api: WaniKaniApi,
    private val subjectDao: SubjectDao,
    private val srsSystemDao: SrsSystemDao,
    private val studyMaterialDao: StudyMaterialDao,
    private val syncStateDao: SyncStateDao
) {
    private val _isSyncingSubjectLibrary = MutableStateFlow(false)
    fun observeIsSyncingSubjectLibrary(): Flow<Boolean> = _isSyncingSubjectLibrary.asStateFlow()

    suspend fun syncSubjects(force: Boolean = false): ApiResult<Unit> {
        if (!shouldSync(syncStateDao, RESOURCE_SUBJECTS, force, SUBJECTS_STALENESS)) return ApiResult.Success(Unit)
        return safeApiCall {
            _isSyncingSubjectLibrary.value = true
            try {
                val cursor = syncCursor(syncStateDao, RESOURCE_SUBJECTS)
                val startedAt = Instant.now().toString()
                val items = collectAllPages(
                    firstPage = { api.getSubjects(updatedAfter = cursor) },
                    nextPage = { url -> api.getSubjectsPage(url) }
                )
                subjectDao.upsertAll(
                    items.map { item ->
                        SubjectEntity(
                            id = item.id,
                            subjectType = item.objectType,
                            level = item.data.level,
                            slug = item.data.slug,
                            characters = item.data.characters,
                            meanings = item.data.meanings,
                            readings = item.data.readings,
                            auxiliaryMeanings = item.data.auxiliaryMeanings,
                            documentUrl = item.data.documentUrl,
                            meaningMnemonic = item.data.meaningMnemonic?.let(::stripWkMarkup),
                            readingMnemonic = item.data.readingMnemonic?.let(::stripWkMarkup),
                            meaningHint = item.data.meaningHint?.let(::stripWkMarkup),
                            readingHint = item.data.readingHint?.let(::stripWkMarkup),
                            lessonPosition = item.data.lessonPosition,
                            srsSystemId = item.data.srsSystemId,
                            componentSubjectIds = item.data.componentSubjectIds,
                            amalgamationSubjectIds = item.data.amalgamationSubjectIds,
                            partsOfSpeech = item.data.partsOfSpeech,
                            hiddenAt = item.data.hiddenAt,
                            searchTarget = buildSearchTarget(
                                item.data.characters, item.data.slug, item.data.meanings.map { it.meaning },
                                item.data.readings.map { it.reading }
                            )
                        )
                    }
                )
                recordSyncSuccess(syncStateDao, RESOURCE_SUBJECTS, cursor = startedAt)
            } finally {
                _isSyncingSubjectLibrary.value = false
            }
        }
    }

    suspend fun syncSrsSystems(force: Boolean = false): ApiResult<Unit> {
        if (!shouldSync(syncStateDao, RESOURCE_SRS_SYSTEMS, force, SUBJECTS_STALENESS)) return ApiResult.Success(Unit)
        return safeApiCall {
            val cursor = syncCursor(syncStateDao, RESOURCE_SRS_SYSTEMS)
            val startedAt = Instant.now().toString()
            val response = api.getSpacedRepetitionSystems(updatedAfter = cursor)
            srsSystemDao.upsertAll(
                response.data.map { item ->
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
            )
            recordSyncSuccess(syncStateDao, RESOURCE_SRS_SYSTEMS, cursor = startedAt)
        }
    }

    suspend fun syncStudyMaterials(force: Boolean = false): ApiResult<Unit> {
        if (!shouldSync(syncStateDao, RESOURCE_STUDY_MATERIALS, force, STUDY_MATERIALS_STALENESS)) return ApiResult.Success(Unit)
        return safeApiCall {
            val cursor = syncCursor(syncStateDao, RESOURCE_STUDY_MATERIALS)
            val startedAt = Instant.now().toString()
            val items = collectAllPages(
                firstPage = { api.getStudyMaterials(updatedAfter = cursor) },
                nextPage = { url -> api.getStudyMaterialsPage(url) }
            )
            studyMaterialDao.upsertAll(
                items.map { item ->
                    StudyMaterialEntity(
                        id = item.id,
                        subjectId = item.data.subjectId,
                        subjectType = item.data.subjectType,
                        meaningNote = item.data.meaningNote,
                        readingNote = item.data.readingNote,
                        meaningSynonyms = item.data.meaningSynonyms,
                        hidden = item.data.hidden
                    )
                }
            )
            recordSyncSuccess(syncStateDao, RESOURCE_STUDY_MATERIALS, cursor = startedAt)
        }
    }

    fun observeSearch(query: String): Flow<List<SubjectSummary>> =
        subjectDao.observeSearch(query.lowercase()).map { entities -> entities.map { it.toSubjectSummary() } }

    fun observeTotalSubjectCount(): Flow<Int> = subjectDao.observeTotalCount()
}

private fun buildSearchTarget(characters: String?, slug: String, meanings: List<String>, readings: List<String>): String =
    (listOfNotNull(characters) + slug + meanings + readings).joinToString(" ").lowercase()

private fun SubjectEntity.toSubjectSummary(): SubjectSummary = SubjectSummary(
    subjectId = id,
    subjectType = SubjectType.fromWkString(subjectType),
    characters = characters,
    level = level,
    meanings = meanings.map { it.meaning },
    readings = readings.map { it.reading }
)
