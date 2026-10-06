package com.mikczemny.prompter.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CaptureSessionGateTest {
    @Test
    fun `repeated start stays blocked during preparation listening and cleanup`() {
        val gate = CaptureSessionGate()
        val session = requireNotNull(gate.begin())

        assertTrue(gate.isActive)
        assertFalse(gate.isListening)
        assertNull(gate.begin())
        assertTrue(gate.startListening(session) {})
        assertFalse(gate.startListening(session) { error("Microphone started twice") })
        assertTrue(gate.isListening)
        assertNull(gate.begin())

        assertSame(session, gate.requestStop())
        assertFalse(gate.isListening)
        assertTrue("Stop must keep the cleanup reservation", gate.isActive)
        assertNull(gate.begin())

        assertTrue(gate.finish(session))
        assertFalse(gate.isActive)
        assertNotNull(gate.begin())
    }

    @Test
    fun `stop during model preparation prevents the microphone from opening`() {
        val gate = CaptureSessionGate()
        val session = requireNotNull(gate.begin())
        var microphoneStarts = 0

        gate.requestStop()
        assertFalse(gate.mayContinue(session))
        assertFalse(gate.startListening(session) { microphoneStarts++ })
        assertEquals(0, microphoneStarts)
        assertFalse(gate.mayEmit(session))
        assertNull(gate.begin())

        gate.finish(session)
        val replacement = requireNotNull(gate.begin())
        assertTrue(gate.startListening(replacement) { microphoneStarts++ })
        assertEquals(1, microphoneStarts)
    }

    @Test
    fun `simultaneous starts reserve exactly one capture session`() {
        val gate = CaptureSessionGate()
        val workers = Executors.newFixedThreadPool(8)
        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        try {
            val attempts = (1..8).map {
                workers.submit<CaptureSessionGate.Session?> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    gate.begin()
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()

            val accepted = attempts.map { it.get(5, TimeUnit.SECONDS) }.filterNotNull()
            assertEquals(1, accepted.size)
            assertTrue(gate.isActive)
            assertTrue(gate.finish(accepted.single()))
        } finally {
            start.countDown()
            workers.shutdownNow()
        }
    }

    @Test
    fun `failed microphone start still reserves its resources until cleanup`() {
        val gate = CaptureSessionGate()
        val session = requireNotNull(gate.begin())

        assertThrows(IllegalStateException::class.java) {
            gate.startListening(session) { error("Microphone unavailable") }
        }
        assertFalse(gate.isListening)
        assertTrue(gate.isActive)
        assertNull(gate.begin())

        assertTrue(gate.finish(session))
        assertNotNull(gate.begin())
    }

    @Test
    fun `delayed cleanup and focus loss cannot affect a replacement session`() {
        val gate = CaptureSessionGate()
        val old = requireNotNull(gate.begin())
        gate.startListening(old) {}
        assertTrue(gate.requestStop(old))
        assertFalse("Repeated focus loss is handled once", gate.requestStop(old))
        gate.finish(old)

        val current = requireNotNull(gate.begin())
        gate.startListening(current) {}
        assertFalse(gate.requestStop(old))
        assertFalse(gate.finish(old))
        assertFalse(gate.mayContinue(old))
        assertFalse(gate.mayEmit(old))
        assertTrue(gate.isListening(current))
        assertTrue(gate.mayEmit(current))
    }

    @Test
    fun `jump suppresses in-flight results until capture consumes the reset`() {
        val gate = CaptureSessionGate()
        val session = requireNotNull(gate.begin())
        gate.startListening(session) {}
        assertTrue(gate.mayEmit(session))

        gate.requestReset()
        gate.requestReset()
        assertFalse(gate.mayEmit(session))
        assertTrue(gate.consumeReset(session))
        assertFalse("Multiple jumps coalesce at the audio boundary", gate.consumeReset(session))
        assertTrue(gate.mayEmit(session))

        gate.requestReset()
        assertFalse(gate.mayEmit(session))
        assertTrue(gate.consumeReset(session))
    }

    @Test
    fun `stop suppresses results and cannot be undone by a transcript reset`() {
        val gate = CaptureSessionGate()
        val session = requireNotNull(gate.begin())
        gate.startListening(session) {}
        gate.requestReset()
        gate.requestStop()
        gate.requestReset()

        assertFalse(gate.consumeReset(session))
        assertFalse(gate.mayEmit(session))
        assertFalse(gate.mayContinue(session))
        assertFalse(gate.startListening(session) { error("Stopped session restarted") })
    }

    @Test
    fun `reset while idle does not leak into a future capture session`() {
        val gate = CaptureSessionGate()
        gate.requestReset()
        val session = requireNotNull(gate.begin())
        gate.startListening(session) {}
        assertFalse(gate.consumeReset(session))
        assertTrue(gate.mayEmit(session))

        gate.requestReset()
        gate.requestStop()
        gate.finish(session)
        val next = requireNotNull(gate.begin())
        gate.startListening(next) {}
        assertFalse(gate.consumeReset(next))
        assertTrue(gate.mayEmit(next))
    }
}
