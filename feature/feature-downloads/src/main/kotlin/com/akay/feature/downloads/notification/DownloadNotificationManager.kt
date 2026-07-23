package com.akay.feature.downloads.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object DownloadNotificationManager {

    private const val BASE_NOTIFICATION_ID = 9000
    private const val CHANNEL_PROGRESS = "ax_dl_progress"
    private const val CHANNEL_COMPLETE = "ax_dl_complete"
    private const val CHANNEL_FAILED = "ax_dl_failed"

    fun showProgress(context: Context, downloadId: String, filename: String, percent: Float, speedStr: String, totalStr: String) {
        if (!hasPermission(context)) return
        val notifId = notifIdFor(downloadId)
        val notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading: $filename")
            .setContentText("${"%.1f".format(percent)}% - $speedStr - $totalStr")
            .setProgress(100, percent.toInt(), percent == 0f)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tapPendingIntent(context))
            .setSilent(true)
            .build()
        NotificationManagerCompat.from(context).notify(notifId, notification)
    }

    fun showComplete(context: Context, downloadId: String, filename: String) {
        if (!hasPermission(context)) return
        NotificationManagerCompat.from(context).notify(
            notifIdFor(downloadId),
            NotificationCompat.Builder(context, CHANNEL_COMPLETE)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download complete")
                .setContentText(filename)
                .setAutoCancel(true)
                .setContentIntent(tapPendingIntent(context))
                .build()
        )
    }

    fun showFailed(context: Context, downloadId: String, filename: String, reason: String) {
        if (!hasPermission(context)) return
        NotificationManagerCompat.from(context).notify(
            notifIdFor(downloadId),
            NotificationCompat.Builder(context, CHANNEL_FAILED)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Download failed: $filename")
                .setContentText(reason)
                .setAutoCancel(true)
                .setContentIntent(tapPendingIntent(context))
                .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
                .build()
        )
    }

    fun cancel(context: Context, downloadId: String) {
        NotificationManagerCompat.from(context).cancel(notifIdFor(downloadId))
    }

    private fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun notifIdFor(downloadId: String): Int = BASE_NOTIFICATION_ID + (downloadId.hashCode() and 0x00FFFFFF)

    private fun tapPendingIntent(context: Context): PendingIntent {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(context.packageName) ?: Intent().apply { `package` = context.packageName }
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
