package com.crazyfluff.shellfstudy.shared.quiz

class QuizItemProgress<T>(val item: T) {
    var meaningDone = false
    var readingDone = false

    // Counts, not flags — a second wrong attempt on a retry (after an earlier miss was already
    // committed via Continue) must not be indistinguishable from the item's only wrong attempt.
    // Undo reverts exactly the attempt it's undoing (decrement) rather than clearing all memory of
    // being wrong (reset to zero), so an earlier, never-undone miss survives undoing a later one.
    private var incorrectMeaningCount = 0
    private var incorrectReadingCount = 0

    // Boolean view for callers that only care "was this ever wrong" (grading, persistence,
    // summaries) — setter is used only when restoring from persisted state, where only the
    // had-any-miss boolean survives a process restart, never the exact count.
    var hadIncorrectMeaning: Boolean
        get() = incorrectMeaningCount > 0
        set(value) { incorrectMeaningCount = if (value) maxOf(incorrectMeaningCount, 1) else 0 }
    var hadIncorrectReading: Boolean
        get() = incorrectReadingCount > 0
        set(value) { incorrectReadingCount = if (value) maxOf(incorrectReadingCount, 1) else 0 }

    fun recordIncorrectMeaning() { incorrectMeaningCount++ }
    fun recordIncorrectReading() { incorrectReadingCount++ }
    fun revertIncorrectMeaning() { if (incorrectMeaningCount > 0) incorrectMeaningCount-- }
    fun revertIncorrectReading() { if (incorrectReadingCount > 0) incorrectReadingCount-- }

    val hasAnyProgress: Boolean get() = meaningDone || readingDone || hadIncorrectMeaning || hadIncorrectReading
}
