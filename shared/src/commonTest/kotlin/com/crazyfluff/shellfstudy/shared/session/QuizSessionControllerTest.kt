package com.crazyfluff.shellfstudy.shared.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class FakeStore : PersistedSessionStore<String> {
    private val _hasActiveSession = MutableStateFlow(false)
    override val hasActiveSession = _hasActiveSession
    var stored: String? = null
        private set
    var saveCount = 0
        private set
    var clearCount = 0
        private set

    override suspend fun save(session: String) {
        stored = session
        saveCount++
        _hasActiveSession.value = true
    }

    override suspend fun load(): String? = stored

    override suspend fun clear() {
        stored = null
        clearCount++
        _hasActiveSession.value = false
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class QuizSessionControllerTest {

    @Test
    fun persistBeforeBeginIsANoOp() = runTest {
        val store = FakeStore()
        val controller = QuizSessionController(this, store)

        controller.persist("should not be saved")

        assertNull(store.stored)
        assertEquals(0, store.saveCount)
    }

    @Test
    fun persistAfterBeginSavesTheSnapshot() = runTest {
        val store = FakeStore()
        val controller = QuizSessionController(this, store)

        controller.begin()
        controller.persist("snapshot-1")

        assertEquals("snapshot-1", store.stored)
    }

    @Test
    fun persistAfterCompleteIsANoOp_evenThoughItWasLegitimatelyOrderedAfter() = runTest {
        // This is the actual bug this class exists to make impossible: a save issued after
        // completion — even one that's correctly ordered after the clear, like a menu action queued
        // right after the last question was graded — must never resurrect a cleared session.
        val store = FakeStore()
        val controller = QuizSessionController(this, store)
        controller.begin()
        controller.persist("in-progress")

        controller.complete()
        controller.persist("late save, should be rejected")

        assertNull(store.stored)
    }

    @Test
    fun completeIsUnconditional_evenIfNeverBegun() = runTest {
        val store = FakeStore()
        val controller = QuizSessionController(this, store)
        store.save("leftover from a previous run")

        controller.complete()

        assertNull(store.stored)
    }

    @Test
    fun completeIsIdempotent() = runTest {
        val store = FakeStore()
        val controller = QuizSessionController(this, store)
        controller.begin()
        controller.persist("snapshot")

        controller.complete()
        controller.complete()

        assertNull(store.stored)
        assertEquals(2, store.clearCount)
    }

    @Test
    fun beginAfterCompleteReactivatesPersist() = runTest {
        // A fresh session (new queue built, or a resume) must be able to persist again after a
        // previous session using the same controller instance completed.
        val store = FakeStore()
        val controller = QuizSessionController(this, store)
        controller.begin()
        controller.complete()

        controller.begin()
        controller.persist("new session")

        assertEquals("new session", store.stored)
    }

    @Test
    fun alongsideRunsAsPartOfTheSameQueuedUnitAsComplete() = runTest {
        val store = FakeStore()
        val controller = QuizSessionController(this, store)
        controller.begin()
        val order = mutableListOf<String>()

        controller.complete(alongside = { order.add("side-effect") })
        order.add("after-complete-returns")

        assertEquals(listOf("side-effect", "after-complete-returns"), order)
        assertEquals(1, store.clearCount)
    }

    @Test
    fun loadDelegatesToTheStore() = runTest {
        val store = FakeStore()
        store.save("existing")
        val controller = QuizSessionController(this, store)

        assertEquals("existing", controller.load())
    }
}
