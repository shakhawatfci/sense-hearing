package com.hapticHearing.haptics

import com.hapticHearing.dsp.DspFrame
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/**
 * Output for any haptic device. 6 channels = low/mid/high × front/back (spec §5.4); the phone motor
 * uses the dominant band, the BLE wristband (M8) will use all six.
 * Channel values are drive levels 0..1 (already scaled by the user's strength).
 */
class HapticFrame(
    val bands: FloatArray,
    val dominantBand: Int,
    val eventId: Int = 0,
) {
    val isOn: Boolean get() = dominantBand >= 0

    companion object {
        const val CHANNELS = 6
        val OFF = HapticFrame(FloatArray(CHANNELS), -1)
    }
}

data class HapticConfig(
    val userStrength: Float = 1f,
    val continuousEnabled: Boolean = true,
)

/** Maps band levels to vibration drive levels with attack/release smoothing (spec §5.3). */
class HapticMapper(
    frameMs: Float = 16f,
    attackMs: Float = 10f,
    releaseMs: Float = 120f,
    private val numBands: Int = 3,
) {
    private val attackCoef = 1f - exp(-frameMs / attackMs)
    private val releaseCoef = 1f - exp(-frameMs / releaseMs)
    private val smoothed = FloatArray(numBands)

    fun reset() = smoothed.fill(0f)

    fun map(frame: DspFrame, cfg: HapticConfig): HapticFrame {
        val enabled = cfg.continuousEnabled && cfg.userStrength > 0f
        var dominant = -1
        var best = 0f
        for (b in 0 until numBands) {
            val target = if (enabled && frame.active[b]) {
                driveLevel(frame.bandDbfs[b], frame.floorDbfs[b], frame.noiseMarginDb, frame.thresholdDbfs) * cfg.userStrength
            } else {
                0f
            }
            val coef = if (target > smoothed[b]) attackCoef else releaseCoef
            smoothed[b] += coef * (target - smoothed[b])
            if (target == 0f && smoothed[b] < OFF_LEVEL) smoothed[b] = 0f
            if (smoothed[b] > best) {
                best = smoothed[b]
                dominant = b
            }
        }
        val out = FloatArray(HapticFrame.CHANNELS)
        for (b in 0 until numBands) {
            out[b] = smoothed[b] // front
            out[b + numBands] = smoothed[b] // back (phone has no direction yet)
        }
        return HapticFrame(out, dominant)
    }

    companion object {
        const val OFF_LEVEL = 0.005f

        /** amplitude = 20 + 235·strength^0.7 on a 0..255 scale, returned as 0..1. */
        fun driveLevel(level: Float, floor: Float, margin: Float, threshold: Float): Float {
            val excess = level - max(floor + margin, threshold)
            val strength = (excess / 30f).coerceIn(0f, 1f)
            return (20f + 235f * strength.pow(0.7f)) / 255f
        }
    }
}
