package com.hapticHearing.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place iterative radix-2 complex FFT (forward transform, no scaling). */
class Fft(val n: Int) {
    init {
        require(n >= 2 && (n and (n - 1)) == 0) { "FFT size must be a power of two" }
    }

    private val bits = Integer.numberOfTrailingZeros(n)
    private val cosTable = FloatArray(n / 2) { cos(2.0 * PI * it / n).toFloat() }
    private val sinTable = FloatArray(n / 2) { sin(2.0 * PI * it / n).toFloat() }
    private val reversed = IntArray(n) { Integer.reverse(it) ushr (32 - bits) }

    fun transform(re: FloatArray, im: FloatArray) {
        require(re.size >= n && im.size >= n)
        for (i in 0 until n) {
            val j = reversed[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var start = 0
            while (start < n) {
                var k = 0
                for (j in start until start + half) {
                    val l = j + half
                    val c = cosTable[k]
                    val s = sinTable[k]
                    // multiply by e^{-i·2πk/size} = cos − i·sin
                    val tRe = re[l] * c + im[l] * s
                    val tIm = im[l] * c - re[l] * s
                    re[l] = re[j] - tRe
                    im[l] = im[j] - tIm
                    re[j] += tRe
                    im[j] += tIm
                    k += step
                }
                start += size
            }
            size *= 2
        }
    }
}

object Window {
    /** Periodic Hann window (correct for overlapping STFT frames). */
    fun hann(n: Int): FloatArray = FloatArray(n) { (0.5 - 0.5 * cos(2.0 * PI * it / n)).toFloat() }
}
