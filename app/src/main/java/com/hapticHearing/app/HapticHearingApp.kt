package com.hapticHearing.app

import android.app.Application
import com.hapticHearing.service.Notifications
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HapticHearingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
