package com.nageh.cliniccollections

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager

class ClinicApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)

        // A channel's importance, sound and vibration are fixed the moment it is created;
        // an app update cannot raise them. Existing users were on the old "payments"
        // channel, so a new versioned ID is registered and the old one is removed. That
        // gives everyone sound and vibration without reinstalling the app.
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_channel_description)
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 400L, 200L, 400L)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build()
            )
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        /** Versioned: bump this suffix again if sound or importance ever needs to change. */
        const val CHANNEL_ID = "payments_v2_high"
        private const val LEGACY_CHANNEL_ID = "payments"
    }
}
