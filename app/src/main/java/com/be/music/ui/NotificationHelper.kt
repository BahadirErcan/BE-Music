package com.be.music.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val DOWNLOAD_CHANNEL_ID = "download_channel"
        const val PLAYBACK_CHANNEL_ID = "playback_channel"
    }

    fun createNotificationChannels() {
        val downloadChannel = NotificationChannel(
            DOWNLOAD_CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress of music downloads"
        }

        val playbackChannel = NotificationChannel(
            PLAYBACK_CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Media controls for music playback"
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(downloadChannel)
        manager?.createNotificationChannel(playbackChannel)
    }
}
