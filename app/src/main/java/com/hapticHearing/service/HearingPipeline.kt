package com.hapticHearing.service

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import com.hapticHearing.audio.AudioEngine
import com.hapticHearing.audio.MicState
import com.hapticHearing.data.db.EventLog
import com.hapticHearing.data.db.EventLogDao
import com.hapticHearing.events.ArbiterEvent
import com.hapticHearing.events.EventArbiter
import com.hapticHearing.events.EventPriority
import com.hapticHearing.haptics.VibrationPatterns
import com.hapticHearing.ml.SoundAlert
import com.hapticHearing.ml.SoundCategoryTable
import com.hapticHearing.ml.SoundDetection
import com.hapticHearing.ml.SoundEventDetector
import com.hapticHearing.ml.SoundModelFactory
import com.hapticHearing.ml.SoundPriority
import android.util.Log
import com.hapticHearing.data.settings.AppSettings
import com.hapticHearing.data.settings.SettingsRepository
import com.hapticHearing.dsp.DspConfig
import com.hapticHearing.dsp.DspFrame
import com.hapticHearing.dsp.DspProcessor
import com.hapticHearing.haptics.ContinuousHaptics
import com.hapticHearing.haptics.ContinuousOutput
import com.hapticHearing.haptics.HapticConfig
import com.hapticHearing.haptics.HapticMapper
import com.hapticHearing.haptics.NaturalHaptics
import com.hapticHearing.haptics.VibrationEngine
import com.hapticHearing.haptics.Waveform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
    /** Latest recognised sound (voice, vehicle, bird, doorbell…), null when nothing for 8 s. */
    val nowHearing: SoundDetection? = null,
    /** False if the sound model could not be loaded on this device. */
    val recognitionAvailable: Boolean = true,
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
    private val soundModelFactory: SoundModelFactory,
    private val eventLogDao: EventLogDao,
) {
    private val dsp = DspProcessor()
    private val mapper = HapticMapper()
    private val motorOutput = object : ContinuousOutput {
        override val hasAmplitudeControl get() = vibration.hasAmplitudeControl
        override fun play(waveform: Waveform) = vibration.playContinuous(waveform)
        override fun stop() = vibration.stopContinuous()
    }
    private val continuous = ContinuousHaptics(motorOutput)

    /** Natural feel: fast attack, short release so gaps between syllables come through. */
    private val naturalMapper = HapticMapper(attackMs = 5f, releaseMs = 40f)
    private val natural = NaturalHaptics(motorOutput)
    @Volatile private var naturalFeel = false

    private val arbiter = EventArbiter()
    private val table: SoundCategoryTable by lazy { soundModelFactory.table() }
    private val detector by lazy { SoundEventDetector(table) }

    /** Loudest overall level (dBFS) since the last classification window. */
    @Volatile private var windowMaxDbfs = -120f

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
            launch { classifyLoop(this) }
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
        natural.reset()
        naturalMapper.reset()
        arbiter.reset()
        detector.reset()
        mapper.reset()
        dsp.reset()
        vibration.cancelAll()
        _live.update { it.copy(vibrating = false, active = listOf(false, false, false), hasSignal = false, nowHearing = null) }
    }

    private fun applySettings(s: AppSettings) {
        settings = s
        dsp.config = DspConfig(
            minHz = s.minHz,
            maxHz = s.maxHz,
            thresholdDbfs = s.thresholdDbfs,
            noiseMarginDb = s.noiseMarginDb,
        )
        if (s.naturalFeel != naturalFeel) {
            // Switching modes: let the other driver release the motor.
            continuous.reset()
            natural.reset()
            naturalFeel = s.naturalFeel
        }
        hapticConfig = HapticConfig(
            userStrength = s.vibrationStrength,
            continuousEnabled = s.continuousEnabled && !s.quietMode,
        )
    }

    private fun onFrame(frame: DspFrame) {
        val now = SystemClock.uptimeMillis()
        val haptic = if (naturalFeel) naturalMapper.map(frame, hapticConfig) else mapper.map(frame, hapticConfig)
        if (frame.overallDbfs > windowMaxDbfs) windowMaxDbfs = frame.overallDbfs
        arbiter.poll(now)?.let { vibration.playPattern(it.pattern, settings.vibrationStrength.coerceAtLeast(0.3f)) }
        if (naturalFeel) natural.onFrame(haptic, now) else continuous.onFrame(haptic, now)
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

    /** YAMNet every 0.5 s (1 s in battery saver) on the last 0.975 s of audio (spec §6). */
    private suspend fun classifyLoop(scope: CoroutineScope) {
        val model = try {
            soundModelFactory.create().also { table }
        } catch (e: Throwable) {
            Log.e(TAG, "Sound model unavailable", e)
            _live.update { it.copy(recognitionAvailable = false) }
            return
        }
        try {
            while (scope.isActive) {
                delay(if (settings.batterySaver) 1000 else 500)
                if (audio.state.value != MicState.RUNNING) continue
                if (audio.ring.totalWritten < model.windowSamples) continue
                val samples = audio.ring.readLast(model.windowSamples)
                val levelPassed = windowMaxDbfs >= settings.thresholdDbfs
                windowMaxDbfs = -120f
                val labels = model.classify(samples)
                val now = SystemClock.uptimeMillis()
                val out = detector.onWindow(table.score(labels), levelPassed, now)
                _live.update { it.copy(nowHearing = out.display) }
                out.alerts.forEach { alert(it, now, scope) }
            }
        } finally {
            withContext(NonCancellable) { model.close() }
        }
    }

    private fun alert(a: SoundAlert, now: Long, scope: CoroutineScope) {
        val s = settings
        if (s.quietMode && a.priority != SoundPriority.DANGER) return
        val priority = when (a.priority) {
            SoundPriority.DANGER -> EventPriority.DANGER
            SoundPriority.HIGH -> EventPriority.HIGH
            else -> EventPriority.NORMAL
        }
        val pattern = VibrationPatterns.byId(a.patternId) ?: VibrationPatterns.GENERAL
        arbiter.submit(ArbiterEvent(a.categoryId, priority, pattern), now)?.let {
            vibration.playPattern(it.pattern, s.vibrationStrength.coerceAtLeast(0.3f))
        }
        val levelDb = _live.value.overallDb
        scope.launch(Dispatchers.IO) {
            runCatching {
                eventLogDao.insert(
                    EventLog(
                        time = System.currentTimeMillis(),
                        type = "SOUND",
                        label = a.categoryId,
                        personId = null,
                        confidence = a.confidence,
                        levelDb = levelDb,
                    ),
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
        const val TAG = "HearingPipeline"
        const val UI_INTERVAL_NANOS = 33_000_000L
    }
}
