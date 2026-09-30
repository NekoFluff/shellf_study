package com.crazyfluff.shellfstudy.shared.quiz

import com.crazyfluff.shellfstudy.shared.data.PersistedAnsweredQuestion
import com.crazyfluff.shellfstudy.shared.data.PersistedItemProgress
import com.crazyfluff.shellfstudy.shared.data.PersistedQuestion
import com.crazyfluff.shellfstudy.shared.data.model.QuizDisplayItem

/**
 * Everything a lesson or review session knows about its questions, as one immutable value: what is
 * still to be asked, how each item is doing, every answer so far, and the grade that can still be
 * undone.
 *
 * Every change is a function returning a new session, so a ViewModel holds exactly one of these and
 * swaps it whole. That is what the two ViewModels could not get from the mutable queue, progress map
 * and answer list they used to hold side by side: a snapshot for persisting is just the current value,
 * and nothing can mutate it while a write of it is in flight.
 *
 * The queue itself is still [QuizQueue]'s algorithm — admission, requeueing and the priority split are
 * unchanged — applied copy-on-write.
 */
data class QuizSession<T : QuizDisplayItem>(
    /** The working set, in draw order; its head is [current]. */
    val inFlight: List<PendingQuestion<T>> = emptyList(),
    /** Questions not yet admitted into [inFlight] — empty unless built with a cap. */
    val reserve: List<PendingQuestion<T>> = emptyList(),
    /** Per-item progress by assignment id, in the order items were first touched. */
    val progress: Map<Long, QuizItemProgress<T>> = emptyMap(),
    /** Every graded answer, oldest first — what the summary's slowest answers come from. */
    val answered: List<AnsweredQuestionRecord<T>> = emptyList(),
    /** How many questions this pass asks in all, for the progress display. */
    val totalQuestions: Int = 0,
    /** The question most recently graded — what [undoLastGrade] reverts. Undo is only offered while
     *  that grade's feedback is on screen, so this is never consulted once the session has moved on. */
    val lastGraded: GradedQuestion<T>? = null,
    /** Items admitted ahead of the rest — Review's rank-up setting (see [QuizQueue.admitNext]). Fixed
     *  when the questions are built and persisted with the session, so a resume keeps exactly the
     *  split the reserve was sorted by, even if the learner has since levelled up or changed the
     *  setting. Empty means no priority at all. */
    val priorityIds: Set<Long> = emptySet(),
    private val shuffleOnAdmit: Boolean = true
) {
    val current: PendingQuestion<T>? get() = inFlight.firstOrNull()
    val remainingQuestions: Int get() = inFlight.size + reserve.size
    val isEmpty: Boolean get() = inFlight.isEmpty() && reserve.isEmpty()

    /** Questions answered correctly so far, across every item. */
    val completedQuestionCount: Int
        get() = progress.values.sumOf { (if (it.meaningDone) 1 else 0) + (if (it.readingDone) 1 else 0) }

    /**
     * A fresh set of questions over [items], keeping [progress] and [answered] — a lesson quizzes each
     * batch as its own pass, but summarizes the whole session. See [QuizQueue.build] for [shuffle],
     * [cap] and [backfillCap]; [priorityIds] are the items [QuizQueue.build]'s `isPriority` accepts.
     */
    fun withQuestionsFor(
        items: List<T>,
        shuffle: Boolean = true,
        cap: Int? = null,
        priorityIds: Set<Long> = emptySet(),
        backfillCap: Int? = null
    ): QuizSession<T> {
        val queue = QuizQueue<T>().apply {
            build(
                items,
                typesFor = { questionTypesFor(it.subjectType) },
                shuffle = shuffle,
                cap = cap,
                isPriority = isPriorityFor(priorityIds),
                backfillCap = backfillCap
            )
        }
        return copy(
            inFlight = queue.toList(),
            reserve = queue.reserveList(),
            totalQuestions = queue.size,
            lastGraded = null,
            priorityIds = priorityIds,
            shuffleOnAdmit = shuffle
        )
    }

    /** Gives each of [items] a progress entry if it has none, so a pass's summary counts items that were
     *  learned without a single wrong answer. Existing entries are kept as they are. */
    fun withProgressFor(items: List<T>): QuizSession<T> =
        copy(progress = progress + items.filter { it.assignmentId !in progress }.map { it.assignmentId to QuizItemProgress(it) })

    /**
     * Grades [current]. A correct answer takes the question out; a wrong one records the miss and sends
     * it to the back. With [deferSiblingOnCorrect], an item still owed its other question type has that
     * one moved to the back too, so it is not the next thing drawn. With a [cap], a freed slot admits
     * from [reserve] by [QuizQueue.admitNext]'s rule, with [priorityIds] and [backfillCap].
     *
     * Returns this session unchanged when there is no current question.
     */
    fun grade(
        isCorrect: Boolean,
        elapsedMs: Long,
        deferSiblingOnCorrect: Boolean = false,
        cap: Int? = null,
        backfillCap: Int? = null
    ): QuizSession<T> {
        val question = current ?: return this
        val item = question.item
        val before = progress[item.assignmentId] ?: QuizItemProgress(item)
        val after = if (isCorrect) before.withDone(question.type, true) else before.withIncorrectAttempt(question.type)
        val itemDone = isCorrect && questionTypesFor(item.subjectType).all(after::isDone)

        val queue = toQueue()
        queue.removeCurrent()
        if (!isCorrect) {
            queue.requeue(question)
        } else if (deferSiblingOnCorrect && !itemDone) {
            queue.moveMatchingToBack { it.item.assignmentId == item.assignmentId }
        }
        cap?.let { queue.admitNext(it, isPriorityFor(priorityIds), backfillCap) }

        return copy(
            inFlight = queue.toList(),
            reserve = queue.reserveList(),
            progress = progress + (item.assignmentId to after),
            answered = answered + AnsweredQuestionRecord(item, question.type, isCorrect, elapsedMs),
            lastGraded = GradedQuestion(
                question, isCorrect, itemDone, inFlightBefore = inFlight, reserveBefore = reserve
            )
        )
    }

    /**
     * Reverts [lastGraded], putting its question back in front as [current] — for a typo, or a correct
     * answer the learner takes back before it is committed. Null when there is nothing to undo.
     *
     * The queue goes back to exactly how it stood before the grade, not just the graded question: a
     * completing answer may have admitted items from [reserve] (and reshuffled the working set), and
     * leaving those admitted beside an item that is unfinished again could push the working set past
     * its cap, or let ordinary items in past the rank-up backfill.
     */
    fun undoLastGrade(): QuizSession<T>? {
        val graded = lastGraded ?: return null
        val question = graded.question
        val itemProgress = progress[question.item.assignmentId] ?: return null
        val reverted = if (graded.isCorrect) {
            itemProgress.withDone(question.type, false)
        } else {
            itemProgress.withIncorrectAttempt(question.type, by = -1)
        }
        return copy(
            inFlight = graded.inFlightBefore,
            reserve = graded.reserveBefore,
            progress = progress + (question.item.assignmentId to reverted),
            answered = answered.dropLast(1),
            lastGraded = null
        )
    }

    /**
     * Stops introducing brand-new items: only [current] and the items already attempted keep their
     * questions, and [totalQuestions] shrinks to what that leaves plus what is already answered.
     */
    fun wrappedUp(): QuizSession<T> {
        val currentId = current?.item?.assignmentId
        val queue = toQueue()
        queue.retainCurrentAndMatching {
            progress[it.item.assignmentId]?.hasAnyProgress == true || it.item.assignmentId == currentId
        }
        val kept = copy(inFlight = queue.toList(), reserve = queue.reserveList())
        return kept.copy(totalQuestions = kept.remainingQuestions + kept.completedQuestionCount)
    }

    /** The session summary over the items [counts] accepts — see [summarizeQuizSession]. */
    fun summary(totalElapsedMs: Long, counts: (QuizItemProgress<T>) -> Boolean = { true }): QuizSessionSummary<T> =
        summarizeQuizSession(progress.values.filter(counts), answered, totalElapsedMs)

    fun persistedInFlight(): List<PersistedQuestion> = inFlight.map { it.toPersisted() }
    fun persistedReserve(): List<PersistedQuestion> = reserve.map { it.toPersisted() }

    fun persistedProgress(): List<PersistedItemProgress> = progress.map { (id, p) ->
        PersistedItemProgress(
            assignmentId = id,
            meaningDone = p.meaningDone,
            readingDone = p.readingDone,
            hadIncorrectMeaning = p.hadIncorrectMeaning,
            hadIncorrectReading = p.hadIncorrectReading,
            incorrectMeaningCount = p.incorrectMeaningAttempts,
            incorrectReadingCount = p.incorrectReadingAttempts
        )
    }

    fun persistedAnswers(): List<PersistedAnsweredQuestion> = answered.map {
        PersistedAnsweredQuestion(it.item.assignmentId, it.type.name, it.isCorrect, it.elapsedMs)
    }

    private fun toQueue(): QuizQueue<T> = QuizQueue<T>().apply { restore(inFlight, reserve, shuffleOnAdmit) }

    companion object {
        /**
         * Rebuilds a persisted session against [itemsById], or null when a queued question cannot be
         * rebuilt — its assignment is gone, or its question type is one this build does not know. Both
         * features treat null as a corrupt snapshot and fetch afresh. Progress and answers for items
         * that no longer resolve are dropped rather than failing the resume.
         */
        fun <T : QuizDisplayItem> restore(
            itemsById: Map<Long, T>,
            inFlight: List<PersistedQuestion> = emptyList(),
            reserve: List<PersistedQuestion> = emptyList(),
            progress: List<PersistedItemProgress> = emptyList(),
            answered: List<PersistedAnsweredQuestion> = emptyList(),
            totalQuestions: Int = 0,
            priorityIds: Set<Long> = emptySet()
        ): QuizSession<T>? {
            val restoredInFlight = inFlight.mapNotNull { it.toPendingQuestionOrNull(itemsById) }
            val restoredReserve = reserve.mapNotNull { it.toPendingQuestionOrNull(itemsById) }
            if (restoredInFlight.size != inFlight.size || restoredReserve.size != reserve.size) return null
            return QuizSession(
                inFlight = restoredInFlight,
                reserve = restoredReserve,
                progress = progress.mapNotNull { p ->
                    val item = itemsById[p.assignmentId] ?: return@mapNotNull null
                    // A snapshot written before the counts were persisted carries 0 with the flag
                    // still set; it can prove "missed at least once", so that is the floor.
                    p.assignmentId to QuizItemProgress(
                        item = item,
                        meaningDone = p.meaningDone,
                        readingDone = p.readingDone,
                        incorrectMeaningAttempts = maxOf(p.incorrectMeaningCount, if (p.hadIncorrectMeaning) 1 else 0),
                        incorrectReadingAttempts = maxOf(p.incorrectReadingCount, if (p.hadIncorrectReading) 1 else 0)
                    )
                }.toMap(),
                answered = answered.mapNotNull { p ->
                    val item = itemsById[p.assignmentId] ?: return@mapNotNull null
                    val type = QuestionType.fromPersisted(p.questionType) ?: return@mapNotNull null
                    AnsweredQuestionRecord(item, type, p.isCorrect, p.elapsedMs)
                },
                totalQuestions = totalQuestions,
                priorityIds = priorityIds
            )
        }
    }
}

/** [QuizQueue]'s `isPriority` over [priorityIds] — null when there are none, which keeps the queue on
 *  its original no-priority path rather than a check that happens to reject everything. */
private fun <T : QuizDisplayItem> isPriorityFor(priorityIds: Set<Long>): ((T) -> Boolean)? =
    if (priorityIds.isEmpty()) null else { item -> item.assignmentId in priorityIds }

/** A graded question, as [QuizSession.lastGraded] keeps it. [completedItem] is whether this answer
 *  finished its item — every question type it has now answered correctly. [inFlightBefore] and
 *  [reserveBefore] are the queue as it stood before the grade, what [QuizSession.undoLastGrade] puts
 *  back; both are the session's own immutable lists, so keeping them copies nothing. */
data class GradedQuestion<T>(
    val question: PendingQuestion<T>,
    val isCorrect: Boolean,
    val completedItem: Boolean,
    val inFlightBefore: List<PendingQuestion<T>>,
    val reserveBefore: List<PendingQuestion<T>>
)

private fun <T : QuizDisplayItem> PendingQuestion<T>.toPersisted(): PersistedQuestion =
    PersistedQuestion(item.assignmentId, type.name)
