package com.crazyfluff.shellfstudy.core.quiz

data class PendingQuestion<T>(val item: T, val type: QuestionType)

/**
 * The shuffled, mutable pending-question queue shared by the lesson quiz and review queue —
 * purely a data structure: it knows nothing about grading, persistence, or item-specific
 * completion rules. Both call sites still own that logic themselves, via the predicates they pass
 * in here.
 */
class QuizQueue<T> {
    private val entries = ArrayDeque<PendingQuestion<T>>()

    val size: Int get() = entries.size
    val isEmpty: Boolean get() = entries.isEmpty()
    val current: PendingQuestion<T>? get() = entries.firstOrNull()

    fun clear() = entries.clear()

    /** Replaces the queue with one entry per (item, question type) pair, shuffled by default. */
    fun build(items: List<T>, typesFor: (T) -> List<QuestionType>, shuffle: Boolean = true) {
        entries.clear()
        items.forEach { item -> typesFor(item).forEach { type -> entries.addLast(PendingQuestion(item, type)) } }
        if (shuffle) entries.shuffle()
    }

    /** Replaces the queue with an already-ordered list, as reconstructed from a persisted session. */
    fun restore(pending: List<PendingQuestion<T>>) {
        entries.clear()
        entries.addAll(pending)
    }

    fun removeCurrent(): PendingQuestion<T>? = entries.removeFirstOrNull()

    fun requeue(question: PendingQuestion<T>) = entries.addLast(question)

    fun noneMatches(predicate: (PendingQuestion<T>) -> Boolean): Boolean = entries.none(predicate)

    /** Moves the last entry matching [predicate] back to the front — used by undo to restore a
     *  just-requeued wrong answer to "current" without dropping it from the queue entirely. */
    fun moveMatchingToFront(predicate: (PendingQuestion<T>) -> Boolean) {
        val index = entries.indexOfLast(predicate)
        if (index >= 0) entries.addFirst(entries.removeAt(index))
    }

    /** Keeps only the current entry plus any later entries matching [predicate] — used by wrap-up
     *  to stop introducing brand-new items while keeping ones already attempted. */
    fun retainCurrentAndMatching(predicate: (PendingQuestion<T>) -> Boolean) {
        val current = entries.firstOrNull()
        val rest = entries.drop(1).filter(predicate)
        entries.clear()
        current?.let(entries::addLast)
        entries.addAll(rest)
    }

    fun toList(): List<PendingQuestion<T>> = entries.toList()
}
