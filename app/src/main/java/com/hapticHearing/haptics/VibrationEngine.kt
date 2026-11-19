package com.hapticHearing.haptics

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * The only class that talks to the phone's vibrator.
 *
 * Event patterns take precedence: while one plays, continuous updates are ignored, and continuous
 * vibration resumes on its own afterwards (spec §9.1). Every vibration uses the ALARM usage so it is
 * not silenced by "touch feedback" settings or Do Not Disturb.
 */
@Singleton
class VibrationEngine @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    val hasVibrator: Boolean = vibrator.hasVibrator()
    val hasAmplitudeControl: Boolean = vibrator.hasAmplitudeControl()

    @Volatile private var patternUntilMs = 0L
    @Volatile private var continuousOn = false

    /** Uptime (ms) until which the motor is known to be running; used to guard against mic feedback. */
    @Volatile var motorBusyUntilMs = 0L
        private set

    fun isMotorRunning(nowMs: Long = SystemClock.uptimeMillis()): Boolean = nowMs < motorBusyUntilMs + MOTOR_TAIL_MS

    /** Plays a short continuous-mode waveform (replaces the previous one). Ignored while a pattern plays. */
    fun playContinuous(waveform: Waveform) {
        val now = SystemClock.uptimeMillis()
        if (now < patternUntilMs || !hasVibrator) return
        vibrate(waveform.timings, waveform.amplitudes)
        continuousOn = true
        motorBusyUntilMs = now + waveform.totalMs
    }

    fun stopContinuous() {
        if (!continuousOn) return
        continuousOn = false
        if (SystemClock.uptimeMillis() < patternUntilMs) return
        vibrator.cancel()
    }

    /** Plays an event pattern, pausing continuous vibration while it runs. */
    fun playPattern(pattern: VibrationPattern, strength: Float = 1f) {
        if (!hasVibrator) return
        val now = SystemClock.uptimeMillis()
        val amps = IntArray(pattern.amplitudes.size) { i ->
            val a = pattern.amplitudes[i]
            if (a == 0) 0 else (a * strength.coerceIn(0.1f, 1f)).roundToInt().coerceIn(1, 255)
        }
        patternUntilMs = now + pattern.totalMs
        motorBusyUntilMs = patternUntilMs
        continuousOn = false
        vibrate(pattern.timings, amps)
    }

    fun cancelAll() {
        patternUntilMs = 0
        continuousOn = false
        vibrator.cancel()
    }

    private fun vibrate(timings: LongArray, amplitudes: IntArray) {
        if (timings.isEmpty() || timings.sum() <= 0) return
        val amps = if (hasAmplitudeControl) amplitudes else IntArray(amplitudes.size) { if (amplitudes[it] > 0) 255 else 0 }
        val effect = VibrationEffect.createWaveform(timings, amps, -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
        }
    }

    private companion object {
        /** The motor keeps ringing briefly after the waveform ends. */
        const val MOTOR_TAIL_MS = 40L
    }
}
