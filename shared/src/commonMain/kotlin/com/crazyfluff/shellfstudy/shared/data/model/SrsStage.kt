package com.crazyfluff.shellfstudy.shared.data.model

/** WaniKani's SRS stage scale (0-9): 0 is locked/not yet started, 1-4 are the four Apprentice
 *  sub-stages, 5-6 are the two Guru sub-stages, then Master, Enlightened, and Burned. */
enum class SrsStage(val raw: Int, val displayName: String) {
    LOCKED(0, "Locked"),
    APPRENTICE_1(1, "Apprentice I"),
    APPRENTICE_2(2, "Apprentice II"),
    APPRENTICE_3(3, "Apprentice III"),
    APPRENTICE_4(4, "Apprentice IV"),
    GURU_1(5, "Guru I"),
    GURU_2(6, "Guru II"),
    MASTER(7, "Master"),
    ENLIGHTENED(8, "Enlightened"),
    BURNED(9, "Burned");

    companion object {
        fun fromRaw(raw: Int): SrsStage = entries.firstOrNull { it.raw == raw } ?: LOCKED
    }
}

/** An item's SRS rank changed as a result of completing it in review or a lesson — surfaced once
 *  per item, not once per sub-question, and cleared when advancing to the next question. */
data class RankChange(val from: SrsStage, val to: SrsStage) {
    val isRankUp: Boolean get() = to.raw > from.raw
}
