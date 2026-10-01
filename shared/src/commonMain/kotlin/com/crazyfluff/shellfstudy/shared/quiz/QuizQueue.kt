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
 *
 * [reserve] is a FIFO: admission always takes from its head, so the order it was built with decides
 * which items get admitted next. Review's rank-up priority setting rides on that, via [build]'s
 * [isPriority] — see [admitNext] for the admission rule and the invariants it keeps.
 */
class QuizQueue<T> {
    private val inFlight = ArrayDeque<PendingQuestion<T>>()
    private val reserve = ArrayDeque<PendingQuestion<T>>()

    // Mirrors the `shuffle` a session was built with, so a later admitNext() randomizes a
    // newly-admitted item's position the same way build() would have — and, symmetrically, stays
    // off for shuffle=false test fixtures that rely on deterministic ordering.
    var shuffleOnAdmit = true
        private set

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
     *
     * [isPriority] is Review's rank-up priority setting. Without it (every caller but Review),
     * [items] is shuffled as one pool and the first [cap] are admitted, so the *selection* is an
     * arbitrary draw. With it, [items] is stably sorted priority-first instead and admitted by
     * [admitNext]'s rule, so the working set holds only priority items until every one of them is
     * finished. Within each group the caller's own order survives. Draw order inside [inFlight] is
     * still shuffled either way.
     */
    fun build(
        items: List<T>,
        typesFor: (T) -> List<QuestionType>,
        shuffle: Boolean = true,
        cap: Int? = null,
        isPriority: ((T) -> Boolean)? = null
    ) {
        shuffleOnAdmit = shuffle
        inFlight.clear()
        reserve.clear()
        if (isPriority == null) {
            val order = if (shuffle) items.shuffled() else items
            val admittedItems = if (cap == null) order else order.take(cap)
            val heldItems = if (cap == null) emptyList() else order.drop(cap)
            inFlight.addAll(admittedItems.flatMap { item -> typesFor(item).map { PendingQuestion(item, it) } })
            reserve.addAll(heldItems.flatMap { item -> typesFor(item).map { PendingQuestion(item, it) } })
        } else {
            // Sorted, NOT shuffled: which items get admitted (and the order the reserve feeds them in)
            // is the whole point of the priority setting, so randomizing here would randomize the
            // selection. Ties keep `items`' own order — sortedBy is stable. Everything starts in the
            // reserve so the opening admission follows exactly the rule every later one does.
            val sorted = items.sortedBy { if (isPriority(it)) 0 else 1 }
            reserve.addAll(sorted.flatMap { item -> typesFor(item).map { PendingQuestion(item, it) } })
            admitWhileRoom(cap ?: Int.MAX_VALUE, isPriority)
        }
        // Shuffled as its own pass (not just inherited from the item-level shuffle above) so an
        // item's own question types land at independently random positions — otherwise they'd always
        // be adjacent, which would surface right back as meaning-then-reading even on a *wrong*
        // first answer (moveMatchingToBack only protects the correct-answer case).
        if (shuffle) inFlight.shuffle()
    }

    /** Restores a previously-persisted split — [inFlight] defaults every existing caller that never
     *  used a cap to an empty [reserve]. [shuffleOnAdmit] carries over the `shuffle` the queue was
     *  first built with, for a copy that will go on admitting from [reserve]. */
    fun restore(
        inFlight: List<PendingQuestion<T>>,
        reserve: List<PendingQuestion<T>> = emptyList(),
        shuffleOnAdmit: Boolean = true
    ) {
        this.shuffleOnAdmit = shuffleOnAdmit
        this.inFlight.clear()
        this.inFlight.addAll(inFlight)
        this.reserve.clear()
        this.reserve.addAll(reserve)
    }

    fun removeCurrent(): PendingQuestion<T>? = inFlight.removeFirstOrNull()

    fun requeue(question: PendingQuestion<T>) = inFlight.addLast(question)

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
     * Pulls items in from [reserve] — each item's question types together — while [inFlightItemCount]
     * has room under [cap]. No-op when nothing is free, so it's safe to call unconditionally after
     * every graded answer rather than only when something's actually free. Returns the items admitted.
     *
     * With [isPriority], an ordinary (non-priority) item is admitted only once no priority item is
     * left in flight; a priority item is always admitted up to [cap]. So the working set holds only
     * priority items until every one of them is finished, then fills with ordinary ones. This holds
     * because:
     *  - [build] sorts [reserve] priority-first and nothing re-sorts it, so once its head is ordinary
     *    no priority item is left behind it;
     *  - an empty working set has no priority item in it, so it always admits and the session always
     *    drains;
     *  - [cap] is checked before every admission.
     * Without [isPriority] the rule is plain "room under [cap]", unchanged from before the setting
     * existed.
     */
    fun admitNext(cap: Int, isPriority: ((T) -> Boolean)? = null): List<T> {
        val admitted = admitWhileRoom(cap, isPriority)
        if (admitted.isNotEmpty() && shuffleOnAdmit) inFlight.shuffle()
        return admitted
    }

    private fun admitWhileRoom(cap: Int, isPriority: ((T) -> Boolean)?): List<T> {
        val admitted = mutableListOf<T>()
        var next = reserve.firstOrNull()?.item
        while (next != null && canAdmit(next, cap, isPriority)) {
            while (reserve.isNotEmpty() && reserve.first().item == next) {
                inFlight.addLast(reserve.removeFirst())
            }
            admitted += next
            next = reserve.firstOrNull()?.item
        }
        return admitted
    }

    /** [admitNext]'s rule for the reserve's head, [next]. */
    private fun canAdmit(next: T, cap: Int, isPriority: ((T) -> Boolean)?): Boolean {
        val priorityAllows = isPriority == null || isPriority(next) || inFlight.none { isPriority(it.item) }
        return inFlightItemCount < cap && priorityAllows
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
