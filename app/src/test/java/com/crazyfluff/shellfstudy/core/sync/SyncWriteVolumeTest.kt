package com.crazyfluff.shellfstudy.core.sync

import com.crazyfluff.shellfstudy.fakes.waniKaniCollectionDispatcher
import com.crazyfluff.shellfstudy.fakes.TestRepositories
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.fakes.jsonResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Measures how many database write operations a sync pass issues, because that is the number that
 * decides how often Room wakes every observable query in the app.
 *
 * Room broadcasts invalidation once per *outermost* write operation, so "how many writes did we do"
 * is the cost driver — and it is invisible to every other test here, since the DAOs are in-memory
 * maps where writing the same rows twice changes nothing observable. These tests exist to put a
 * number on it before and after the no-op guard, rather than to assert a behaviour someone could
 * reason about from the end state.
 *
 * The scenario that matters most is [second pass with nothing changed]: an incremental sync whose
 * `updated_after` fetch returns no items has no row to store, and every write it issues anyway is a
 * pure wake-up cost. That is the common case on app open, since the staleness gate is an hour.
 */
class SyncWriteVolumeTest {

    private lateinit var server: MockWebServer
    private lateinit var repositories: TestRepositories

    /** Every resource responds with an empty collection, i.e. "nothing has changed since your cursor". */
    init {
        System.err.println("### SyncWriteVolumeTest class initialised")
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = waniKaniCollectionDispatcher()
        server.start()
        repositories = buildTestRepositories(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /**
     * A first pass has nothing cached, so every resource is stale by definition and every one is
     * fetched. All five fetches are empty here, which is the interesting part: none of them has a row
     * to store, so every write issued is pure cost.
     */
    /**
     * The headline measurement: a first pass where every fetch returns nothing.
     *
     * Before the no-op guard this issued five writes — one per resource — each of which refreshed
     * Room's invalidation tracker and woke every observable query in the app, despite storing no rows
     * at all.
     *
     * `level_progressions` is the one resource that can still write with nothing to store, because its
     * fetch is not cursor-based: an empty response there might mean the server genuinely has nothing,
     * so it must not be skipped. It is listed explicitly rather than filtered out, so this test fails
     * loudly if another cursorless resource is added without thought.
     */
    @Test
    fun `a pass whose cursor-based fetches all return nothing writes nothing`() = runTest {
        repositories.writeLog.reset()

        val result = repositories.syncOrchestrator.syncAll(force = true)

        assertThat(result).isInstanceOf(com.crazyfluff.shellfstudy.shared.data.ApiResult.Success::class.java)
        assertThat(repositories.writeLog.writes.map { it.resource }).containsExactly("level_progressions")
        assertThat(repositories.writeLog.rowCountFor("level_progressions")).isEqualTo(0)
    }

    /**
     * The regression this whole measurement was for: once cursors exist, a resume that finds nothing
     * changed must be free. Before the guard this still wrote every resource, and because an empty
     * `upsertAll(emptyList())` still refreshes Room's invalidation tracker, each one woke the
     * dashboard's flows for no reason.
     */
    @Test
    fun `a resume with nothing changed performs no writes`() = runTest {
        repositories.syncOrchestrator.syncAll(force = true) // establishes cursors for every resource
        repositories.writeLog.reset()

        // A normal resume, not a forced one: force bypasses the staleness gate, and the gate is what
        // stops a cursorless resource (level progressions) being refetched on every single pass. This
        // is the path the dashboard actually takes on app open.
        repositories.syncOrchestrator.syncAll(force = false)

        assertThat(repositories.writeLog.writeCount).isEqualTo(0)
    }

    /**
     * Pins the other half of the contract: when a resource *does* return rows, exactly one write per
     * changed resource is issued — not zero (the guard must not swallow real data) and not more than
     * one (which would be a duplicate write).
     */
    @Test
    fun `a pass with real data writes each changed resource exactly once`() = runTest {
        server.dispatcher = waniKaniCollectionDispatcher { request ->
            when {
                request.path.orEmpty().startsWith("/subjects") ->
                    collectionOfOne("kanji", subjectJson(id = 1))
                request.path.orEmpty().startsWith("/assignments") ->
                    collectionOfOne("assignment", assignmentJson(id = 1, subjectId = 1))
                else -> null
            }
        }
        repositories.writeLog.reset()

        repositories.syncOrchestrator.syncAll(force = true)
        assertThat(repositories.writeLog.nonEmptyWrites.map { it.resource })
            .containsExactly("subjects", "assignments")
        assertThat(repositories.writeLog.rowCountFor("subjects")).isEqualTo(1)
        assertThat(repositories.writeLog.rowCountFor("assignments")).isEqualTo(1)
    }


    private fun collectionOfOne(objectType: String, itemJson: String) = jsonResponse(
        """{"object":"collection","url":"https://api.wanikani.com/v2/$objectType","total_count":1,"data":[$itemJson]}"""
    )

    /**
     * Mirrors the real `SubjectData`/`AssignmentData` shapes. Only `created_at`, `level` and `slug` are
     * required for a subject and `created_at`/`subject_id`/`subject_type`/`srs_stage`/`hidden` for an
     * assignment — every other field has a default.
     *
     * Written against those declarations rather than hand-guessed, because a missing required field
     * makes the whole page fail to deserialize. That surfaces as "the fetch returned nothing", which
     * is indistinguishable from the no-op behaviour this test exists to measure — it is exactly how
     * the first version of this test silently measured nothing at all.
     */
    private fun subjectJson(id: Long) = """
        {"id":$id,"object":"kanji","url":"https://api.wanikani.com/v2/subjects/$id",
         "data_updated_at":"2026-01-01T00:00:00.000000Z",
         "data":{"created_at":"2026-01-01T00:00:00.000000Z","level":12,"slug":"water","characters":"水"}}
    """.trimIndent()

    private fun assignmentJson(id: Long, subjectId: Long) = """
        {"id":$id,"object":"assignment","url":"https://api.wanikani.com/v2/assignments/$id",
         "data_updated_at":"2026-01-01T00:00:00.000000Z",
         "data":{"created_at":"2026-01-01T00:00:00.000000Z","subject_id":$subjectId,
         "subject_type":"kanji","srs_stage":1,"hidden":false}}
    """.trimIndent()
}
