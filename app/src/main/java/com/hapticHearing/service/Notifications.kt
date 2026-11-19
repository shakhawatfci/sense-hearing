package com.hapticHearing.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hapticHearing.R
import com.hapticHearing.app.MainActivity

object Notifications {
    const val CHANNEL_LISTENING = "listening"
    const val CHANNEL_RESUME = "resume"
    const val ID_LISTENING = 1
    const val ID_RESUME = 2

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_LISTENING, context.getString(R.string.channel_listening), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESUME, context.getString(R.string.channel_resume), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun openAppIntent(context: Context, resume: Boolean = false): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (resume) putExtra(MainActivity.EXTRA_RESUME_LISTENING, true)
        }
        return PendingIntent.getActivity(
            context,
            if (resume) 1 else 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun serviceAction(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, HearingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun listening(context: Context, paused: Boolean): Notification {
        val toggle = if (paused) {
            NotificationCompat.Action(0, context.getString(R.string.notif_action_resume), serviceAction(context, HearingService.ACTION_RESUME, 11))
        } else {
            NotificationCompat.Action(0, context.getString(R.string.notif_action_pause), serviceAction(context, HearingService.ACTION_PAUSE, 10))
        }
        return NotificationCompat.Builder(context, CHANNEL_LISTENING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(if (paused) R.string.notif_paused_title else R.string.notif_listening_title))
            .setContentText(context.getString(R.string.notif_listening_text))
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(toggle)
            .addAction(0, context.getString(R.string.notif_action_stop), serviceAction(context, HearingService.ACTION_STOP, 12))
            .build()
    }

    @SuppressLint("MissingPermission") // checked by canPost
    fun showResumeAfterBoot(context: Context) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_RESUME)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_resume_title))
            .setContentText(context.getString(R.string.notif_resume_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notif_resume_text)))
            .setContentIntent(openAppIntent(context, resume = true))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_RESUME, n)
    }

    fun cancelResume(context: Context) = NotificationManagerCompat.from(context).cancel(ID_RESUME)

    fun canPost(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
