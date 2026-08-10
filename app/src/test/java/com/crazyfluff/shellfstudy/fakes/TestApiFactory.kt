package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.core.data.AssignmentRepository
import com.crazyfluff.shellfstudy.core.data.StatsRepository
import com.crazyfluff.shellfstudy.core.data.SubjectRepository
import com.crazyfluff.shellfstudy.core.data.WaniKaniRepository
import com.crazyfluff.shellfstudy.core.network.WaniKaniApi
import com.crazyfluff.shellfstudy.core.sync.SyncOrchestrator
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** mockwebserver3 (OkHttp5)'s [MockResponse] is immutable and built via [MockResponse.Builder]. */
fun jsonResponse(body: String, code: Int = 200): MockResponse =
    MockResponse.Builder().code(code).body(body).build()

fun emptyResponse(code: Int): MockResponse =
    MockResponse.Builder().code(code).build()

/** Builds a real [WaniKaniApi] pointed at a local MockWebServer instance for tests. */
fun buildTestApi(baseUrl: String): WaniKaniApi {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }
    val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(OkHttpClient.Builder().build())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
    return retrofit.create(WaniKaniApi::class.java)
}

/** The full repository graph, wired with in-memory fakes, for ViewModel/repository unit tests. */
class TestRepositories(
    val api: WaniKaniApi,
    val subjectDao: FakeSubjectDao,
    val assignmentDao: FakeAssignmentDao,
    val syncStateDao: FakeSyncStateDao,
    val reviewLogDao: FakeReviewLogDao,
    val subjectRepository: SubjectRepository,
    val assignmentRepository: AssignmentRepository,
    val statsRepository: StatsRepository,
    val waniKaniRepository: WaniKaniRepository,
    val syncOrchestrator: SyncOrchestrator
)

fun buildTestRepositories(baseUrl: String): TestRepositories {
    val api = buildTestApi(baseUrl)
    val subjectDao = FakeSubjectDao()
    val assignmentDao = FakeAssignmentDao(subjectLevelLookup = subjectDao::levelOf)
    val syncStateDao = FakeSyncStateDao()
    val reviewLogDao = FakeReviewLogDao()

    val subjectRepository = SubjectRepository(api, subjectDao, FakeSrsSystemDao(), FakeStudyMaterialDao(), syncStateDao)
    val assignmentRepository = AssignmentRepository(api, assignmentDao, subjectDao, syncStateDao, subjectRepository)
    val statsRepository = StatsRepository(api, FakeReviewStatisticDao(), FakeLevelProgressionDao(), reviewLogDao, syncStateDao)
    val waniKaniRepository = WaniKaniRepository(api, statsRepository)
    val syncOrchestrator = SyncOrchestrator(subjectRepository, assignmentRepository, statsRepository)

    return TestRepositories(
        api, subjectDao, assignmentDao, syncStateDao, reviewLogDao,
        subjectRepository, assignmentRepository, statsRepository, waniKaniRepository, syncOrchestrator
    )
}
