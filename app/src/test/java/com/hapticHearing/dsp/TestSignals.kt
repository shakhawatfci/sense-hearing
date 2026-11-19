package com.hapticHearing.dsp

import java.util.Random
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

object TestSignals {
    const val SR = 16_000

    fun sine(freq: Float, amplitude: Float, seconds: Float, sr: Int = SR): FloatArray {
        val n = (seconds * sr).toInt()
        return FloatArray(n) { (amplitude * sin(2.0 * PI * freq * it / sr)).toFloat() }
    }

    fun whiteNoise(rms: Float, seconds: Float, seed: Long = 42, sr: Int = SR): FloatArray {
        val r = Random(seed)
        val n = (seconds * sr).toInt()
        return FloatArray(n) { (r.nextGaussian() * rms).toFloat() }
    }

    fun dbToAmp(db: Float): Float = 10f.pow(db / 20f)

    operator fun FloatArray.plus(other: FloatArray): FloatArray {
        val n = maxOf(size, other.size)
        return FloatArray(n) { (getOrElse(it) { 0f }) + other.getOrElse(it) { 0f } }
    }
}
