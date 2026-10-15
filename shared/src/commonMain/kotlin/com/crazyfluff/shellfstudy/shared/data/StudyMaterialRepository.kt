package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.database.StudyMaterialDao
import com.crazyfluff.shellfstudy.shared.database.StudyMaterialEntity
import com.crazyfluff.shellfstudy.shared.database.SyncStateDao
import com.crazyfluff.shellfstudy.shared.network.StudyMaterialBody
import com.crazyfluff.shellfstudy.shared.network.StudyMaterialData
import com.crazyfluff.shellfstudy.shared.network.StudyMaterialRequest
import com.crazyfluff.shellfstudy.shared.network.WaniKaniApi
import com.crazyfluff.shellfstudy.shared.network.WkResourceItem
import com.crazyfluff.shellfstudy.shared.network.collectAllPages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reading and editing the user's own meaning synonyms — the slice of [StudyMaterialRepository] the
 * subject views need. An interface so a screen test can hand the views a fake without a database.
 */
interface UserSynonymsStore {
    fun observeSynonyms(subjectId: Long): Flow<List<String>>

    /** Every subject's synonyms, keyed by subject id — the quiz grading path's live source, so a
     *  synonym added mid-session counts for the very next answer. */
    fun observeAllSynonyms(): Flow<Map<Long, List<String>>>

    /** Adds [synonym] (trimmed, inner whitespace collapsed); a case-insensitive repeat is a no-op. */
    suspend fun addSynonym(subjectId: Long, synonym: String): ApiResult<Unit>

    suspend fun removeSynonym(subjectId: Long, synonym: String): ApiResult<Unit>
}

/**
 * Owns WaniKani study materials, of which the app uses only the meaning synonyms: grading accepts them
 * like any of WaniKani's own meanings, and every subject view can add or remove them.
 *
 * ## Edits
 *
 * An edit shows at once — the local row is rewritten before the request goes out — and is rolled back
 * if WaniKani refuses it or the device is offline. There is no outbox for synonyms: an edit the user
 * just made and watched fail is better reported there and then than retried behind their back.
 *
 * Each edit reads the subject's current material from WaniKani first and applies the change to *that*
 * list. WaniKani's update replaces the whole list, and the local mirror can be up to a sync behind, so
 * writing the local list back would silently drop a synonym added on the website since the last sync.
 * Edits run one at a time for the same reason: two quick additions must not each start from a list
 * without the other.
 */
