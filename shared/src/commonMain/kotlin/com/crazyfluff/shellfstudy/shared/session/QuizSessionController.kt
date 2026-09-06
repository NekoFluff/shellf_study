package com.crazyfluff.shellfstudy.shared.session

import com.crazyfluff.shellfstudy.shared.coroutines.SerialDurableWork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

enum class SessionLifecycleState { IDLE, ACTIVE }

/** The minimal shape [QuizSessionController] needs from a session's DataStore-backed repository —
 *  implemented by LessonSessionRepository/ReviewSessionRepository so the controller stays generic
 *  over either payload type. */
interface PersistedSessionStore<T> {
    val hasActiveSession: Flow<Boolean>
    suspend fun save(session: T)
    suspend fun load(): T?
    suspend fun clear()
}

/**
 * The single gatekeeper for a lesson or review session's persisted state — every save/clear for a
 * given feature goes through one controller instance (a process-wide Koin singleton, not scoped to
 * the owning ViewModel), so both the owning ViewModel and any out-of-band caller (Dashboard's
 * abandon action, account logout) share the same write-ordering queue and the same completion lock.
 *
 * The completion lock is what makes "no save after completion" a structural guarantee rather than a
 * call-site convention: [complete]/[abandon] flip [state] to IDLE synchronously, before any
 * suspension, so a save that was legitimately *ordered* after a completion (e.g. a menu action
 * queued right after the last question was graded) still finds the session IDLE and no-ops — the
 * ordering guarantee [SerialDurableWork] provides is not what's being relied on here; the state
 * check running before a write is even enqueued is.
 */
class QuizSessionController<T : Any>(
    scope: CoroutineScope,
    private val store: PersistedSessionStore<T>
) {
    private val writeQueue = SerialDurableWork(scope)
    private val _state = MutableStateFlow(SessionLifecycleState.IDLE)
    val hasActiveSession: Flow<Boolean> = store.hasActiveSession

    suspend fun load(): T? = store.load()

    /** Call once a fresh or resumed session is actually committed to. Idempotent. */
    fun begin() {
        _state.value = SessionLifecycleState.ACTIVE
    }

    /** No-op if the session has already ended — regardless of which call site issued this save or
     *  how delayed it was. See the class doc for why this is the actual "can't save after complete"
     *  guarantee.
     *
     *  [alongside] runs as part of the same queued, ordered unit as the save itself — for a caller
     *  with an additional durable side effect (e.g. an outbox enqueue) that must settle inseparably
     *  from this write, rather than as a second, independently-awaited suspension. Splitting such a
     *  side effect into its own separate `launch().join()` before calling [persist] reintroduces a
     *  window where a concurrent reader can observe this write as not-yet-applied even though the
     *  caller has, from its own point of view, already "finished" persisting. */
    suspend fun persist(snapshot: T, alongside: suspend () -> Unit = {}) {
        if (_state.value != SessionLifecycleState.ACTIVE) return
        writeQueue.run { alongside(); store.save(snapshot) }
    }

    /** Ends the session and clears its persisted state. Idempotent — safe to call again from a
     *  later explicit abandon/dashboard action after grading already completed the session. See
     *  [persist]'s [alongside] parameter for why a caller's extra durable side effect belongs here
     *  rather than in its own separately-awaited suspension. */
    suspend fun complete(alongside: suspend () -> Unit = {}) {
        _state.value = SessionLifecycleState.IDLE
        writeQueue.run { alongside(); store.clear() }
    }

    /** Alias for [complete], for call-site clarity where a session is ended out-of-band (Dashboard,
     *  account logout) rather than by its own owning ViewModel reaching a natural completion. */
    suspend fun abandon() = complete()
}
