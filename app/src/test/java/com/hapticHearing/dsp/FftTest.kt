package com.hapticHearing.dsp

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class FftTest {
    @Test
    fun `1 kHz sine peaks in bin 32`() {
        val n = 512
        val x = TestSignals.sine(1000f, 1f, n / 16000f)
        val re = x.copyOf(n)
        val im = FloatArray(n)
        Fft(n).transform(re, im)
        val mags = FloatArray(n / 2) { hypot(re[it], im[it]) }
        val peak = mags.indices.maxBy { mags[it] }
        assertEquals(32, peak) // 1000 Hz / 31.25 Hz per bin
        assertEquals(n / 2f, mags[32], 1e-2f) // amplitude·N/2 for an exactly periodic sine
    }

    @Test
    fun `impulse has flat spectrum`() {
        val n = 64
        val re = FloatArray(n).also { it[0] = 1f }
        val im = FloatArray(n)
        Fft(n).transform(re, im)
        for (k in 0 until n) {
            assertEquals(1f, re[k], 1e-5f)
            assertEquals(0f, im[k], 1e-5f)
        }
    }

    @Test
    fun `matches naive DFT`() {
        val n = 32
        val r = java.util.Random(1)
        val x = FloatArray(n) { r.nextFloat() - 0.5f }
        val re = x.copyOf()
        val im = FloatArray(n)
        Fft(n).transform(re, im)
        for (k in 0 until n) {
            var sr = 0.0
            var si = 0.0
            for (j in 0 until n) {
                val a = -2 * Math.PI * j * k / n
                sr += x[j] * kotlin.math.cos(a)
                si += x[j] * kotlin.math.sin(a)
            }
            assert(abs(sr - re[k]) < 1e-4) { "re[$k]" }
            assert(abs(si - im[k]) < 1e-4) { "im[$k]" }
        }
    }
}
