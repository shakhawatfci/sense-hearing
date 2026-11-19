package com.hapticHearing.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BandAnalyzerTest {
    private val analyzer = BandAnalyzer()

    @Test
    fun `default range gives log spaced bands`() {
        analyzer.setRange(100f, 8000f)
        val e = analyzer.bandEdgesHz
        assertEquals(100f, e[0], 0.01f)
        assertEquals(430.9f, e[1], 1f)
        assertEquals(1856.6f, e[2], 1f)
        assertEquals(8000f, e[3], 0.01f)
    }

    @Test
    fun `bands are contiguous and cover the range`() {
        for ((lo, hi) in listOf(100f to 8000f, 300f to 3400f, 50f to 500f, 2000f to 8000f)) {
            analyzer.setRange(lo, hi)
            for (b in 0 until 2) assertEquals(analyzer.binsOf(b).last + 1, analyzer.binsOf(b + 1).first)
            assertTrue(analyzer.binsOf(0).first * analyzer.binHz >= analyzer.minHz)
            assertTrue(analyzer.binsOf(2).last * analyzer.binHz <= analyzer.maxHz)
        }
    }

    @Test
    fun `full scale sine reads about 0 dBFS and scales with amplitude`() {
        analyzer.setRange(100f, 8000f)
        val bands = FloatArray(3)
        val full = analyzer.analyze(TestSignals.sine(1000f, 1f, 0.032f), bands)
        assertEquals(0f, full, 0.5f)
        val tenth = analyzer.analyze(TestSignals.sine(1000f, 0.1f, 0.032f), bands)
        assertEquals(-20f, tenth, 0.5f)
    }

    @Test
    fun `sine lands in the right band`() {
        analyzer.setRange(100f, 8000f)
        val bands = FloatArray(3)
        analyzer.analyze(TestSignals.sine(250f, 0.5f, 0.032f), bands)
        assertTrue(bands[0] > bands[1] + 20 && bands[0] > bands[2] + 20)
        analyzer.analyze(TestSignals.sine(1000f, 0.5f, 0.032f), bands)
        assertTrue(bands[1] > bands[0] + 20 && bands[1] > bands[2] + 20)
        analyzer.analyze(TestSignals.sine(4000f, 0.5f, 0.032f), bands)
        assertTrue(bands[2] > bands[0] + 20 && bands[2] > bands[1] + 20)
    }

    @Test
    fun `frequency filter removes out of range hum`() {
        analyzer.setRange(300f, 3400f) // "Voices only"
        val bands = FloatArray(3)
        val overall = analyzer.analyze(TestSignals.sine(100f, 0.5f, 0.032f), bands)
        assertTrue("100 Hz hum should be strongly attenuated, was $overall", overall < -40f)
    }

    @Test
    fun `silence is clamped`() {
        val bands = FloatArray(3)
        val overall = analyzer.analyze(FloatArray(512), bands)
        assertEquals(BandAnalyzer.SILENCE_DB, overall, 0f)
    }
}
