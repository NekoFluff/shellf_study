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

    fun noneMatches(predicate: (PendingQuestion<T>) -> Boolean): Boolean = entries.none(predicate)

    fun moveMatchingToFront(predicate: (PendingQuestion<T>) -> Boolean) {
        val index = entries.indexOfLast(predicate)
        if (index >= 0) entries.addFirst(entries.removeAt(index))
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
