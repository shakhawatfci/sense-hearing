package com.hapticHearing.haptics

import com.hapticHearing.dsp.DspFrame
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticMapperTest {
    private fun frame(levels: FloatArray, active: BooleanArray, floor: Float = -70f) = DspFrame(
        timeNanos = 0,
        bandDbfs = levels,
        floorDbfs = FloatArray(3) { floor },
        active = active,
        overallDbfs = levels.max(),
        bandEdgesHz = floatArrayOf(100f, 431f, 1857f, 8000f),
        thresholdDbfs = -50f,
        noiseMarginDb = 8f,
    )

    @Test
    fun `drive level follows the spec formula`() {
        // threshold dominates: excess = L − max(F+8, −50)
        assertEquals(20f / 255f, HapticMapper.driveLevel(-50f, -70f, 8f, -50f), 1e-4f)
        assertEquals(1f, HapticMapper.driveLevel(-20f, -70f, 8f, -50f), 1e-4f)
        assertEquals(1f, HapticMapper.driveLevel(0f, -70f, 8f, -50f), 1e-4f)
        val half = (20f + 235f * Math.pow(0.5, 0.7).toFloat()) / 255f
        assertEquals(half, HapticMapper.driveLevel(-35f, -70f, 8f, -50f), 1e-4f)
    }

    @Test
    fun `louder is stronger and dominant band is the strongest active one`() {
        val m = HapticMapper()
        var out = HapticFrame.OFF
        repeat(5) { out = m.map(frame(floatArrayOf(-45f, -25f, -90f), booleanArrayOf(true, true, false)), HapticConfig()) }
        assertEquals(1, out.dominantBand)
        assertTrue(out.bands[1] > out.bands[0])
        assertEquals(0f, out.bands[2], 0f)
        // back channels mirror front ones
        assertArrayEquals(out.bands.copyOfRange(0, 3), out.bands.copyOfRange(3, 6), 0f)
    }

    @Test
    fun `fast attack, slower release, then off`() {
        val m = HapticMapper()
        val on = m.map(frame(floatArrayOf(-20f, -90f, -90f), booleanArrayOf(true, false, false)), HapticConfig())
        assertTrue("attack ~10 ms should reach >75% in one 16 ms frame", on.bands[0] > 0.75f)
        val r1 = m.map(frame(floatArrayOf(-90f, -90f, -90f), BooleanArray(3)), HapticConfig())
        assertTrue("release ~120 ms keeps most energy after one frame", r1.bands[0] > 0.8f * on.bands[0])
        var r = r1
        repeat(60) { r = m.map(frame(floatArrayOf(-90f, -90f, -90f), BooleanArray(3)), HapticConfig()) }
        assertFalse(r.isOn)
    }

    @Test
    fun `strength zero or continuous disabled means no vibration`() {
        val f = frame(floatArrayOf(-20f, -20f, -20f), booleanArrayOf(true, true, true))
        assertFalse(HapticMapper().map(f, HapticConfig(userStrength = 0f)).isOn)
        assertFalse(HapticMapper().map(f, HapticConfig(continuousEnabled = false)).isOn)
    }
}

class RhythmGeneratorTest {
    @Test
    fun `low band from phase 0 starts with a 120 ms pulse`() {
        val w = RhythmGenerator.build(0, 1f, 0, 300, hasAmplitudeControl = true)
        assertArrayEquals(longArrayOf(120, 130, 50), w.timings)
        assertArrayEquals(intArrayOf(255, 0, 255), w.amplitudes)
    }

    @Test
    fun `phase continues across refreshes`() {
        val w = RhythmGenerator.build(1, 1f, 50, 80, hasAmplitudeControl = true)
        // mid: 60 on / 65 off; at phase 50 → 10 ms on, then 65 off, then 5 on
        assertArrayEquals(longArrayOf(10, 65, 5), w.timings)
        assertEquals(80, w.totalMs)
    }

    @Test
    fun `high band amplitude is reduced`() {
        val w = RhythmGenerator.build(2, 1f, 0, 60, hasAmplitudeControl = true)
        assertTrue(w.amplitudes[0] < 255)
    }

    @Test
    fun `without amplitude control strength becomes duty cycle`() {
        val weak = RhythmGenerator.build(0, 0f, 0, 250, hasAmplitudeControl = false)
        val strong = RhythmGenerator.build(0, 1f, 0, 250, hasAmplitudeControl = false)
        assertTrue(weak.timings[0] < strong.timings[0])
        assertEquals(255, weak.amplitudes[0])
        assertEquals(250, weak.totalMs)
    }
}

class ContinuousHapticsTest {
    private class FakeOutput : ContinuousOutput {
        override val hasAmplitudeControl = true
        val played = ArrayList<Pair<Long, Waveform>>()
        var stops = 0
        var now = 0L
        override fun play(waveform: Waveform) { played += now to waveform }
        override fun stop() { stops++ }
    }

    private fun on(band: Int) = HapticFrame(FloatArray(6).also { it[band] = 0.8f; it[band + 3] = 0.8f }, band)

    @Test
    fun `onset sends immediately, then refreshes every 50 ms, then stops once`() {
        val out = FakeOutput()
        val c = ContinuousHaptics(out)
        var t = 0L
        fun step(f: HapticFrame) { out.now = t; c.onFrame(f, t); t += 16 }
        step(on(0))
        assertEquals("onset must vibrate on the first frame", 1, out.played.size)
        repeat(12) { step(on(0)) } // ~200 ms
        assertTrue(out.played.size in 4..6)
        val gaps = out.played.zipWithNext { a, b -> b.first - a.first }
        assertTrue(gaps.all { it in 48..64 })
        // waveform is longer than the refresh so there is no gap
        assertTrue(out.played.all { it.second.totalMs > 50 })
        step(HapticFrame.OFF)
        step(HapticFrame.OFF)
        assertEquals(1, out.stops)
    }

    @Test
    fun `band change restarts the rhythm immediately`() {
        val out = FakeOutput()
        val c = ContinuousHaptics(out)
        c.onFrame(on(0), 0)
        c.onFrame(on(2), 16)
        assertEquals(2, out.played.size)
        assertEquals(30L, out.played[1].second.timings[0]) // high band starts with a full 30 ms pulse
    }
}

class VibrationPatternsTest {
    @Test
    fun `presets are well formed`() {
        VibrationPatterns.all.forEach { p ->
            assertEquals(p.timings.size, p.amplitudes.size)
            assertTrue(p.amplitudes.all { it in 0..255 })
            assertTrue(p.amplitudes.any { it > 0 })
            assertEquals(0, p.amplitudes[0])
            assertTrue(p.totalMs > 0)
        }
        // NAME_CALL is repeated 3× with gaps
        assertEquals(18, VibrationPatterns.NAME_CALL.timings.size)
        assertEquals((400 + 150 + 400 + 150 + 800) * 3L + 2 * 300, VibrationPatterns.NAME_CALL.totalMs)
    }
}
