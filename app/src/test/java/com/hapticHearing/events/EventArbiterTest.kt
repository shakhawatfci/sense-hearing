package com.hapticHearing.events

import com.hapticHearing.haptics.VibrationPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventArbiterTest {
    private fun ev(id: String, p: EventPriority, ms: Long = 1000) =
        ArbiterEvent(id, p, VibrationPattern(id, longArrayOf(0, ms), intArrayOf(0, 200)))

    @Test
    fun `plays immediately when idle`() {
        val a = EventArbiter()
        assertEquals("a", a.submit(ev("a", EventPriority.NORMAL), 0)?.id)
    }

    @Test
    fun `higher priority interrupts, lower waits`() {
        val a = EventArbiter()
        a.submit(ev("dog", EventPriority.NORMAL), 0)
        assertEquals("siren", a.submit(ev("siren", EventPriority.DANGER), 100)?.id)
        assertNull(a.submit(ev("bell", EventPriority.HIGH), 200))
        assertNull(a.poll(500))
        assertEquals("bell", a.poll(1100)?.id)
    }

    @Test
    fun `queue keeps 3 and drops the oldest normal`() {
        val a = EventArbiter()
        a.submit(ev("playing", EventPriority.DANGER), 0)
        a.submit(ev("n1", EventPriority.NORMAL), 1)
        a.submit(ev("h1", EventPriority.HIGH), 2)
        a.submit(ev("n2", EventPriority.NORMAL), 3)
        a.submit(ev("h2", EventPriority.HIGH), 4) // drops n1
        val order = listOf(a.poll(1000)?.id, a.poll(2000)?.id, a.poll(3000)?.id, a.poll(4000)?.id)
        assertEquals(listOf("h1", "h2", "n2", null), order)
    }

    @Test
    fun `equal priority waits its turn`() {
        val a = EventArbiter()
        a.submit(ev("a", EventPriority.HIGH), 0)
        assertNull(a.submit(ev("b", EventPriority.HIGH), 10))
        assertEquals("b", a.poll(1000)?.id)
    }
}
