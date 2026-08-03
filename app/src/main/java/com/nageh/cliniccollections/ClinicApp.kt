package com.nageh.cliniccollections

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class ClinicApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // minSdk is 29, so notification channels always exist on supported devices.
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_channel_description)
            enableVibration(true)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "payments"
    }
}
