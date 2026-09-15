package com.crazyfluff.shellfstudy.shared.quiz

data class PendingQuestion<T>(val item: T, val type: QuestionType)

class QuizQueue<T> {
    private val entries = ArrayDeque<PendingQuestion<T>>()

    val size: Int get() = entries.size
    val isEmpty: Boolean get() = entries.isEmpty()
    val current: PendingQuestion<T>? get() = entries.firstOrNull()

    fun clear() = entries.clear()

    fun build(items: List<T>, typesFor: (T) -> List<QuestionType>, shuffle: Boolean = true) {
        entries.clear()
        items.forEach { item -> typesFor(item).forEach { type -> entries.addLast(PendingQuestion(item, type)) } }
        if (shuffle) entries.shuffle()
    }

    fun restore(pending: List<PendingQuestion<T>>) {
        entries.clear()
        entries.addAll(pending)
    }

    fun removeCurrent(): PendingQuestion<T>? = entries.removeFirstOrNull()

    fun requeue(question: PendingQuestion<T>) = entries.addLast(question)

    /** Re-inserts a question removed via [removeCurrent] back at the front, so it stays "current" —
     *  used to undo a correct answer, which (unlike an incorrect one) isn't put back via [requeue]. */
    fun pushFront(question: PendingQuestion<T>) = entries.addFirst(question)

    fun noneMatches(predicate: (PendingQuestion<T>) -> Boolean): Boolean = entries.none(predicate)

    fun moveMatchingToFront(predicate: (PendingQuestion<T>) -> Boolean) {
        val index = entries.indexOfLast(predicate)
        if (index >= 0) entries.addFirst(entries.removeAt(index))
    }

    /** Caps how many distinct items can be in flight at once — mirrors WaniKani's own review
     *  session, which stops introducing new items once a fixed number are already being worked on
     *  (see ReviewViewModel.MAX_IN_FLIGHT_REVIEW_ITEMS). If [inFlightCount] has already reached
     *  [cap] and the front entry belongs to an item [isStarted] says hasn't been started yet, the
     *  front is swapped for the first already-started entry instead, so that one is drawn next. A
     *  no-op below [cap], and left alone if every remaining entry is for a not-yet-started item —
     *  there's nothing else left to offer in that case. */
    fun capInFlight(isStarted: (T) -> Boolean, inFlightCount: Int, cap: Int) {
        if (inFlightCount < cap) return
        if (entries.isEmpty() || isStarted(entries.first().item)) return
        val index = entries.indexOfFirst { isStarted(it.item) }
        if (index > 0) entries.addFirst(entries.removeAt(index))
    }

    fun retainCurrentAndMatching(predicate: (PendingQuestion<T>) -> Boolean) {
        val current = entries.firstOrNull()
        val rest = entries.drop(1).filter(predicate)
        entries.clear()
        current?.let(entries::addLast)
        entries.addAll(rest)
    }

    fun toList(): List<PendingQuestion<T>> = entries.toList()
}
