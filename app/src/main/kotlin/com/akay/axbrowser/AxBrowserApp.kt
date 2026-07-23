package com.akay.axbrowser

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AxBrowserApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DOWNLOAD_PROGRESS, "Download Progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows active download progress"
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DOWNLOAD_COMPLETE, "Download Complete", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifies when a download finishes"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DOWNLOAD_FAILED, "Download Failed", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifies when a download fails"
            }
        )
    }

    companion object {
        const val CHANNEL_DOWNLOAD_PROGRESS = "ax_dl_progress"
        const val CHANNEL_DOWNLOAD_COMPLETE = "ax_dl_complete"
        const val CHANNEL_DOWNLOAD_FAILED = "ax_dl_failed"
    }
}
