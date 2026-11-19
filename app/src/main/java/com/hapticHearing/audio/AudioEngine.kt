package com.hapticHearing.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import android.media.AudioFormat as PlatformAudioFormat

enum class MicState { STOPPED, STARTING, RUNNING, BUSY, NO_PERMISSION }

/**
 * Captures 16 kHz mono audio on a dedicated high-priority thread, writes it into [ring] and emits
 * 256-sample chunks on [chunks]. If the mic is lost (another app or a call takes it) it reports
 * [MicState.BUSY] and retries every 2 s.
 */
@Singleton
class AudioEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val ring = RingBuffer(AudioFormat.SAMPLE_RATE * AudioFormat.RING_SECONDS)

    private val _chunks = MutableSharedFlow<FloatArray>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val chunks: SharedFlow<FloatArray> = _chunks.asSharedFlow()

    private val _state = MutableStateFlow(MicState.STOPPED)
    val state: StateFlow<MicState> = _state.asStateFlow()

    private val audioManager = context.getSystemService(AudioManager::class.java)

    @Volatile private var running = false
    @Volatile private var sessionId = -1
    private var thread: Thread? = null

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !running) return
            val mine = configs.firstOrNull { it.clientAudioSessionId == sessionId } ?: return
            // Another app took the mic: Android keeps us "recording" but feeds silence.
            _state.value = if (mine.isClientSilenced) MicState.BUSY else MicState.RUNNING
        }
    }

    @Synchronized
    fun start() {
        if (running) return
        running = true
        _state.value = MicState.STARTING
        audioManager.registerAudioRecordingCallback(recordingCallback, Handler(Looper.getMainLooper()))
        thread = Thread(::captureLoop, "AudioEngine").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        audioManager.unregisterAudioRecordingCallback(recordingCallback)
        thread?.interrupt()
        thread?.join(1000)
        thread = null
        _state.value = MicState.STOPPED
    }

    private fun captureLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        while (running) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                _state.value = MicState.NO_PERMISSION
                if (!sleepRetry()) break
                continue
            }
            val record = createRecord()
            if (record == null) {
                _state.value = MicState.BUSY
                if (!sleepRetry()) break
                continue
            }
            try {
                record.startRecording()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    _state.value = MicState.BUSY
                } else {
                    sessionId = record.audioSessionId
                    _state.value = MicState.RUNNING
                    readLoop(record)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Recording failed", e)
                _state.value = MicState.BUSY
            } finally {
                runCatching { record.stop() }
                record.release()
                sessionId = -1
            }
            if (running && !sleepRetry()) break
        }
    }

    private fun readLoop(record: AudioRecord) {
        val shorts = ShortArray(AudioFormat.CHUNK_SAMPLES)
        while (running) {
            val n = record.read(shorts, 0, shorts.size, AudioRecord.READ_BLOCKING)
            if (n <= 0) {
                Log.w(TAG, "AudioRecord.read returned $n")
                _state.value = MicState.BUSY
                return
            }
            val chunk = FloatArray(n) { shorts[it] / 32768f }
            ring.write(chunk, n)
            _chunks.tryEmit(chunk)
        }
    }

    @SuppressLint("MissingPermission") // checked in captureLoop
    private fun createRecord(): AudioRecord? {
        val unprocessed = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val source = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
        val minBuf = AudioRecord.getMinBufferSize(
            AudioFormat.SAMPLE_RATE,
            PlatformAudioFormat.CHANNEL_IN_MONO,
            PlatformAudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return null
        val bufferBytes = maxOf(minBuf, AudioFormat.CHUNK_SAMPLES * 2 * 8)
        return try {
            val r = AudioRecord(
                source,
                AudioFormat.SAMPLE_RATE,
                PlatformAudioFormat.CHANNEL_IN_MONO,
                PlatformAudioFormat.ENCODING_PCM_16BIT,
                bufferBytes,
            )
            if (r.state != AudioRecord.STATE_INITIALIZED) {
                r.release()
                null
            } else {
                r
            }
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord creation failed", e)
            null
        }
    }

    /** Waits 2 s before retrying. Returns false if the engine was stopped meanwhile. */
    private fun sleepRetry(): Boolean = try {
        Thread.sleep(RETRY_MS)
        running
    } catch (_: InterruptedException) {
        false
    }

    private companion object {
        const val TAG = "AudioEngine"
        const val RETRY_MS = 2000L
    }
}
