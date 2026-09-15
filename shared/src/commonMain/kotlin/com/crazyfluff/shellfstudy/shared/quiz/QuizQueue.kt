package com.crazyfluff.shellfstudy.shared.quiz

data class PendingQuestion<T>(val item: T, val type: QuestionType)

/**
 * Two pools of questions: [inFlight] is the working set a session actually draws from (front =
 * [current]); [reserve] holds items not yet admitted into it. Most callers never populate [reserve]
 * at all — `build(cap = null)` admits everything immediately, exactly like the single flat queue
 * this replaces, and [admitNext] is then permanently a no-op. Review is the one caller that passes a
 * real `cap`, to bound how many distinct items can be in flight at once (mirrors WaniKani's own
 * review session — see ReviewViewModel.MAX_IN_FLIGHT_REVIEW_ITEMS). Lesson never sets a cap; it
 * already bounds its own working set by batch size before ever building a queue.
 */
class QuizQueue<T> {
    private val inFlight = ArrayDeque<PendingQuestion<T>>()
    private val reserve = ArrayDeque<PendingQuestion<T>>()

    // Mirrors the `shuffle` a session was built with, so a later admitNext() randomizes a
    // newly-admitted item's position the same way build() would have — and, symmetrically, stays
    // off for shuffle=false test fixtures that rely on deterministic ordering.
    private var shuffleOnAdmit = true

    val size: Int get() = inFlight.size + reserve.size
    val isEmpty: Boolean get() = inFlight.isEmpty() && reserve.isEmpty()
    val current: PendingQuestion<T>? get() = inFlight.firstOrNull()

    /** Distinct items currently admitted into the working set — what [admitNext] compares against
     *  its cap. Counts an item the moment it's admitted, not the moment it's first answered: an
     *  admitted-but-untouched item still occupies its slot, the same way a fixed-size batch would. */
    val inFlightItemCount: Int get() = inFlight.distinctBy { it.item }.size

    fun clear() {
        inFlight.clear()
        reserve.clear()
    }

    /**
     * Builds the queue from [items]. With [cap] null (or >= the item count), every item is admitted
     * into [inFlight] immediately — the original all-at-once behavior Lesson's fixed-size batches
     * rely on. A finite [cap] admits only that many items up front, holding the rest in [reserve]
     * for [admitNext] to pull from as items finish.
     */
    fun build(items: List<T>, typesFor: (T) -> List<QuestionType>, shuffle: Boolean = true, cap: Int? = null) {
        shuffleOnAdmit = shuffle
        val order = if (shuffle) items.shuffled() else items
        val admittedItems = if (cap == null) order else order.take(cap)
        val heldItems = if (cap == null) emptyList() else order.drop(cap)

        inFlight.clear()
        inFlight.addAll(admittedItems.flatMap { item -> typesFor(item).map { PendingQuestion(item, it) } })
        // Shuffled as its own pass (not just inherited from the item-level shuffle above) so an
        // item's own question types land at independently random positions — otherwise they'd always
        // be adjacent, which would surface right back as meaning-then-reading even on a *wrong*
        // first answer (moveMatchingToBack only protects the correct-answer case).
        if (shuffle) inFlight.shuffle()

        reserve.clear()
        reserve.addAll(heldItems.flatMap { item -> typesFor(item).map { PendingQuestion(item, it) } })
    }

    /** Restores a previously-persisted split — [inFlight] defaults every existing caller that never
     *  used a cap to an empty [reserve]. */
    fun restore(inFlight: List<PendingQuestion<T>>, reserve: List<PendingQuestion<T>> = emptyList()) {
        this.inFlight.clear()
        this.inFlight.addAll(inFlight)
        this.reserve.clear()
        this.reserve.addAll(reserve)
    }

    fun removeCurrent(): PendingQuestion<T>? = inFlight.removeFirstOrNull()

    fun requeue(question: PendingQuestion<T>) = inFlight.addLast(question)

    /** Re-inserts a question removed via [removeCurrent] back at the front, so it stays "current" —
     *  used to undo a correct answer, which (unlike an incorrect one) isn't put back via [requeue]. */
    fun pushFront(question: PendingQuestion<T>) = inFlight.addFirst(question)

    fun noneMatches(predicate: (PendingQuestion<T>) -> Boolean): Boolean =
        inFlight.none(predicate) && reserve.none(predicate)

    fun moveMatchingToFront(predicate: (PendingQuestion<T>) -> Boolean) {
        val index = inFlight.indexOfLast(predicate)
        if (index >= 0) inFlight.addFirst(inFlight.removeAt(index))
    }

    /** Moves the first entry matching [predicate] to the back of the working set — used when an
     *  item's first question type is answered correctly and it isn't fully done yet: its still-
     *  pending sibling type is given the same tail placement a wrong answer gets via [requeue],
     *  instead of sitting wherever it landed when the item was admitted, so it isn't the entry most
     *  likely to be drawn again right away. */
    fun moveMatchingToBack(predicate: (PendingQuestion<T>) -> Boolean) {
        val index = inFlight.indexOfFirst(predicate)
        if (index >= 0) inFlight.addLast(inFlight.removeAt(index))
    }

    /**
     * Pulls one more item's worth of questions in from [reserve] — both its question types together
     * — if [inFlightItemCount] has room under [cap] and [reserve] isn't empty. Call whenever an item
     * might have just left [inFlight] for good (finished, or requeued-forever doesn't apply here —
     * only a genuine completion frees a slot). No-op otherwise, so it's safe to call unconditionally
     * after every graded answer rather than only when something's actually free.
     */
    fun admitNext(cap: Int) {
        if (inFlightItemCount >= cap) return
        val next = reserve.firstOrNull()?.item ?: return
        while (reserve.isNotEmpty() && reserve.first().item == next) {
            inFlight.addLast(reserve.removeFirst())
        }
        if (shuffleOnAdmit) inFlight.shuffle()
    }

    /** Keeps [current] and everything already admitted that matches [predicate]; drops the rest of
     *  [inFlight] AND the entire [reserve] outright — used by wrapUp, which stops introducing
     *  brand-new items entirely, not just the ones reserve happened to be holding back. */
    fun retainCurrentAndMatching(predicate: (PendingQuestion<T>) -> Boolean) {
        val current = inFlight.firstOrNull()
        val rest = inFlight.drop(1).filter(predicate)
        inFlight.clear()
        current?.let(inFlight::addLast)
        inFlight.addAll(rest)
        reserve.clear()
    }

    /** The working set, in draw order — everything, unless [build] was called with a [cap]. */
    fun toList(): List<PendingQuestion<T>> = inFlight.toList()

    /** Items still held back from [inFlight] — empty unless [build] was called with a [cap]. */
    fun reserveList(): List<PendingQuestion<T>> = reserve.toList()
}
