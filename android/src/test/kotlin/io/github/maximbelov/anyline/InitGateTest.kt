package io.github.maximbelov.anyline

import io.github.maximbelov.anyline.InitGate.Decision
import io.github.maximbelov.anyline.InitGate.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InitGateTest {
    private val gate = InitGate<String>()

    private fun request(key: String, call: String, offline: Boolean = false) = gate.request(Request(key, offline, call))

    @Test
    fun startsTheFirstInitialisation() {
        assertEquals(Decision.Start("a", true), request("a", "c1", offline = true))
        assertTrue(gate.isRunning)
    }

    @Test
    fun aSecondCallWithTheSameKeyJoinsTheRunningOneInsteadOfStartingAnother() {
        request("a", "c1")
        assertEquals(Decision.Wait, request("a", "c2"))
        assertEquals(Decision.Wait, request("a", "c3"))
        val settled = gate.settle(initialised = true)
        assertEquals(listOf("c1", "c2", "c3"), settled.settled)
        assertNull(settled.next)
        assertFalse(gate.isRunning)
    }

    @Test
    fun joinedCallsShareARefusal() {
        request("a", "c1")
        request("a", "c2")
        assertEquals(listOf("c1", "c2"), gate.settle(initialised = false).settled)
    }

    @Test
    fun aCallWithAnotherKeyWaitsForTheRunningOneThenRuns() {
        request("a", "c1")
        assertEquals(Decision.Wait, request("b", "c2"))
        assertEquals(Decision.Wait, request("b", "c3"))
        val first = gate.settle(initialised = false)
        assertEquals(listOf("c1"), first.settled)
        assertEquals(Decision.Start("b", false), first.next)
        assertTrue(gate.isRunning)
        assertEquals(listOf("c2", "c3"), gate.settle(initialised = true).settled)
    }

    @Test
    fun neverHasTwoInitialisationsRunning() {
        // Whatever arrives while one runs, the gate hands out at most one Start until settle is called.
        val decisions = listOf(request("a", "1"), request("b", "2"), request("a", "3"), request("c", "4"), request("b", "5"))
        assertEquals(1, decisions.count { it is Decision.Start })
        var starts = 0
        while (gate.isRunning) {
            if (gate.settle(initialised = true).next != null) starts++
        }
        // a, then b (with 5), then c: one at a time, in order of arrival.
        assertEquals(2, starts)
    }

    @Test
    fun aRepeatWithTheInitialisedKeyResolvesWithoutTheSdk() {
        request("a", "c1")
        gate.settle(initialised = true)
        assertEquals(Decision.AlreadyInitialised, request("a", "c2"))
        assertFalse(gate.isRunning)
    }

    @Test
    fun aRefusedKeyIsInitialisedAgainNextTime() {
        request("a", "c1")
        gate.settle(initialised = false)
        assertEquals(Decision.Start("a", false), request("a", "c2"))
    }

    @Test
    fun waitingCallsForTheKeyJustInitialisedAreServedWithoutAnotherStart() {
        request("a", "c1")
        request("b", "c2")
        gate.settle(initialised = false) // a refused: b starts
        request("a", "c3") // a again, behind b
        request("b", "c4") // joins b
        val settled = gate.settle(initialised = true)
        assertEquals(listOf("c2", "c4"), settled.settled)
        assertEquals(Decision.Start("a", false), settled.next)
    }

    @Test
    fun aCallWithTheRunningKeyJoinsItEvenWithOthersWaiting() {
        request("a", "c1")
        request("b", "c2")
        request("a", "c3")
        val settled = gate.settle(initialised = true)
        assertEquals(listOf("c1", "c3"), settled.settled)
        assertEquals(Decision.Start("b", false), settled.next)
    }

    @Test
    fun aSettleWithNothingRunningSettlesNothing() {
        val settled = gate.settle(initialised = true)
        assertTrue(settled.settled.isEmpty())
        assertNull(settled.next)
    }
}
