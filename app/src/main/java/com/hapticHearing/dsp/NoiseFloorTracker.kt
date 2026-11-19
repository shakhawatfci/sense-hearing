package com.hapticHearing.dsp

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Adaptive background-noise floor for one band (spec §5.2).
 *
 * - Level below the floor: the floor falls fast (`F += 0.2·(L − F)` per frame).
 * - Level above the floor: the floor rises slowly (1.5 dB/s), so steady noise is absorbed.
 * - Steady-hum detector: the level is smoothed (100 ms), sampled every 100 ms, and if its standard
 *   deviation over the last 3 s is below 1.5 dB the band is "steady" and the floor jumps up to just
 *   under that level. This absorbs a fan/AC within ~3 s instead of waiting for the slow rise, while
 *   a clap or a voice on top of the hum is still far above the floor and stays active.
 */
class NoiseFloorTracker(
    private val riseDbPerSec: Float = 1.5f,
    private val fallCoef: Float = 0.2f,
    steadyWindowSec: Float = 3f,
    private val steadyStdDb: Float = 1.5f,
    private val slotSec: Float = 0.1f,
    private val smoothTauSec: Float = 0.1f,
    private val steadyHeadroomDb: Float = 1f,
) {
    var floor: Float = Float.NaN; private set
    var isSteady: Boolean = false; private set

    private var smoothed = Float.NaN
    private val slots = FloatArray((steadyWindowSec / slotSec).toInt().coerceAtLeast(2))
    private var slotCount = 0
    private var slotIndex = 0
    private var slotTimer = 0f

    fun reset() {
        floor = Float.NaN
        smoothed = Float.NaN
        isSteady = false
        slotCount = 0
        slotIndex = 0
        slotTimer = 0f
    }

    /** Feeds one frame's band level (dBFS) with frame duration [dtSec]; returns the new floor. */
    fun update(level: Float, dtSec: Float): Float {
        if (floor.isNaN()) {
            floor = level
            smoothed = level
        } else {
            floor = if (level < floor) {
                floor + fallCoef * (level - floor)
            } else {
                min(level, floor + riseDbPerSec * dtSec)
            }
            smoothed += (1f - exp(-dtSec / smoothTauSec)) * (level - smoothed)
        }

        slotTimer += dtSec
        if (slotTimer >= slotSec) {
            slotTimer -= slotSec
            slots[slotIndex] = smoothed
            slotIndex = (slotIndex + 1) % slots.size
            if (slotCount < slots.size) slotCount++
            isSteady = slotCount == slots.size && stdDev() < steadyStdDb
        }
        if (isSteady) floor = max(floor, mean() - steadyHeadroomDb)
        return floor
    }

    private fun mean(): Float {
        var s = 0.0
        for (i in 0 until slotCount) s += slots[i]
        return (s / slotCount).toFloat()
    }

    private fun stdDev(): Float {
        val m = mean()
        var s = 0.0
        for (i in 0 until slotCount) {
            val d = slots[i] - m
            s += d * d
        }
        return sqrt(s / slotCount).toFloat()
    }
}
