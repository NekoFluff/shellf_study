package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.PitchAccentRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.SubjectRepository
import com.crazyfluff.shellfstudy.shared.data.WaniKaniRepository
import com.crazyfluff.shellfstudy.shared.data.WeblioPitchAccentParser
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.database.SrsSystemEntity
import com.crazyfluff.shellfstudy.shared.network.SrsStageData
import com.crazyfluff.shellfstudy.shared.network.WaniKaniApi
import com.crazyfluff.shellfstudy.shared.network.createWaniKaniHttpClient
import com.crazyfluff.shellfstudy.shared.sync.SyncOrchestrator
import mockwebserver3.MockResponse

/**
 * mockwebserver3 (OkHttp5)'s [MockResponse] is immutable and built via [MockResponse.Builder].
 * The explicit Content-Type is required for Ktor's ContentNegotiation to deserialize the body —
 * unlike Retrofit's converter, it won't guess a type for a response with none.
 */
fun jsonResponse(body: String, code: Int = 200): MockResponse =
    MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

fun emptyResponse(code: Int): MockResponse =
    MockResponse.Builder().code(code).build()

/** Builds a real [WaniKaniApi] pointed at a local MockWebServer instance for tests. */
fun buildTestApi(baseUrl: String): WaniKaniApi {
    val httpClient = createWaniKaniHttpClient(tokenProvider = { null })
    return WaniKaniApi(httpClient, baseUrl = baseUrl)
}

/**
 * The standard WaniKani SRS system fixtures implicitly rely on: JSON test fixtures for subjects
 * never specify `spaced_repetition_system_id`, so it defaults to 0 (see [com.crazyfluff.shellfstudy.shared.network.SubjectData]) —
 * seeding id=0 here means optimistic-grading logic (which needs a cached SRS system to predict a
 * stage transition) works out of the box for every existing test fixture without each one needing
 * to seed its own.
 */
val DEFAULT_TEST_SRS_SYSTEM = SrsSystemEntity(
    id = 0,
    name = "Default",
    unlockingStagePosition = 0,
    startingStagePosition = 1,
    passingStagePosition = 5,
    burningStagePosition = 9,
    stages = listOf(
        SrsStageData(position = 1, interval = 4, intervalUnit = "hours"),
        SrsStageData(position = 2, interval = 8, intervalUnit = "hours"),
        SrsStageData(position = 3, interval = 23, intervalUnit = "hours"),
        SrsStageData(position = 4, interval = 47, intervalUnit = "hours"),
        SrsStageData(position = 5, interval = 1, intervalUnit = "weeks"),
        SrsStageData(position = 6, interval = 2, intervalUnit = "weeks"),
        SrsStageData(position = 7, interval = 1, intervalUnit = "months"),
        SrsStageData(position = 8, interval = 4, intervalUnit = "months"),
        SrsStageData(position = 9, interval = null, intervalUnit = null)
    )
)

/** The full repository graph, wired with in-memory fakes, for ViewModel/repository unit tests. */
class TestRepositories(
    val api: WaniKaniApi,
    val subjectDao: FakeSubjectDao,
    val assignmentDao: FakeAssignmentDao,
    val srsSystemDao: FakeSrsSystemDao,
    val syncStateDao: FakeSyncStateDao,
    val studyActivityDao: FakeStudyActivityDao,
    val outboxDao: FakeOutboxDao,
    val outboxSyncScheduler: FakeOutboxSyncScheduler,
    val subjectRepository: SubjectRepository,
    val assignmentRepository: AssignmentRepository,
    val pitchAccentRepository: PitchAccentRepository,
    val statsRepository: StatsRepository,
    val waniKaniRepository: WaniKaniRepository,
    val syncOrchestrator: SyncOrchestrator
)

fun buildTestRepositories(
    baseUrl: String,
    pitchAccentEntries: Map<String, List<PitchAccent>> = emptyMap()
): TestRepositories {
    val api = buildTestApi(baseUrl)
    val subjectDao = FakeSubjectDao()
    val assignmentDao = FakeAssignmentDao(subjectLevelLookup = subjectDao::levelOf, subjectLookup = subjectDao::entityOf)
    val srsSystemDao = FakeSrsSystemDao().apply { seed(DEFAULT_TEST_SRS_SYSTEM) }
    val syncStateDao = FakeSyncStateDao()
    val studyActivityDao = FakeStudyActivityDao()
    val outboxDao = FakeOutboxDao()
    val outboxSyncScheduler = FakeOutboxSyncScheduler()

    val pitchAccentRepository = PitchAccentRepository(
        FakePitchAccentBundledSource(pitchAccentEntries), FakePitchAccentCacheDao(), FakeWeblioApi(), WeblioPitchAccentParser()
    )
    val subjectRepository =
        SubjectRepository(api, subjectDao, srsSystemDao, FakeStudyMaterialDao(), syncStateDao, pitchAccentRepository)
    val assignmentRepository = AssignmentRepository(api, assignmentDao, subjectDao, syncStateDao, subjectRepository, srsSystemDao)
    val statsRepository = StatsRepository(api, FakeReviewStatisticDao(), FakeLevelProgressionDao(), studyActivityDao, syncStateDao)
    val waniKaniRepository = WaniKaniRepository(api)
    val syncOrchestrator = SyncOrchestrator(subjectRepository, assignmentRepository, statsRepository, syncStateDao)

    return TestRepositories(
        api, subjectDao, assignmentDao, srsSystemDao, syncStateDao, studyActivityDao, outboxDao, outboxSyncScheduler,
        subjectRepository, assignmentRepository, pitchAccentRepository, statsRepository, waniKaniRepository, syncOrchestrator
    )
}
