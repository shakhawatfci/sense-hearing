package com.hapticHearing.dsp

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Hann window → FFT → one-sided power spectrum → per-band levels in dBFS.
 *
 * Power is normalised so a full-scale sine (amplitude 1.0) reads 0 dBFS and the sum over all bins
 * equals 2 × the signal's mean square. Only bins inside [minHz, maxHz] count; that range is split
 * into [numBands] log-spaced bands.
 */
class BandAnalyzer(
    val sampleRate: Int = 16_000,
    val fftSize: Int = 512,
    val numBands: Int = 3,
) {
    val binHz: Float = sampleRate.toFloat() / fftSize
    private val nyquist = sampleRate / 2f
    private val fft = Fft(fftSize)
    private val window = Window.hann(fftSize)
    private val re = FloatArray(fftSize)
    private val im = FloatArray(fftSize)
    val power = FloatArray(fftSize / 2 + 1)
    private val interiorScale: Float
    private val edgeScale: Float

    var minHz: Float = 100f; private set
    var maxHz: Float = 8000f; private set

    /** numBands + 1 edges, in Hz. */
    var bandEdgesHz: FloatArray = FloatArray(numBands + 1); private set
    private var bandFirstBin = IntArray(numBands)
    private var bandLastBin = IntArray(numBands)

    init {
        var sumW2 = 0.0
        for (w in window) sumW2 += w * w
        // P_k = 4|X_k|² / (N·Σw²) for interior bins; DC and Nyquist appear once, so half that.
        interiorScale = (4.0 / (fftSize * sumW2)).toFloat()
        edgeScale = interiorScale / 2f
        setRange(100f, 8000f)
    }

    fun setRange(minHz: Float, maxHz: Float) {
        val lo = minHz.coerceIn(binHz, nyquist - binHz)
        val hi = maxHz.coerceIn(lo + binHz, nyquist)
        this.minHz = lo
        this.maxHz = hi
        val edges = FloatArray(numBands + 1) { i -> (lo * (hi / lo).pow(i.toFloat() / numBands)) }
        edges[0] = lo
        edges[numBands] = hi
        bandEdgesHz = edges
        val first = IntArray(numBands)
        val last = IntArray(numBands)
        for (b in 0 until numBands) {
            first[b] = ceil(edges[b] / binHz).toInt()
            // Upper edge exclusive, except the top band which includes maxHz.
            val upper = edges[b + 1] / binHz
            last[b] = if (b == numBands - 1) floor(upper).toInt() else ceil(upper).toInt() - 1
            last[b] = last[b].coerceAtMost(fftSize / 2)
        }
        bandFirstBin = first
        bandLastBin = last
    }

    /** Bin index range covered by band [b] (may be empty for very narrow ranges). */
    fun binsOf(b: Int): IntRange = bandFirstBin[b]..bandLastBin[b]

    /**
     * Analyses one [fftSize]-sample frame. Writes per-band dBFS into [outBandDbfs] and returns the
     * overall dBFS of everything inside the frequency range.
     */
    fun analyze(frame: FloatArray, outBandDbfs: FloatArray): Float {
        for (i in 0 until fftSize) {
            re[i] = frame[i] * window[i]
            im[i] = 0f
        }
        fft.transform(re, im)
        val half = fftSize / 2
        for (k in 0..half) {
            val p = re[k] * re[k] + im[k] * im[k]
            power[k] = p * if (k == 0 || k == half) edgeScale else interiorScale
        }
        var total = 0.0
        for (b in 0 until numBands) {
            var sum = 0.0
            for (k in bandFirstBin[b]..bandLastBin[b]) sum += power[k]
            total += sum
            outBandDbfs[b] = toDb(sum)
        }
        return toDb(total)
    }

    companion object {
        const val SILENCE_DB = -120f
        fun toDb(power: Double): Float = (10.0 * log10(power + 1e-12)).toFloat().coerceAtLeast(SILENCE_DB)
    }
}
