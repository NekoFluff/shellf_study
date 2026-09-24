package com.crazyfluff.shellfstudy.shared.quiz

class QuizItemProgress<T>(val item: T) {
    var meaningDone = false
    var readingDone = false

    // Counts, not flags — a second wrong attempt on a retry (after an earlier miss was already
    // committed via Continue) must not be indistinguishable from the item's only wrong attempt.
    // Undo reverts exactly the attempt it's undoing (decrement) rather than clearing all memory of
    // being wrong (reset to zero), so an earlier, never-undone miss survives undoing a later one.
    //
    // Readable so the grade handed to WaniKani (and to the rank-change prediction) can carry the
    // real count: its demotion rule is ceil(incorrect / 2) * penalty, so the count changes the
    // resulting stage, not just the statistics.
    private var incorrectMeaningCount = 0
    private var incorrectReadingCount = 0

    /** Wrong meaning answers recorded for this item so far (undo decrements it). */
    val incorrectMeaningAttempts: Int get() = incorrectMeaningCount

    /** Wrong reading answers recorded for this item so far (undo decrements it). */
    val incorrectReadingAttempts: Int get() = incorrectReadingCount

    /**
     * Restores counts from a persisted session, never lowering an already-recorded one. Snapshots
     * written before the counts were persisted carry 0 with [hadIncorrectMeaning]/[hadIncorrectReading]
     * still true — the boolean setters have already floored those at 1, so a 0 here is a no-op and an
     * older snapshot degrades to "missed at least once" rather than being lost.
     */
    fun restoreIncorrectCounts(meaning: Int, reading: Int) {
        incorrectMeaningCount = maxOf(incorrectMeaningCount, meaning)
        incorrectReadingCount = maxOf(incorrectReadingCount, reading)
    }

    // Boolean view for callers that only care "was this ever wrong" (grading, summaries). The setter
    // is used when restoring from persisted state: it floors the count at 1, which is what a snapshot
    // written before the counts were persisted can prove. A snapshot that does carry counts raises
    // them past this via restoreIncorrectCounts.
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
