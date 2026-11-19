package com.hapticHearing.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hapticHearing.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A microphone service cannot start from the background after a reboot, so if the user had
 * listening on we post "Tap to resume listening" instead (spec §12).
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (settingsRepository.settings.first().wasListening) {
                    Notifications.showResumeAfterBoot(context.applicationContext)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
