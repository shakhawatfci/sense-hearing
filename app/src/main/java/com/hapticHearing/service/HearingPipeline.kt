package com.hapticHearing.service

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import com.hapticHearing.audio.AudioEngine
import com.hapticHearing.audio.MicState
import com.hapticHearing.data.settings.AppSettings
import com.hapticHearing.data.settings.SettingsRepository
import com.hapticHearing.dsp.DspConfig
import com.hapticHearing.dsp.DspFrame
import com.hapticHearing.dsp.DspProcessor
import com.hapticHearing.haptics.ContinuousHaptics
import com.hapticHearing.haptics.ContinuousOutput
import com.hapticHearing.haptics.HapticConfig
import com.hapticHearing.haptics.HapticMapper
import com.hapticHearing.haptics.VibrationEngine
import com.hapticHearing.haptics.Waveform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class ListeningStatus { OFF, STARTING, LISTENING, PAUSED, MIC_BUSY, IN_CALL, NO_PERMISSION }

/** What the UI shows. dB values are approximate dB SPL (dBFS + calibration offset). */
data class LiveState(
    val status: ListeningStatus = ListeningStatus.OFF,
    val overallDb: Float = 0f,
    val bandDb: List<Float> = listOf(0f, 0f, 0f),
    val floorDb: List<Float> = listOf(0f, 0f, 0f),
    val active: List<Boolean> = listOf(false, false, false),
    val bandEdgesHz: List<Float> = listOf(100f, 431f, 1857f, 8000f),
    val minDb: Float = 50f,
    val vibrating: Boolean = false,
    val hasSignal: Boolean = false,
) {
    val isOn: Boolean get() = status != ListeningStatus.OFF
}

/**
 * Owns the real-time chain Mic → DSP → HapticMapper → motor. Lives as long as [HearingService]
 * runs it; the UI only observes [live].
 */
@Singleton
class HearingPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audio: AudioEngine,
    private val vibration: VibrationEngine,
    private val settingsRepository: SettingsRepository,
) {
    private val dsp = DspProcessor()
    private val mapper = HapticMapper()
    private val continuous = ContinuousHaptics(object : ContinuousOutput {
        override val hasAmplitudeControl get() = vibration.hasAmplitudeControl
        override fun play(waveform: Waveform) = vibration.playContinuous(waveform)
        override fun stop() = vibration.stopContinuous()
    })

    private val _live = MutableStateFlow(LiveState())
    val live: StateFlow<LiveState> = _live.asStateFlow()

    private val paused = MutableStateFlow(false)
    private val inCall = MutableStateFlow(false)

    @Volatile private var settings = AppSettings()
    @Volatile private var hapticConfig = HapticConfig()
    private var job: Job? = null
    private var lastUiNanos = 0L

    val isRunning: Boolean get() = job?.isActive == true

    fun start(scope: CoroutineScope) {
        if (isRunning) return
        _live.value = LiveState(status = ListeningStatus.STARTING)
        job = scope.launch(Dispatchers.Default) {
            applySettings(settingsRepository.settings.first())
            launch { settingsRepository.settings.collect { applySettings(it) } }
            launch { monitorCalls() }
            launch {
                combine(audio.state, paused, inCall) { mic, p, call -> statusOf(mic, p, call) }
                    .collect { status -> _live.update { it.copy(status = status) } }
            }
            launch {
                combine(paused, inCall) { p, call -> !p && !call }.collect { shouldRun ->
                    if (shouldRun) {
                        audio.start()
                    } else {
                        audio.stop()
                        resetOutput()
                    }
                }
            }
            audio.chunks
                .onSubscription { dsp.reset() }
                .collect { chunk -> dsp.process(chunk) { onFrame(it) } }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        audio.stop()
        resetOutput()
        paused.value = false
        _live.value = LiveState()
    }

    fun setPaused(value: Boolean) {
        paused.value = value
    }

    val isPaused: Boolean get() = paused.value

    private fun resetOutput() {
        continuous.reset()
        mapper.reset()
        dsp.reset()
        vibration.cancelAll()
        _live.update { it.copy(vibrating = false, active = listOf(false, false, false), hasSignal = false) }
    }

    private fun applySettings(s: AppSettings) {
        settings = s
        dsp.config = DspConfig(
            minHz = s.minHz,
            maxHz = s.maxHz,
            thresholdDbfs = s.thresholdDbfs,
            noiseMarginDb = s.noiseMarginDb,
        )
        hapticConfig = HapticConfig(
            userStrength = s.vibrationStrength,
            continuousEnabled = s.continuousEnabled && !s.quietMode,
        )
    }

    private fun onFrame(frame: DspFrame) {
        val now = SystemClock.uptimeMillis()
        val haptic = mapper.map(frame, hapticConfig)
        continuous.onFrame(haptic, now)
        dsp.selfVibrationGuard = vibration.isMotorRunning(now)

        if (frame.timeNanos - lastUiNanos >= UI_INTERVAL_NANOS) {
            lastUiNanos = frame.timeNanos
            val offset = settings.calibrationOffset
            _live.update { st ->
                st.copy(
                    overallDb = frame.overallDbfs + offset,
                    bandDb = frame.bandDbfs.map { it + offset },
                    floorDb = frame.floorDbfs.map { it + offset },
                    active = frame.active.toList(),
                    bandEdgesHz = frame.bandEdgesHz.toList(),
                    minDb = settings.minDb,
                    vibrating = haptic.isOn,
                    hasSignal = true,
                )
            }
        }
    }

    /** Releases the mic during phone/VoIP calls (spec §12). Polling avoids needing READ_PHONE_STATE. */
    private suspend fun monitorCalls() {
        val am = context.getSystemService(AudioManager::class.java)
        while (kotlin.coroutines.coroutineContext.isActive) {
            val mode = am.mode
            inCall.value = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
                mode == AudioManager.MODE_RINGTONE
            delay(1000)
        }
    }

    private fun statusOf(mic: MicState, paused: Boolean, call: Boolean): ListeningStatus = when {
        paused -> ListeningStatus.PAUSED
        call -> ListeningStatus.IN_CALL
        else -> when (mic) {
            MicState.STOPPED, MicState.STARTING -> ListeningStatus.STARTING
            MicState.RUNNING -> ListeningStatus.LISTENING
            MicState.BUSY -> ListeningStatus.MIC_BUSY
            MicState.NO_PERMISSION -> ListeningStatus.NO_PERMISSION
        }
    }

    private companion object {
        const val UI_INTERVAL_NANOS = 33_000_000L
    }
}
