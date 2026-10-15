package com.crazyfluff.shellfstudy.fakes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest

/**
 * WaniKani's study-material endpoints, kept as state: a GET returns what earlier creates and updates
 * left behind, so a test can check what the server ends up holding rather than only what was sent.
 * Use as a [waniKaniCollectionDispatcher] stub; anything that is not a study-material request falls
 * through.
 */
class FakeStudyMaterialServer {
    /** Synonyms by subject id — seed it to stand for edits made on the website. */
    val synonymsBySubject = mutableMapOf<Long, List<String>>()
    private val idsBySubject = mutableMapOf<Long, Long>()
    private var nextId = 100L

    /** When set, every write (create or update) fails with this status instead. */
    var failWritesWith: Int? = null

    val requests = mutableListOf<String>()

    fun seed(subjectId: Long, synonyms: List<String>) {
        synonymsBySubject[subjectId] = synonyms
        idsBySubject[subjectId] = nextId++
    }

    fun stub(request: RecordedRequest): MockResponse? {
        val target = request.target.orEmpty()
        if (!target.startsWith("/study_materials")) return null
        requests += "${request.method} ${target.substringBefore('?')}"
        return when (request.method) {
            "GET" -> {
                val subjectIds = Regex("subject_ids=([0-9,]+)").find(target)?.groupValues?.get(1)
                    ?.split(',')?.map { it.toLong() }
                val subjects = subjectIds ?: synonymsBySubject.keys.toList()
                collection(subjects.filter { it in synonymsBySubject })
            }
            "POST" -> write(request) { body ->
                val subjectId = body["subject_id"]!!.jsonPrimitive.long
                idsBySubject.getOrPut(subjectId) { nextId++ }
                synonymsBySubject[subjectId] = body.synonyms()
                jsonResponse(materialJson(subjectId))
            }
            "PUT" -> write(request) { body ->
                val id = target.substringAfterLast('/').toLong()
                val subjectId = idsBySubject.entries.single { it.value == id }.key
                synonymsBySubject[subjectId] = body.synonyms()
                jsonResponse(materialJson(subjectId))
            }
            else -> emptyResponse(405)
        }
    }

    private fun write(request: RecordedRequest, block: (JsonObject) -> MockResponse): MockResponse {
        failWritesWith?.let { return jsonResponse("""{"error":"nope","code":$it}""", it) }
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        return block(body["study_material"]!!.jsonObject)
    }

    private fun JsonObject.synonyms(): List<String> =
        this["meaning_synonyms"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()

    private fun collection(subjectIds: List<Long>) = jsonResponse(
        """{"object":"collection","url":"https://api.wanikani.com/v2/study_materials",
           "pages":{"per_page":500,"next_url":null,"previous_url":null},
           "total_count":${subjectIds.size},"data":[${subjectIds.joinToString(",") { materialJson(it) }}]}"""
    )

    private fun materialJson(subjectId: Long): String {
        val id = idsBySubject.getValue(subjectId)
        val synonyms = synonymsBySubject.getValue(subjectId).joinToString(",") { "\"$it\"" }
        return """{"id":$id,"object":"study_material","url":"https://api.wanikani.com/v2/study_materials/$id",
            "data_updated_at":"2026-01-01T00:00:00.000000Z",
            "data":{"created_at":"2026-01-01T00:00:00.000000Z","subject_id":$subjectId,"subject_type":"kanji",
            "meaning_note":"kept","reading_note":null,"meaning_synonyms":[$synonyms],"hidden":false}}"""
    }
}
