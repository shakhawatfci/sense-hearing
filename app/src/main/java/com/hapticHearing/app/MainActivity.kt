package com.hapticHearing.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.hapticHearing.service.ServiceController
import com.hapticHearing.ui.theme.HapticHearingTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HapticHearingTheme {
                AppNavHost()
            }
        }
        handleResume(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleResume(intent)
    }

    /** "Tap to resume listening" after a reboot: the app is visible now, so the mic service may start. */
    private fun handleResume(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_RESUME_LISTENING, false) != true) return
        intent.removeExtra(EXTRA_RESUME_LISTENING)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            ServiceController.start(this)
        }
    }

    companion object {
        const val EXTRA_RESUME_LISTENING = "resume_listening"
    }
}