class StudyMaterialRepository(
    private val api: WaniKaniApi,
    private val studyMaterialDao: StudyMaterialDao,
    private val syncStateDao: SyncStateDao
) : UserSynonymsStore {
    private val editMutex = Mutex()

    suspend fun syncStudyMaterials(force: Boolean = false): ApiResult<Unit> = safeApiCall {
        fetchStudyMaterials(force)?.let { completeResourceSync(syncStateDao, SyncResources.STUDY_MATERIALS, it) }
    }

    /** Fetches study materials without writing them, for the same reason as
     *  [AssignmentRepository.fetchAssignments]. Null when they are fresh enough to skip. */
    internal suspend fun fetchStudyMaterials(force: Boolean = false): ResourceSync<List<StudyMaterialEntity>>? =
        fetchResourceSync(
            syncStateDao = syncStateDao,
            resource = SyncResources.STUDY_MATERIALS,
            force = force,
            staleness = ASSIGNMENTS_STALENESS,
            countRows = { it.size },
            fetch = { cursor ->
                collectAllPages(
                    firstPage = { api.getStudyMaterials(updatedAfter = cursor) },
                    nextPage = { url -> api.getStudyMaterialsPage(url) }
                ).map { it.toEntity() }
            },
            write = { rows -> studyMaterialDao.upsertAll(rows) }
        )

    override fun observeAllSynonyms(): Flow<Map<Long, List<String>>> =
        studyMaterialDao.observeAll().map { rows -> rows.associate { it.subjectId to it.meaningSynonyms } }

    override fun observeSynonyms(subjectId: Long): Flow<List<String>> =
        studyMaterialDao.observeBySubjectId(subjectId).map { it?.meaningSynonyms.orEmpty() }.distinctUntilChanged()

    override suspend fun addSynonym(subjectId: Long, synonym: String): ApiResult<Unit> {
        val cleaned = synonym.trim().replace(WHITESPACE_RUN, " ")
        if (cleaned.isEmpty()) return ApiResult.Error("Type a synonym first.")
        return edit(subjectId) { current ->
            if (current.any { it.equals(cleaned, ignoreCase = true) }) current else current + cleaned
        }
    }

    override suspend fun removeSynonym(subjectId: Long, synonym: String): ApiResult<Unit> =
        edit(subjectId) { current -> current.filterNot { it.equals(synonym, ignoreCase = true) } }

    private suspend fun edit(subjectId: Long, transform: (List<String>) -> List<String>): ApiResult<Unit> =
        editMutex.withLock {
            val before = studyMaterialDao.getBySubjectId(subjectId)
            val current = before?.meaningSynonyms.orEmpty()
            val optimistic = transform(current)
            if (optimistic == current) return@withLock ApiResult.Success(Unit)

            val placeholder = before?.copy(meaningSynonyms = optimistic)
                ?: StudyMaterialEntity(
                    id = -subjectId, subjectId = subjectId, meaningSynonyms = optimistic, hidden = false
                )
            studyMaterialDao.upsertAll(listOf(placeholder))

            when (val result = safeApiCall { pushEdit(subjectId, transform) }) {
                is ApiResult.Success -> {
                    studyMaterialDao.upsertAll(listOf(result.data.toEntity()))
                    ApiResult.Success(Unit)
                }
                is ApiResult.Error -> {
                    if (before != null) {
                        studyMaterialDao.upsertAll(listOf(before))
                    } else {
                        studyMaterialDao.deleteById(placeholder.id)
                    }
                    result.forSynonymEdit()
                }
            }
        }

    /** Applies [transform] to WaniKani's current list, creating the material if the subject has none. */
    private suspend fun pushEdit(
        subjectId: Long,
        transform: (List<String>) -> List<String>
    ): WkResourceItem<StudyMaterialData> {
        val remote = api.getStudyMaterials(subjectIds = listOf(subjectId)).data.firstOrNull()
        val base = remote?.data?.meaningSynonyms.orEmpty()
        val target = transform(base)
        return when {
            remote == null -> api.createStudyMaterial(
                StudyMaterialRequest(StudyMaterialBody(subjectId = subjectId, meaningSynonyms = target))
            )
            target == base -> remote
            else -> api.updateStudyMaterial(
                remote.id, StudyMaterialRequest(StudyMaterialBody(meaningSynonyms = target))
            )
        }
    }
}

/** The generic API errors, said in terms of what the user was doing. 403 is the one worth spelling
 *  out: a read-only token is common, and WaniKani answers it with nothing more than the status. */
private fun ApiResult.Error.forSynonymEdit(): ApiResult.Error = when (status) {
    null -> this
    HTTP_UNAUTHORIZED -> this
    HTTP_FORBIDDEN -> copy(
        message = "Your API token can't edit synonyms. Make a token with the study_materials:create " +
            "and study_materials:update permissions on WaniKani and log in with it."
    )
    HTTP_UNPROCESSABLE -> copy(message = "WaniKani didn't accept that synonym.")
    else -> copy(message = "Couldn't save the synonym — ${message.removeSuffix(".")}.")
}

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_UNPROCESSABLE = 422

private val WHITESPACE_RUN = Regex("\\s+")

internal fun WkResourceItem<StudyMaterialData>.toEntity(): StudyMaterialEntity = StudyMaterialEntity(
    id = id,
    subjectId = data.subjectId,
    meaningSynonyms = data.meaningSynonyms,
    hidden = data.hidden
)
