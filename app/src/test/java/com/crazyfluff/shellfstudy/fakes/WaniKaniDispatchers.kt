package com.crazyfluff.shellfstudy.fakes

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest

/**
 * A [Dispatcher] that answers every WaniKani collection endpoint with an empty collection, so a test
 * only has to describe the endpoints it actually cares about.
 *
 * The same branch list was written out in six test classes, and had already drifted: some answered
 * `/study_materials`, others 404'd it; some used `jsonResponse("{}", 404)` where the rest used an empty
 * 404; and SyncOrchestratorTest kept two copies in one file. A test that wanted one endpoint stubbed
 * had to restate the other six, so omitting one was a silent change in what the test exercised.
 *
 * [stub] is consulted first, for the endpoints a test stubs, observes or fails: return a response to
 * handle the request, or null to fall through to the empty collections. Requests to paths nothing here
 * knows about get an empty 404, as they did before.
 */
fun waniKaniCollectionDispatcher(
    stub: (request: RecordedRequest) -> MockResponse? = { null },
): Dispatcher = object : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        stub(request)?.let { return it }
        val path = request.path.orEmpty()
        return when {
            path.startsWith("/spaced_repetition_systems") -> emptyCollection("srs_system")
            path.startsWith("/subjects") -> emptyCollection("kanji")
            path.startsWith("/assignments") -> emptyCollection("assignment")
            path.startsWith("/review_statistics") -> emptyCollection("review_statistic")
            path.startsWith("/study_materials") -> emptyCollection("study_material")
            path.startsWith("/level_progressions") -> emptyCollection("level_progression")
            else -> emptyResponse(404)
        }
    }
}
