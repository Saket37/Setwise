package dev.saketanand.setwise.timer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRestTimerTest {

    /** The timer runs on the test's virtual clock. */
    private fun TestScope.timer(): Pair<DefaultRestTimer, MutableList<RestTimerState>> {
        val timer = DefaultRestTimer(backgroundScope) { testScheduler.currentTime }
        val finished = mutableListOf<RestTimerState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { timer.finished.toList(finished) }
        return timer to finished
    }

    @Test
    fun `a rest counts down and reports when it runs out`() = runTest {
        val (timer, finished) = timer()

        timer.start(workoutId = 1, durationSec = 90, next = NextUp.Set(3))
        assertEquals(90_000L, timer.state.value!!.remainingMillis(testScheduler.currentTime))

        advanceTimeBy(89_000); runCurrent()
        assertEquals(1_000L, timer.state.value!!.remainingMillis(testScheduler.currentTime))
        assertTrue(finished.isEmpty())

        advanceTimeBy(1_001); runCurrent()
        assertNull(timer.state.value)
        assertEquals(NextUp.Set(3), finished.single().next)
    }

    @Test
    fun `plus and minus 15 move the end and the total`() = runTest {
        val (timer, finished) = timer()
        timer.start(1, 60, NextUp.Nothing)

        timer.adjust(+15)
        assertEquals(75_000L, timer.state.value!!.totalMillis)
        advanceTimeBy(70_000); runCurrent()
        assertTrue("still resting at 70s of 75s", timer.state.value != null)

        timer.adjust(-15) // 5s left → below zero: ends quietly
        runCurrent()
        assertNull(timer.state.value)
        assertTrue("no alert for a rest cut short", finished.isEmpty())
    }

    @Test
    fun `skip ends without an alert`() = runTest {
        val (timer, finished) = timer()
        timer.start(1, 60, NextUp.Nothing)

        timer.skip()
        advanceTimeBy(120_000); runCurrent()

        assertNull(timer.state.value)
        assertTrue(finished.isEmpty())
    }

    @Test
    fun `a new rest replaces the running one`() = runTest {
        val (timer, finished) = timer()
        timer.start(1, 60, NextUp.Set(2))
        advanceTimeBy(30_000)

        timer.start(1, 90, NextUp.Set(3))
        advanceTimeBy(60_000); runCurrent()
        assertEquals("the first one doesn't fire", 0, finished.size)

        advanceTimeBy(31_000); runCurrent()
        assertEquals(NextUp.Set(3), finished.single().next)
    }

    @Test
    fun `cancel only stops the rest of that workout`() = runTest {
        val (timer, _) = timer()
        timer.start(workoutId = 1, durationSec = 60, next = NextUp.Nothing)

        timer.cancel(workoutId = 2)
        assertEquals(1L, timer.state.value?.workoutId)

        timer.cancel(workoutId = 1)
        assertNull(timer.state.value)
    }

    @Test
    fun `no rest for exercises with no rest time`() = runTest {
        val (timer, _) = timer()
        timer.start(1, 0, NextUp.Nothing)
        assertNull(timer.state.value)
    }
}
