package com.hapticHearing.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec §5.2 / §15: steady noise stops vibrating, louder sounds on top still vibrate. */
class NoiseFloorTest {
    private val frameSec = 256f / 16000f

    private fun run(signal: FloatArray, cfg: DspConfig = DspConfig(thresholdDbfs = -90f)): List<DspFrame> {
        val dsp = DspProcessor(nanoClock = { 0L })
        dsp.config = cfg
        val frames = ArrayList<DspFrame>()
        signal.asList().chunked(256).forEach { c -> dsp.process(c.toFloatArray()) { frames += it } }
        return frames
    }

    private fun timeOf(i: Int) = (i + 2) * frameSec // first frame needs 2 hops

    @Test
    fun `stationary white noise is inactive within 5 s`() {
        val frames = run(TestSignals.whiteNoise(0.03f, 10f))
        val late = frames.filterIndexed { i, _ -> timeOf(i) >= 5f }
        assertTrue(late.isNotEmpty())
        assertTrue("noise still active after 5 s", late.none { it.anyActive })
    }

    @Test
    fun `fan switching on is felt first, then absorbed within 5 s`() {
        val quiet = TestSignals.whiteNoise(0.0003f, 2f, seed = 1)
        val fan = TestSignals.whiteNoise(0.03f, 8f, seed = 2)
        val frames = run(concat(quiet, fan))
        val onsetIdx = frames.indices.first { timeOf(it) >= 2.1f }
        assertTrue("fan onset should vibrate", frames[onsetIdx].anyActive)
        val after5 = frames.filterIndexed { i, _ -> timeOf(i) >= 2f + 5f }
        assertTrue("fan still active 5 s after it started", after5.none { it.anyActive })
    }

    @Test
    fun `burst 15 dB above steady noise is active`() {
        val noise = TestSignals.whiteNoise(0.01f, 8f, seed = 3)
        val burst = TestSignals.whiteNoise(0.01f * TestSignals.dbToAmp(15f), 0.3f, seed = 4)
        val signal = concat(noise, burst)
        val frames = run(signal)
        val burstFrames = frames.filterIndexed { i, _ -> timeOf(i) > 8.05f && timeOf(i) < 8.25f }
        assertTrue(burstFrames.isNotEmpty())
        assertTrue("burst must be active in every band", burstFrames.all { f -> f.active.all { it } })
    }

    @Test
    fun `voice-like tone over fan is active, fan alone is not`() {
        val fan = TestSignals.whiteNoise(0.01f, 9f, seed = 5)
        val tone = TestSignals.sine(800f, 0.1f, 1f)
        val signal = fan.copyOf()
        for (i in tone.indices) signal[8 * 16000 + i] += tone[i]
        val frames = run(signal)
        val before = frames.filterIndexed { i, _ -> timeOf(i) in 6f..7.9f }
        val during = frames.filterIndexed { i, _ -> timeOf(i) in 8.1f..8.5f }
        assertTrue(before.none { it.anyActive })
        assertTrue(during.all { it.active[1] })
    }

    @Test
    fun `quiet sounds below the user threshold do not vibrate`() {
        val quiet = TestSignals.whiteNoise(0.00001f, 1f, seed = 6)
        val tone = TestSignals.sine(1000f, 0.001f, 0.5f) // ≈ -60 dBFS
        val frames = run(concat(quiet, tone), DspConfig(thresholdDbfs = -50f))
        assertTrue(frames.none { it.anyActive })
        val frames2 = run(concat(quiet, tone), DspConfig(thresholdDbfs = -70f))
        assertTrue(frames2.any { it.active[1] })
    }

    @Test
    fun `tracker floor falls fast and rises slowly`() {
        val t = NoiseFloorTracker()
        t.update(-40f, frameSec)
        repeat(10) { t.update(-80f, frameSec) }
        assertTrue("floor should fall fast", t.floor < -75f)
        val start = t.floor
        repeat(62) { t.update(-20f, frameSec) } // ~1 s, level jumps but is not steady yet
        assertEquals(start + 1.5f, t.floor, 0.3f)
        assertFalse(t.isSteady)
    }

    private fun concat(a: FloatArray, b: FloatArray) = FloatArray(a.size + b.size).also {
        a.copyInto(it)
        b.copyInto(it, a.size)
    }
}
