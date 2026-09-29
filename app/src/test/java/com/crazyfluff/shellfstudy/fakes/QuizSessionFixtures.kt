package com.crazyfluff.shellfstudy.fakes

/** The instant every quiz-session fixture stamps its timestamps with. */
const val FIXTURE_INSTANT = "2026-01-01T00:00:00.000000Z"

private const val FIXTURE_CREATED_AT = "2020-01-01T00:00:00.000000Z"

/**
 * One assignment inside an assignments-collection fixture.
 *
 * [unlockedAt] is the lesson state — unlocked, never studied, `srs_stage` 0 — and [availableAt] is the
 * review state: started, and due at that instant. That is the only difference the lesson and review
 * fixtures had, so one builder covers both and the difference is the argument a reader sees rather
 * than a JSON blob they have to diff.
 */
data class AssignmentFixture(
    val id: Long,
    val subjectId: Long,
    val subjectType: String,
    val srsStage: Int,
    val unlockedAt: String? = null,
    val availableAt: String? = null,
)

/**
 * A pronunciation clip attached to a subject.
 *
 * The URL is spelled out rather than derived from [pronunciation] because WaniKani romanizes its audio
 * file names — the clip for みず really is `mizu.mp3` — and the tests assert on the URL the player was
 * handed, not just that some clip played.
 */
data class FixtureAudio(val url: String, val pronunciation: String)

/** The clip both ViewModel tests' audio fixtures attach. */
val MIZU_AUDIO = FixtureAudio(url = "https://api.wanikani.com/audio/mizu.mp3", pronunciation = "みず")

/**
 * One subject inside a subjects-collection fixture.
 *
 * [mnemonic] and [audio] are the only optional shapes the suite needed: radicals carry a mnemonic, and
 * a kanji with audio carries one clip whose metadata names the reading it belongs to. A null [reading]
 * emits an empty `readings` array, which is what a radical has.
 */
data class SubjectFixture(
    val id: Long,
    val subjectType: String,
    val level: Int,
    val slug: String,
    val characters: String,
    val meaning: String,
    val reading: String? = null,
    val mnemonic: String? = null,
    val audio: FixtureAudio? = null,
)

/** The kanji both ViewModel tests use: level 3, water/水/みず, no audio. */
val KANJI_SUBJECT = SubjectFixture(
    id = 1, subjectType = "kanji", level = 3, slug = "water", characters = "水",
    meaning = "Water", reading = "みず"
)

/** The radical both use: level 1, mouth/口/Mouth, no readings. */
val RADICAL_SUBJECT = SubjectFixture(
    id = 1, subjectType = "radical", level = 1, slug = "mouth", characters = "口",
    meaning = "Mouth"
)

/** The vocabulary both use: level 1, testword/件亜/けんあ. */
val VOCAB_SUBJECT = SubjectFixture(
    id = 8001, subjectType = "vocabulary", level = 1, slug = "testword", characters = "件亜",
    meaning = "Testword", reading = "けんあ"
)

/**
 * An assignments collection response.
 *
 * These fixtures existed twice — once in `LessonViewModelTest`, once in `ReviewViewModelTest` — as
 * fifteen-line JSON literals that differed in an id and a stage. A field added to the local entity
 * meant editing both, and forgetting one produced a test that passed against a fixture the app can no
 * longer parse.
 */
fun waniKaniAssignmentsJson(vararg assignments: AssignmentFixture): String = """
    {
      "object": "collection", "url": "https://api.wanikani.com/v2/assignments",
      "total_count": ${assignments.size},
      "data": [${assignments.joinToString(",") { it.toJson() }}]
    }
""".trimIndent()

/** A subjects collection response — see [waniKaniAssignmentsJson] for why these are shared. */
fun waniKaniSubjectsJson(vararg subjects: SubjectFixture): String = """
    {
      "object": "collection", "url": "https://api.wanikani.com/v2/subjects",
      "total_count": ${subjects.size},
      "data": [${subjects.joinToString(",") { it.toJson() }}]
    }
""".trimIndent()

private fun AssignmentFixture.toJson(): String {
    val state = when {
        unlockedAt != null && availableAt != null ->
            error("assignment $id is both unlocked and available; a fixture is one or the other")
        unlockedAt != null -> """"unlocked_at": "$unlockedAt""""
        availableAt != null -> """"available_at": "$availableAt""""
        else -> error("assignment $id needs either unlockedAt (lesson) or availableAt (review)")
    }
    return """
        {
          "id": $id, "object": "assignment",
          "url": "https://api.wanikani.com/v2/assignments/$id",
          "data_updated_at": "$FIXTURE_INSTANT",
          "data": {
            "created_at": "$FIXTURE_INSTANT", "subject_id": $subjectId,
            "subject_type": "$subjectType",
            "srs_stage": $srsStage, $state, "hidden": false
          }
        }
    """.trimIndent()
}

private fun SubjectFixture.toJson(): String {
    val readings = if (reading == null) "[]" else """[{"reading": "$reading", "primary": true, "accepted_reading": true}]"""
    val optional = buildList {
        if (mnemonic != null) add(""""meaning_mnemonic": "$mnemonic"""")
        if (audio != null) {
            add(
                """"pronunciation_audios": [
                  {
                    "url": "${audio.url}",
                    "content_type": "audio/mpeg",
                    "metadata": {"gender": "female", "pronunciation": "${audio.pronunciation}"}
                  }
                ]"""
            )
        }
    }
    val optionalBlock = if (optional.isEmpty()) "" else ", " + optional.joinToString(", ")
    return """
        {
          "id": $id, "object": "$subjectType",
          "url": "https://api.wanikani.com/v2/subjects/$id",
          "data_updated_at": "$FIXTURE_INSTANT",
          "data": {
            "created_at": "$FIXTURE_CREATED_AT", "level": $level, "slug": "$slug",
            "characters": "$characters",
            "meanings": [{"meaning": "$meaning", "primary": true, "accepted_meaning": true}],
            "readings": $readings$optionalBlock
          }
        }
    """.trimIndent()
}
