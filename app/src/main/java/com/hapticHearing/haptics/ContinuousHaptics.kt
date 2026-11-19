package com.hapticHearing.haptics

/** Abstraction over the motor so the driver logic can be unit-tested. */
interface ContinuousOutput {
    val hasAmplitudeControl: Boolean
    fun play(waveform: Waveform)
    fun stop()
}

/**
 * Turns the per-frame [HapticFrame] stream (every 16 ms) into vibrator calls.
 *
 * - Onset (off → on) or a change of dominant band: sends a waveform immediately (low latency).
 * - While on: refreshes every [refreshMs] with a waveform slightly longer than the refresh period,
 *   generated from the running rhythm phase, so the motor never gaps and the rhythm stays even.
 * - Off: cancels once.
 */
class ContinuousHaptics(
    private val output: ContinuousOutput,
    private val refreshMs: Long = 50,
    private val waveformMs: Long = 80,
) {
    private var on = false
    private var dominant = -1
    private var lastSendMs = Long.MIN_VALUE
    private var phaseStartMs = 0L

    fun onFrame(frame: HapticFrame, nowMs: Long) {
        if (!frame.isOn) {
            if (on) output.stop()
            on = false
            dominant = -1
            return
        }
        val changed = !on || frame.dominantBand != dominant
        if (!changed && nowMs - lastSendMs < refreshMs) return
        if (changed) phaseStartMs = nowMs
        val band = frame.dominantBand
        val waveform = RhythmGenerator.build(
            band = band,
            drive = frame.bands[band],
            phaseMs = nowMs - phaseStartMs,
            durationMs = waveformMs,
            hasAmplitudeControl = output.hasAmplitudeControl,
        )
        output.play(waveform)
        on = true
        dominant = band
        lastSendMs = nowMs
    }

    fun reset() {
        if (on) output.stop()
        on = false
        dominant = -1
    }
}
