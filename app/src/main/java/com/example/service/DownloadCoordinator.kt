package com.example.service

import com.arthenica.ffmpegkit.FFmpegKit
import com.example.data.browser.DetectedVideo
import com.example.data.browser.VideoResolution
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class DownloadRequest(
    val jobId: String,
    val video: DetectedVideo,
    val resolution: VideoResolution,
    val outputFileName: String
)

enum class JobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class ActiveDownloadJob(
    val jobId: String,
    val title: String,
    val outputFileName: String,
    val resolution: VideoResolution,
    val stage: String,
    val detail: String,
    val progress: Float,
    val status: JobStatus,
    val savedVideoId: Long? = null,
    val errorMessage: String? = null
) {
    val isActive: Boolean get() = status == JobStatus.QUEUED || status == JobStatus.RUNNING
}

data class DownloadBannerEvent(
    val jobId: String,
    val title: String,
    val isError: Boolean,
    val message: String,
    val savedVideoId: Long? = null
)

class DownloadCoordinator {

    private val _jobs = MutableStateFlow<List<ActiveDownloadJob>>(emptyList())
    val jobs: StateFlow<List<ActiveDownloadJob>> = _jobs.asStateFlow()

    private val _bannerEvent = MutableStateFlow<DownloadBannerEvent?>(null)
    val bannerEvent: StateFlow<DownloadBannerEvent?> = _bannerEvent.asStateFlow()

    private val cancellationFlags = mutableMapOf<String, AtomicBoolean>()
    private val ffmpegSessionIds = mutableMapOf<String, AtomicLong>()

    fun registerQueued(request: DownloadRequest): Pair<AtomicBoolean, AtomicLong> {
        val cancelFlag = AtomicBoolean(false)
        val sessionId = AtomicLong(0L)
        synchronized(this) {
            cancellationFlags[request.jobId] = cancelFlag
            ffmpegSessionIds[request.jobId] = sessionId
        }
        val job = ActiveDownloadJob(
            jobId = request.jobId,
            title = request.video.title.ifBlank { request.outputFileName },
            outputFileName = request.outputFileName,
            resolution = request.resolution,
            stage = "Queued for FFmpeg…",
            detail = "Target: ${request.resolution.label}",
            progress = 0f,
            status = JobStatus.QUEUED
        )
        _jobs.update { current -> listOf(job) + current.filterNot { it.jobId == request.jobId } }
        return cancelFlag to sessionId
    }

    fun getControls(jobId: String): Pair<AtomicBoolean, AtomicLong> = synchronized(this) {
        val flag = cancellationFlags.getOrPut(jobId) { AtomicBoolean(false) }
        val sid = ffmpegSessionIds.getOrPut(jobId) { AtomicLong(0L) }
        flag to sid
    }

    fun updateProgress(jobId: String, stage: String, progress: Float, detail: String) {
        _jobs.update { list ->
            list.map { job ->
                if (job.jobId == jobId && job.isActive) {
                    job.copy(
                        status = JobStatus.RUNNING,
                        stage = stage,
                        progress = progress.coerceIn(0f, 1f),
                        detail = detail
                    )
                } else {
                    job
                }
            }
        }
    }

    fun markCompleted(jobId: String, savedVideoId: Long, fileName: String) {
        cleanupControls(jobId)
        var jobTitle = fileName
        _jobs.update { list ->
            list.map { job ->
                if (job.jobId == jobId) {
                    jobTitle = job.title
                    job.copy(
                        status = JobStatus.COMPLETED,
                        stage = "Saved to MP4 Downloads",
                        detail = fileName,
                        progress = 1f,
                        savedVideoId = savedVideoId
                    )
                } else {
                    job
                }
            }
        }
        _bannerEvent.value = DownloadBannerEvent(
            jobId = jobId,
            title = jobTitle,
            isError = false,
            message = "MP4 saved: $fileName",
            savedVideoId = savedVideoId
        )
    }

    fun markFailed(jobId: String, message: String) {
        cleanupControls(jobId)
        var jobTitle = "Video Download"
        _jobs.update { list ->
            list.map { job ->
                if (job.jobId == jobId) {
                    jobTitle = job.title
                    job.copy(
                        status = JobStatus.FAILED,
                        stage = "Failed",
                        detail = message,
                        errorMessage = message
                    )
                } else {
                    job
                }
            }
        }
        _bannerEvent.value = DownloadBannerEvent(
            jobId = jobId,
            title = jobTitle,
            isError = true,
            message = message
        )
    }

    fun requestCancel(jobId: String) {
        val (flag, sessionAtomic) = synchronized(this) {
            cancellationFlags[jobId] to ffmpegSessionIds[jobId]
        }
        flag?.set(true)
        val sid = sessionAtomic?.get() ?: 0L
        if (sid > 0L) {
            FFmpegKit.cancel(sid)
        }
        _jobs.update { list ->
            list.map { job ->
                if (job.jobId == jobId && job.isActive) {
                    job.copy(
                        status = JobStatus.CANCELLED,
                        stage = "Cancelled",
                        detail = "Stopped by user"
                    )
                } else {
                    job
                }
            }
        }
    }

    fun dismissInactiveJob(jobId: String) {
        _jobs.update { list -> list.filterNot { it.jobId == jobId && !it.isActive } }
    }

    fun dismissBanner() {
        _bannerEvent.value = null
    }

    private fun cleanupControls(jobId: String) {
        synchronized(this) {
            cancellationFlags.remove(jobId)
            ffmpegSessionIds.remove(jobId)
        }
    }
}
