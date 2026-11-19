package com.hapticHearing.haptics

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** A waveform ready for `VibrationEffect.createWaveform(timings, amplitudes, -1)`. */
class Waveform(val timings: LongArray, val amplitudes: IntArray) {
    val totalMs: Long get() = timings.sum()
}

/**
 * Pitch → rhythm (spec §5.3): low ≈4 Hz (120/130 ms), mid ≈8 Hz (60/65 ms), high = fast buzz (30/30 ms)
 * at reduced amplitude. Waveforms are generated from a running phase so the rhythm stays continuous
 * across the ~50 ms refreshes.
 */
object RhythmGenerator {
    private val ON_MS = longArrayOf(120, 60, 30)
    private val OFF_MS = longArrayOf(130, 65, 30)
    private val BAND_GAIN = floatArrayOf(1f, 1f, 0.7f)
    private const val MIN_SEGMENT_MS = 10L

    fun periodMs(band: Int): Long = ON_MS[band] + OFF_MS[band]

    /**
     * @param drive 0..1 drive level
     * @param phaseMs time since this rhythm started (0 = start of an "on" pulse)
     * @param hasAmplitudeControl false → strength is expressed with the duty cycle instead
     */
    fun build(band: Int, drive: Float, phaseMs: Long, durationMs: Long, hasAmplitudeControl: Boolean): Waveform {
        val period = periodMs(band)
        val d = drive.coerceIn(0f, 1f)
        val on: Long
        val amplitude: Int
        if (hasAmplitudeControl) {
            on = ON_MS[band]
            amplitude = (d * BAND_GAIN[band] * 255f).roundToInt().coerceIn(1, 255)
        } else {
            on = (ON_MS[band] * (0.35f + 0.65f * d)).roundToLong().coerceIn(MIN_SEGMENT_MS, period - MIN_SEGMENT_MS)
            amplitude = 255
        }
        val timings = ArrayList<Long>(8)
        val amps = ArrayList<Int>(8)
        var t = Math.floorMod(phaseMs, period)
        var remaining = durationMs
        while (remaining > 0) {
            val seg: Long
            val a: Int
            if (t < on) {
                seg = minOf(on - t, remaining)
                a = amplitude
            } else {
                seg = minOf(period - t, remaining)
                a = 0
            }
            timings += seg
            amps += a
            remaining -= seg
            t = (t + seg) % period
        }
        return Waveform(timings.toLongArray(), amps.toIntArray())
    }
}
