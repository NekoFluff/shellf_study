package com.crazyfluff.shellfstudy.shared.data.model

import androidx.compose.runtime.Immutable

import com.crazyfluff.shellfstudy.shared.network.MAX_WANIKANI_LEVEL
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import kotlin.math.ceil
import kotlin.time.Instant
import kotlinx.serialization.Serializable

data class WaniKaniUser(
    /** WaniKani's stable user id — unlike [username], it never changes. */
    val id: String,
    val username: String,
    val level: Int,
    /** The highest level whose content the account's subscription unlocks. */
    val maxLevelGranted: Int = MAX_WANIKANI_LEVEL
)

@Immutable
data class DashboardSummary(
    val lessonCount: Int,
    val reviewCount: Int
)

/** Progress toward WaniKani's level-up requirement: 90% of a level's kanji at Guru or higher. */
@Immutable
data class LevelUpProgress(
    val kanjiGuruedOrHigher: Int,
    val kanjiTotal: Int
) {
    /** WaniKani's actual level-up threshold: 90% of the level's kanji, rounded up. */
    val requiredCount: Int get() = ceil(kanjiTotal * 0.9).toInt()

    /** [kanjiTotal] > 0 guard avoids the vacuous "0 >= requiredCount(0) == 0" reading as ready
     *  during the no-data-yet default. */
    val isLevelUpReady: Boolean get() = kanjiTotal > 0 && kanjiGuruedOrHigher >= requiredCount
}

/**
 * The earliest the current level's kanji can reach Guru if every lesson is done the moment it
 * unlocks and every review is answered correctly the moment it comes due — see
 * [com.crazyfluff.shellfstudy.shared.data.LevelUpPathCalculator].
 *
 * [upcomingGuruTimes] holds one sorted entry per not-yet-Guru kanji whose path could be worked out;
 * a kanji whose SRS system isn't cached is left out rather than guessed at, so the list can be
 * shorter than `kanjiTotal - alreadyGuruCount`.
 */
@Immutable
data class LevelUpPath(
    val kanjiTotal: Int,
    val alreadyGuruCount: Int,
    val upcomingGuruTimes: List<Instant>,
    /** The `now` the path was worked out from — relative times ("in 2d 6h") count from here. */
    val computedAt: Instant,
    /** Every not-yet-Guru radical and kanji at the level with a known path, by subject id. */
    val guruAtBySubject: Map<Long, Instant> = emptyMap(),
    /** The items that set [levelUpAt]: the kanji the level-up still needs (just that many, even when
     *  more reach Guru in the same hour), plus the slowest radical gating each of those still locked.
     *  Holding any of them up moves the date; the rest are spare. Empty when [levelUpAt] is null. */
    val decidingSubjectIds: Set<Long> = emptySet(),
    /** The soonest lesson or review session among [decidingSubjectIds]. */
    val nextDecidingStep: LevelUpStep? = null
) {
    val requiredCount: Int get() = LevelUpProgress(alreadyGuruCount, kanjiTotal).requiredCount

    /** Null when the level is already ready (nothing left to wait for) or when too few kanji have a
     *  known path to reach [requiredCount]. */
    val levelUpAt: Instant? get() {
        val stillNeeded = requiredCount - alreadyGuruCount
        return if (stillNeeded <= 0) null else upcomingGuruTimes.getOrNull(stillNeeded - 1)
    }
}

/** One lesson or review session on the fastest level-up path: when it can start, and how many of the
 *  deciding items it covers. [at] is when the session became available, so it's in the past for an
 *  overdue review or a lesson waiting to be done — and stays put however often the path is recomputed. */
@Immutable
data class LevelUpStep(
    val at: Instant,
    val radicalCount: Int,
    val kanjiCount: Int,
    /** How many of the items are lessons rather than reviews. */
    val lessonCount: Int
) {
    val totalCount: Int get() = radicalCount + kanjiCount

    /** "4 radical reviews", "3 kanji lessons", or "2 radicals, 3 kanji" for a session that mixes
     *  subject types or lessons with reviews. */
    val itemsPhrase: String get() {
        val kind = when (lessonCount) {
            totalCount -> "lesson"
            0 -> "review"
            else -> null
        }
        return when {
            kind != null && kanjiCount == 0 -> plural(radicalCount, "radical $kind")
            kind != null && radicalCount == 0 -> plural(kanjiCount, "kanji $kind")
            else -> listOfNotNull(
                plural(radicalCount, "radical").takeIf { radicalCount > 0 },
                "$kanjiCount kanji".takeIf { kanjiCount > 0 }
            ).joinToString(", ")
        }
    }

    private fun plural(count: Int, noun: String): String = if (count == 1) "1 $noun" else "$count ${noun}s"
}

/** Common shape shared by [LessonItem] and [ReviewItem] — everything a quiz-session-summary row or
 *  the shared quiz-question screen needs to display a subject, without depending on either
 *  feature's full item type. */
interface QuizDisplayItem {
    val assignmentId: Long
    val characters: String?
    val characterImageUrl: String?
    val meanings: List<String>

