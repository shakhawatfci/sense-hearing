package com.hapticHearing.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Starts/stops [HearingService]. [start] must be called while the app is visible. */
object ServiceController {
    fun start(context: Context) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, HearingService::class.java).setAction(HearingService.ACTION_START),
        )
    }

    fun stop(context: Context) {
        context.startService(Intent(context, HearingService::class.java).setAction(HearingService.ACTION_STOP))
    }
}
