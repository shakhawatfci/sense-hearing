package com.hapticHearing.haptics

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalHapticsTest {
    private class FakeOutput(override val hasAmplitudeControl: Boolean = true) : ContinuousOutput {
        val played = ArrayList<Waveform>()
        var stops = 0
        override fun play(waveform: Waveform) { played += waveform }
        override fun stop() { stops++ }
    }

    private fun frame(drive: Float) =
        if (drive <= 0f) HapticFrame.OFF else HapticFrame(FloatArray(6).also { it[1] = drive; it[4] = drive }, 1)

    @Test
    fun `strength follows loudness`() {
        val out = FakeOutput()
        val n = NaturalHaptics(out)
        n.onFrame(frame(0.2f), 0)
        n.onFrame(frame(0.9f), 16) // big change → sent at once
        val a1 = out.played.first().amplitudes.last()
        val a2 = out.played.last().amplitudes.last()
        assertTrue("louder must vibrate stronger ($a1 → $a2)", a2 > a1)
    }

    @Test
    fun `onset starts with a full tap`() {
        val out = FakeOutput()
        val n = NaturalHaptics(out)
        n.onFrame(frame(0.5f), 0)
        val w = out.played.single()
        assertEquals(2, w.timings.size)
        assertEquals(20L, w.timings[0])
        assertTrue(w.amplitudes[0] > w.amplitudes[1])
        assertEquals(48L, w.totalMs)
    }

    @Test
    fun `steady level refreshes about every 32 ms and stops when quiet`() {
        val out = FakeOutput()
        val n = NaturalHaptics(out)
        var t = 0L
        repeat(13) { n.onFrame(frame(0.5f), t); t += 16 } // ~200 ms
        assertTrue(out.played.size in 6..8)
        n.onFrame(frame(0f), t)
        n.onFrame(frame(0f), t + 16)
        assertEquals(1, out.stops)
    }

    @Test
    fun `gap between syllables stops then restarts with a tap`() {
        val out = FakeOutput()
        val n = NaturalHaptics(out)
        n.onFrame(frame(0.6f), 0)
        n.onFrame(frame(0f), 16)
        n.onFrame(frame(0.6f), 32)
        assertEquals(1, out.stops)
        assertEquals(2, out.played.last().timings.size) // tap + body again
    }

    @Test
    fun `without amplitude control strength is a duty cycle`() {
        val n = NaturalHaptics(FakeOutput(hasAmplitudeControl = false))
        val weak = n.build(0.1f, onset = false, hasAmplitudeControl = false)
        val strong = n.build(1f, onset = false, hasAmplitudeControl = false)
        assertEquals(48L, weak.totalMs)
        assertEquals(48L, strong.totalMs)
        fun onTime(w: Waveform) = w.timings.indices.filter { w.amplitudes[it] > 0 }.sumOf { w.timings[it] }
        assertTrue(onTime(strong) > onTime(weak))
        assertArrayEquals(intArrayOf(255), strong.amplitudes.distinct().toIntArray())
    }
}
