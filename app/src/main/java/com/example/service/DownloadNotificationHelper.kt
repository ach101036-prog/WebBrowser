package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

object DownloadNotificationHelper {
    const val CHANNEL_DOWNLOADING = "flowbrowser_downloading"
    const val CHANNEL_COMPLETED = "flowbrowser_completed"

    const val NOTIFICATION_ID_ONGOING = 4201
    const val EXTRA_OPEN_DOWNLOADS = "extra_open_downloads"
    const val EXTRA_OPEN_VIDEO_ID = "extra_open_video_id"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val ongoing = NotificationChannel(
                CHANNEL_DOWNLOADING,
                context.getString(R.string.notification_channel_downloading),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress while FFmpeg downloads and converts web videos to MP4"
                setShowBadge(false)
            }

            val completed = NotificationChannel(
                CHANNEL_COMPLETED,
                context.getString(R.string.notification_channel_completed),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when a background MP4 conversion finishes"
            }

            manager.createNotificationChannel(ongoing)
            manager.createNotificationChannel(completed)
        }
    }

    fun buildOngoingNotification(
        context: Context,
        title: String,
        resolutionLabel: String,
        stage: String,
        progressPercent: Int
    ): Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_DOWNLOADS, true)
        }
        val openPending = PendingIntent.getActivity(
            context,
            401,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val clamped = progressPercent.coerceIn(0, 100)
        return NotificationCompat.Builder(context, CHANNEL_DOWNLOADING)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("$title ($resolutionLabel MP4)")
            .setContentText("$stage • $clamped%")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, clamped, clamped == 0)
            .setContentIntent(openPending)
            .build()
    }

    fun showCompletedNotification(
        context: Context,
        videoId: Long,
        title: String,
        resolutionLabel: String,
        sizeText: String
    ) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_DOWNLOADS, true)
            putExtra(EXTRA_OPEN_VIDEO_ID, videoId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            videoId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETED)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.notification_completed_title))
            .setContentText("$title • $resolutionLabel • $sizeText")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(4300 + videoId.toInt(), notification)
        }
    }
}