    /** WaniKani's own alternate meanings — acceptable answers, distinct from [meanings]. Here rather
     *  than on the concrete items because grading needs them: the shared submit/reveal flow builds a
     *  question's acceptable answers from [meanings] + this + [readings]. */
    val auxiliaryMeanings: List<String>

    /** The subject's readings; empty for radicals, which are only ever asked for meaning. */
    val readings: List<String>

    val subjectId: Long
    val subjectType: SubjectType
}

data class ReviewItem(
    override val assignmentId: Long,
    override val subjectId: Long,
    override val subjectType: SubjectType,
    override val characters: String?,
    override val characterImageUrl: String? = null,
    val level: Int,
    val srsStage: Int,
    override val meanings: List<String>,
    override val readings: List<String>,
    /** WaniKani's own official alternate meanings (e.g. "1" alongside "one") — distinct from the
     *  primary [meanings], but just as acceptable a grading answer. */
    override val auxiliaryMeanings: List<String> = emptyList(),
    val pronunciationAudios: List<PronunciationAudio> = emptyList(),
    /** Carried along so a review's rank change can be computed synchronously against
     *  AssignmentRepository's in-memory SRS-system cache, with no DB access needed on the
     *  per-answer critical path — see AssignmentRepository.computeReviewRankChange. */
    val srsSystemId: Long = 0
) : QuizDisplayItem

/**
 * One item's outcome for a review session — which questions were answered correctly, and how many
 * wrong attempts each took. The counts are not decoration: WaniKani's demotion rule is
 * `ceil(incorrect / 2) * penalty`, so a single wrong answer and a triple wrong answer on the same
 * question produce different ending stages. This shape is the single source of truth for both the
 * local rank-change prediction and the `incorrect_*_answers` posted to WaniKani, so the two can't
 * disagree about how far an item fell.
 *
 * [incorrectMeaning]/[incorrectReading] default to match the booleans, and callers that know the real
 * counts should override them. The defaults keep short-hand construction (e.g. in tests) honest.
 */
data class ReviewGrade(
    val meaningCorrect: Boolean,
    val readingCorrect: Boolean,
    val incorrectMeaning: Int = if (meaningCorrect) 0 else 1,
    val incorrectReading: Int = if (readingCorrect) 0 else 1
) {
    /** A clean pass — no wrong answers anywhere. Deliberately derived from the counts, not the
     *  booleans, so "was anything wrong" can only ever have one answer. */
    val isFullyCorrect: Boolean get() = incorrectMeaning == 0 && incorrectReading == 0

    /** Total wrong answers given for this item in the session — the numerator of WaniKani's penalty. */
    val totalIncorrect: Int get() = incorrectMeaning + incorrectReading
}

data class LessonItem(
    override val assignmentId: Long,
    override val subjectId: Long,
    override val subjectType: SubjectType,
    override val characters: String?,
    override val characterImageUrl: String? = null,
    val level: Int,
    /** The subject's position within its level's lesson order, per WaniKani's own intended
     *  sequencing — used as the tie-break when [com.crazyfluff.shellfstudy.shared.feature.lesson.LessonPrioritizer]
     *  reorders a level's items. */
    val lessonPosition: Int = 0,
    override val meanings: List<String>,
    override val readings: List<String>,
    val meaningMnemonic: String?,
    val readingMnemonic: String?,
    override val auxiliaryMeanings: List<String> = emptyList(),
    val meaningHint: String? = null,
    val readingHint: String? = null,
    val onyomiReadings: List<String> = emptyList(),
    val kunyomiReadings: List<String> = emptyList(),
    val nanoriReadings: List<String> = emptyList(),
    val partsOfSpeech: List<String> = emptyList(),
    val pronunciationAudios: List<PronunciationAudio> = emptyList(),
    val contextSentences: List<ContextSentence> = emptyList(),
    val componentSubjectIds: List<Long> = emptyList(),
    val amalgamationSubjectIds: List<Long> = emptyList(),
    val visuallySimilarSubjectIds: List<Long> = emptyList(),
    /** Carried along so AssignmentRepository.applyOptimisticLessonStart can resolve the SRS
     *  system's starting stage without an extra DB round trip. */
    val srsSystemId: Long = 0
) : QuizDisplayItem

/** A subject as shown in search results. [srsStage] is null if no assignment exists yet. */
data class SubjectSummary(
    val subjectId: Long,
    val subjectType: SubjectType,
    val characters: String?,
    val level: Int,
    val meanings: List<String>,
    val readings: List<String>,
    val srsStage: Int? = null,
    val characterImageUrl: String? = null
)

/** One row of a lesson/review session-complete screen's "slowest answers" card — a single graded
 *  answer, already reduced to display-ready fields. [Serializable] so the same row can be persisted
 *  as part of a [com.crazyfluff.shellfstudy.shared.data.LastSessionSummary] for later revisiting. */
@Serializable
data class SessionAnswerRow(
    val label: String,
    val typeLabel: String,
    val elapsedMs: Long,
    val isCorrect: Boolean,
    val subjectId: Long,
    val subjectType: SubjectType
)

/** One chip of a lesson/review session-complete screen's "missed items" card. */
@Serializable
data class SessionMissedItemRow(val label: String, val subjectId: Long, val subjectType: SubjectType)
