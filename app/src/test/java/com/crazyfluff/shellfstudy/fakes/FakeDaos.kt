package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.core.database.AssignmentDao
import com.crazyfluff.shellfstudy.core.database.AssignmentEntity
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
}

/** In-memory stand-in for [AssignmentDao] used by repository/ViewModel unit tests. */
class FakeAssignmentDao : AssignmentDao {
    private val assignments = MutableStateFlow<Map<Long, AssignmentEntity>>(emptyMap())

    override suspend fun upsertAll(assignments: List<AssignmentEntity>) {
        this.assignments.value = this.assignments.value + assignments.associateBy { it.id }
    }

    override fun observeDueForReview(): Flow<List<AssignmentEntity>> = assignments.map { map ->
        map.values.filter { it.dueForReview && !it.hidden }
    }

    override suspend fun clearDueForReview() {
        assignments.value = assignments.value.filterValues { !it.dueForReview }
    }

    override suspend fun replaceDueForReview(assignments: List<AssignmentEntity>) {
        clearDueForReview()
        upsertAll(assignments)
    }

    override fun observeDueForLesson(): Flow<List<AssignmentEntity>> = assignments.map { map ->
        map.values.filter { it.dueForLesson && !it.hidden }
    }

    override suspend fun clearDueForLesson() {
        assignments.value = assignments.value.filterValues { !it.dueForLesson }
    }

    override suspend fun replaceDueForLesson(assignments: List<AssignmentEntity>) {
        clearDueForLesson()
        upsertAll(assignments)
    }
}
