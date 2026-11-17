package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.example.FlowBrowserApp
import com.example.data.ffmpeg.FfmpegBackgroundProcessor
import com.example.data.local.DownloadedVideoEntity
import com.example.data.recorder.MediaStoreSaver
import com.example.ui.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var workerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as FlowBrowserApp
        val coordinator = app.container.downloadCoordinator

        when (intent?.action) {
            ACTION_ENQUEUE_DOWNLOAD -> {
                val firstTitle = pendingQueue.peek()?.video?.title ?: "MP4 Video"
                val firstRes = pendingQueue.peek()?.resolution?.shortLabel ?: "MP4"
                startForegroundCompat(firstTitle, firstRes)
                ensureWorkerRunning(app, coordinator)
            }
            ACTION_CANCEL_JOB -> {
                val jobId = intent.getStringExtra(EXTRA_JOB_ID)
                if (!jobId.isNullOrBlank()) {
                    coordinator.requestCancel(jobId)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(title: String, resolutionLabel: String) {
        val notification = DownloadNotificationHelper.buildOngoingNotification(
            context = this,
            title = title,
            resolutionLabel = resolutionLabel,
            stage = "Preparing FFmpeg…",
            progressPercent = 0
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                DownloadNotificationHelper.NOTIFICATION_ID_ONGOING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(DownloadNotificationHelper.NOTIFICATION_ID_ONGOING, notification)
        }
    }

    private fun ensureWorkerRunning(app: FlowBrowserApp, coordinator: DownloadCoordinator) {
        if (workerJob?.isActive == true) return
        val processor = FfmpegBackgroundProcessor(applicationContext)

        workerJob = serviceScope.launch {
            try {
                while (true) {
                    val request = pendingQueue.poll() ?: break
                    val (cancelFlag, sessionAtomic) = coordinator.getControls(request.jobId)
                    if (cancelFlag.get()) continue

                    var lastNotifiedPct = -1
                    try {
                        val result = processor.processVideoToMp4(
                            video = request.video,
                            targetResolution = request.resolution,
                            isCancelled = cancelFlag,
                            activeSessionId = sessionAtomic,
                            onStatus = { stage, progress, detail ->
                                coordinator.updateProgress(request.jobId, stage, progress, detail)
                                val pct = (progress * 100f).toInt().coerceIn(0, 100)
                                if (pct != lastNotifiedPct && (pct - lastNotifiedPct >= 3 || pct == 0 || pct >= 97)) {
                                    lastNotifiedPct = pct
                                    val ongoing = DownloadNotificationHelper.buildOngoingNotification(
                                        context = this@DownloadService,
                                        title = request.video.title,
                                        resolutionLabel = request.resolution.shortLabel,
                                        stage = stage,
                                        progressPercent = pct
                                    )
                                    runCatching {
                                        NotificationManagerCompat.from(this@DownloadService)
                                            .notify(DownloadNotificationHelper.NOTIFICATION_ID_ONGOING, ongoing)
                                    }
                                }
                            }
                        )

                        val saved = MediaStoreSaver.saveMp4ToDevice(
                            context = applicationContext,
                            tempMp4File = result.outputFile,
                            desiredFileName = request.outputFileName
                        )

                        val entity = DownloadedVideoEntity(
                            title = request.video.title.ifBlank { saved.fileName },
                            sourcePageUrl = request.video.pageUrl,
                            mediaUrl = request.video.url,
                            fileName = saved.fileName,
                            contentUri = saved.contentUri,
                            filePath = saved.filePath,
                            resolutionLabel = if (request.resolution.targetHeight != null) {
                                request.resolution.shortLabel
                            } else if (result.height > 0) {
                                "${result.height}p"
                            } else {
                                "Source"
                            },
                            width = result.width,
                            height = result.height,
                            durationMs = result.durationMs,
                            fileSizeBytes = saved.fileSizeBytes,
                            createdAtMillis = System.currentTimeMillis()
                        )
                        val insertedId = app.container.downloadRepository.saveDownload(entity)
                        coordinator.markCompleted(request.jobId, insertedId, saved.fileName)

                        DownloadNotificationHelper.showCompletedNotification(
                            context = applicationContext,
                            videoId = insertedId,
                            title = entity.title,
                            resolutionLabel = entity.resolutionLabel,
                            sizeText = Formatters.formatBytes(saved.fileSizeBytes)
                        )
                    } catch (cancelled: InterruptedException) {
                        // Already marked cancelled by coordinator
                    } catch (e: Exception) {
                        if (!cancelFlag.get()) {
                            coordinator.markFailed(
                                request.jobId,
                                e.message ?: "FFmpeg could not convert this video stream."
                            )
                        }
                    }
                }
            } finally {
                ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        workerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_ENQUEUE_DOWNLOAD = "com.example.action.ENQUEUE_DOWNLOAD"
        const val ACTION_CANCEL_JOB = "com.example.action.CANCEL_JOB"
        const val EXTRA_JOB_ID = "extra_job_id"

        private val pendingQueue = ConcurrentLinkedQueue<DownloadRequest>()

        fun enqueueDownload(
            context: Context,
            request: DownloadRequest,
            coordinator: DownloadCoordinator
        ) {
            coordinator.registerQueued(request)
            pendingQueue.add(request)
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_ENQUEUE_DOWNLOAD
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelJob(context: Context, jobId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL_JOB
                putExtra(EXTRA_JOB_ID, jobId)
            }
            context.startService(intent)
        }
    }
}
