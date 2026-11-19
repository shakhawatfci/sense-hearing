package com.hapticHearing.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.hapticHearing.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service of type `microphone` (spec §12). Must be started while the app is visible
 * (Android 14+ rule), see [ServiceController].
 */
@AndroidEntryPoint
class HearingService : Service() {
    @Inject lateinit var pipeline: HearingPipeline
    @Inject lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var inForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch { settingsRepository.update { it.copy(wasListening = false) } }
                shutdown()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                if (!ensureForeground(paused = true)) return START_NOT_STICKY
                pipeline.setPaused(true)
                releaseWakeLock()
                refreshNotification()
            }
            ACTION_RESUME -> {
                if (!ensureForeground(paused = false)) return START_NOT_STICKY
                pipeline.setPaused(false)
                acquireWakeLock()
                refreshNotification()
            }
            else -> {
                if (!ensureForeground(paused = false)) return START_NOT_STICKY
                pipeline.setPaused(false)
                pipeline.start(scope)
                acquireWakeLock()
                refreshNotification()
                Notifications.cancelResume(this)
                scope.launch { settingsRepository.update { it.copy(wasListening = true) } }
            }
        }
        if (!pipeline.isRunning) pipeline.start(scope)
        // Android 14+ forbids restarting a microphone service from the background, so don't be sticky.
        return START_NOT_STICKY
    }

    private fun ensureForeground(paused: Boolean): Boolean {
        if (inForeground) return true
        return try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            ServiceCompat.startForeground(this, Notifications.ID_LISTENING, Notifications.listening(this, paused), type)
            inForeground = true
            true
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException / SecurityException (no mic permission or started from background).
            Log.w(TAG, "Cannot start foreground service", e)
            stopSelf()
            false
        }
    }

    @SuppressLint("MissingPermission") // checked by Notifications.canPost
    private fun refreshNotification() {
        if (!Notifications.canPost(this)) return
        NotificationManagerCompat.from(this)
            .notify(Notifications.ID_LISTENING, Notifications.listening(this, pipeline.isPaused))
    }

    // Held for as long as the user keeps listening on; released on pause/stop/destroy.
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HapticHearing:listening").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun shutdown() {
        pipeline.stop()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        inForeground = false
        stopSelf()
    }

    override fun onDestroy() {
        pipeline.stop()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HearingService"
        const val ACTION_START = "com.hapticHearing.action.START"
        const val ACTION_PAUSE = "com.hapticHearing.action.PAUSE"
        const val ACTION_RESUME = "com.hapticHearing.action.RESUME"
        const val ACTION_STOP = "com.hapticHearing.action.STOP"
    }
}
