package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.UserSynonymsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [UserSynonymsStore] for screen tests; set [failWith] to make every edit fail with it. */
class FakeUserSynonymsStore(initial: Map<Long, List<String>> = emptyMap()) : UserSynonymsStore {
    val synonyms = MutableStateFlow(initial)
    var failWith: String? = null

    override fun observeSynonyms(subjectId: Long): Flow<List<String>> = synonyms.map { it[subjectId].orEmpty() }

    override fun observeAllSynonyms(): Flow<Map<Long, List<String>>> = synonyms

    override suspend fun addSynonym(subjectId: Long, synonym: String): ApiResult<Unit> =
        edit(subjectId) { it + synonym.trim() }

    override suspend fun removeSynonym(subjectId: Long, synonym: String): ApiResult<Unit> =
        edit(subjectId) { it - synonym }

    private fun edit(subjectId: Long, transform: (List<String>) -> List<String>): ApiResult<Unit> {
        failWith?.let { return ApiResult.Error(it) }
        synonyms.value = synonyms.value + (subjectId to transform(synonyms.value[subjectId].orEmpty()))
        return ApiResult.Success(Unit)
    }
}
