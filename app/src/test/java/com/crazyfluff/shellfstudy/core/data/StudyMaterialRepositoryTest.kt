package com.crazyfluff.shellfstudy.core.data

import app.cash.turbine.test
import com.crazyfluff.shellfstudy.fakes.FakeStudyMaterialServer
import com.crazyfluff.shellfstudy.fakes.TestRepositories
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.fakes.waniKaniCollectionDispatcher
import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.database.StudyMaterialEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class StudyMaterialRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repositories: TestRepositories
    private val wanikani = FakeStudyMaterialServer()
    private val repository get() = repositories.studyMaterialRepository
    private val dao get() = repositories.studyMaterialDao

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = waniKaniCollectionDispatcher(wanikani::stub)
        server.start()
        repositories = buildTestRepositories(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `sync mirrors every subject's synonyms and the next pass is incremental`() = runTest {
        wanikani.seed(440, listOf("aqua", "H2O"))
        wanikani.seed(441, listOf("blaze"))

        assertThat(repository.syncStudyMaterials(force = true)).isEqualTo(ApiResult.Success(Unit))
        assertThat(server.takeRequest().url.queryParameter("updated_after")).isNull()

        repository.observeAllSynonyms().test {
            assertThat(awaitItem()).containsExactly(440L, listOf("aqua", "H2O"), 441L, listOf("blaze"))
        }
        repository.syncStudyMaterials(force = true)
        assertThat(server.takeRequest().url.queryParameter("updated_after")).isNotNull()
    }

    @Test
    fun `adding to a subject with no material creates one`() = runTest {
        assertThat(repository.addSynonym(440, "  big   water ")).isEqualTo(ApiResult.Success(Unit))

        assertThat(wanikani.synonymsBySubject[440]).containsExactly("big water")
        assertThat(wanikani.requests).containsExactly("GET /study_materials", "POST /study_materials").inOrder()
        // The placeholder row is replaced by the server's, not kept beside it.
        assertThat(dao.all().map { it.id to it.meaningSynonyms }).containsExactly(100L to listOf("big water"))
    }

    @Test
    fun `adding keeps a synonym added on the website since the last sync`() = runTest {
        dao.upsertAll(
            listOf(StudyMaterialEntity(id = 100, subjectId = 440, meaningSynonyms = listOf("aqua"), hidden = false))
        )
        wanikani.seed(440, listOf("aqua", "from the website"))

        repository.addSynonym(440, "liquid")

        assertThat(wanikani.synonymsBySubject[440]).containsExactly("aqua", "from the website", "liquid").inOrder()
        assertThat(wanikani.requests.last()).isEqualTo("PUT /study_materials/100")
        repository.observeSynonyms(440).test {
            assertThat(awaitItem()).containsExactly("aqua", "from the website", "liquid").inOrder()
        }
    }

    @Test
    fun `removing drops the synonym on WaniKani and locally`() = runTest {
        wanikani.seed(440, listOf("aqua", "liquid"))
        repository.syncStudyMaterials(force = true)

        assertThat(repository.removeSynonym(440, "AQUA")).isEqualTo(ApiResult.Success(Unit))

        assertThat(wanikani.synonymsBySubject[440]).containsExactly("liquid")
        repository.observeSynonyms(440).test { assertThat(awaitItem()).containsExactly("liquid") }
    }

    @Test
    fun `a repeat of an existing synonym sends nothing`() = runTest {
        wanikani.seed(440, listOf("aqua"))
        repository.syncStudyMaterials(force = true)
        wanikani.requests.clear()

        assertThat(repository.addSynonym(440, "Aqua")).isEqualTo(ApiResult.Success(Unit))
        assertThat(repository.addSynonym(440, "   ")).isInstanceOf(ApiResult.Error::class.java)

        assertThat(wanikani.requests).isEmpty()
    }

    @Test
    fun `a refused edit is rolled back and explains a read-only token`() = runTest {
        wanikani.seed(440, listOf("aqua"))
        repository.syncStudyMaterials(force = true)
        wanikani.failWritesWith = 403

        val result = repository.addSynonym(440, "liquid")

        assertThat((result as ApiResult.Error).message).contains("study_materials:update")
        repository.observeSynonyms(440).test { assertThat(awaitItem()).containsExactly("aqua") }
    }

    @Test
    fun `a refused create leaves no placeholder behind`() = runTest {
        wanikani.failWritesWith = 422

        val result = repository.addSynonym(440, "liquid")

        assertThat((result as ApiResult.Error).message).isEqualTo("WaniKani didn't accept that synonym.")
        assertThat(dao.all()).isEmpty()
    }

    @Test
    fun `an edit shows locally before WaniKani answers`() = runTest {
        repository.observeSynonyms(440).test {
            assertThat(awaitItem()).isEmpty()
            repository.addSynonym(440, "liquid")
            // The placeholder, then the server's row: both already carry the new synonym.
            assertThat(awaitItem()).containsExactly("liquid")
            expectNoEvents()
        }
    }
}
