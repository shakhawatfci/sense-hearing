package com.hapticHearing.haptics

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * "Natural feel" mode: instead of a fixed pulse rhythm per pitch band, the motor strength follows the
 * sound's own loudness envelope (~30 ms resolution), so syllables, words, knocks and beats are felt as
 * they happen. A sharp rise in level (an onset) starts with a short full-strength tap so it feels crisp.
 *
 * Feed it frames from a [HapticMapper] with a fast release (~40 ms) so gaps between syllables come
 * through. Without amplitude control the strength becomes a 20 ms PWM duty cycle.
 */
class NaturalHaptics(
    private val output: ContinuousOutput,
    private val refreshMs: Long = 32,
    private val waveformMs: Long = 48,
    private val changeThreshold: Float = 0.12f,
    private val onsetRise: Float = 0.25f,
    private val tapMs: Long = 20,
) {
    private var on = false
    private var lastSentDrive = 0f
    private var prevDrive = 0f
    private var lastSendMs = Long.MIN_VALUE

    fun onFrame(frame: HapticFrame, nowMs: Long) {
        val drive = if (frame.isOn) frame.bands.take(3).max() else 0f
        val rise = drive - prevDrive
        prevDrive = drive
        if (drive <= 0f) {
            if (on) output.stop()
            on = false
            lastSentDrive = 0f
            return
        }
        val onset = rise >= onsetRise
        val changed = !on || abs(drive - lastSentDrive) >= changeThreshold
        if (!onset && !changed && nowMs - lastSendMs < refreshMs) return
        output.play(build(drive, onset, output.hasAmplitudeControl))
        on = true
        lastSentDrive = drive
        lastSendMs = nowMs
    }

    fun reset() {
        if (on) output.stop()
        on = false
        prevDrive = 0f
        lastSentDrive = 0f
    }

    internal fun build(drive: Float, onset: Boolean, hasAmplitudeControl: Boolean): Waveform {
        val d = drive.coerceIn(0f, 1f)
        if (hasAmplitudeControl) {
            val amp = (d * 255f).roundToInt().coerceIn(1, 255)
            return if (onset) {
                val tapAmp = (max(d * 1.6f, 0.6f).coerceAtMost(1f) * 255f).roundToInt()
                Waveform(longArrayOf(tapMs, waveformMs - tapMs), intArrayOf(tapAmp, amp))
            } else {
                Waveform(longArrayOf(waveformMs), intArrayOf(amp))
            }
        }
        // No amplitude control: 20 ms PWM, longer "on" = stronger. Onsets get a solid first pulse.
        val period = PWM_PERIOD_MS
        val onMs = (period * (0.2f + 0.8f * d)).roundToLong().coerceIn(4, period)
        val timings = ArrayList<Long>()
        val amps = ArrayList<Int>()
        var remaining = waveformMs
        if (onset) {
            timings += tapMs; amps += 255; remaining -= tapMs
        }
        while (remaining > 0) {
            val a = minOf(onMs, remaining)
            timings += a; amps += 255; remaining -= a
            if (remaining <= 0) break
            val b = minOf(period - onMs, remaining)
            if (b > 0) { timings += b; amps += 0; remaining -= b }
        }
        return Waveform(timings.toLongArray(), amps.toIntArray())
    }

    private companion object {
        const val PWM_PERIOD_MS = 20L
    }
}
