package com.example.data.ffmpeg

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.example.data.browser.DetectedVideo
import com.example.data.browser.VideoResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

data class FfmpegProcessResult(
    val outputFile: File,
    val width: Int,
    val height: Int,
    val durationMs: Long
)

data class MediaProbeInfo(
    val durationMs: Long,
    val width: Int,
    val height: Int
)

class FfmpegBackgroundProcessor(private val context: Context) {

    suspend fun processVideoToMp4(
        video: DetectedVideo,
        targetResolution: VideoResolution,
        isCancelled: AtomicBoolean,
        activeSessionId: AtomicLong,
        onStatus: (stage: String, progress: Float, detail: String) -> Unit
    ): FfmpegProcessResult = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "ffmpeg_work").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val tempSource = File(workDir, "src_$stamp.tmp")
        val outputMp4 = File(workDir, "out_$stamp.mp4")

        try {
            val isRemoteOnly = video.isHls || video.isDash
            var inputForFfmpeg = video.url
            var knownDurationMs = (video.durationSec * 1000.0).toLong().coerceAtLeast(0L)

            if (!isRemoteOnly) {
                onStatus("Downloading source stream…", 0.02f, "Connecting to server")
                val downloaded = OkHttpStreamDownloader.downloadToTempFile(
                    url = video.url,
                    targetFile = tempSource,
                    userAgent = video.userAgent,
                    referer = video.referer,
                    cookies = video.cookies,
                    isCancelled = isCancelled,
                    onProgress = { bytes, total ->
                        val frac = if (total > 0L) {
                            (bytes.toFloat() / total.toFloat()).coerceIn(0f, 1f) * 0.45f
                        } else {
                            0.2f
                        }
                        onStatus(
                            "Downloading source stream…",
                            frac,
                            if (total > 0L) "${(frac / 0.45f * 100).toInt()}% fetched" else "${bytes / 1024} KB fetched"
                        )
                    }
                )
                if (!downloaded || isCancelled.get()) {
                    throw InterruptedException("Download cancelled")
                }
                inputForFfmpeg = tempSource.absolutePath

                val probed = probeMedia(inputForFfmpeg)
                if (probed.durationMs > 0L) {
                    knownDurationMs = probed.durationMs
                }
            } else {
                onStatus("Probing stream with FFprobe…", 0.05f, video.formatLabel)
                val probed = probeMedia(video.url)
                if (probed.durationMs > 0L) {
                    knownDurationMs = probed.durationMs
                }
            }

            if (isCancelled.get()) {
                throw InterruptedException("Download cancelled")
            }

            val baseProgress = if (isRemoteOnly) 0.08f else 0.45f
            val progressSpan = 0.96f - baseProgress

            val succeeded = if (targetResolution == VideoResolution.ORIGINAL) {
                onStatus(
                    "FFmpeg remuxing to MP4…",
                    baseProgress,
                    "Preserving source quality"
                )
                val remuxArgs = FfmpegCommandBuilder.buildRemuxArgs(
                    input = inputForFfmpeg,
                    outputPath = outputMp4.absolutePath,
                    isRemoteStream = isRemoteOnly,
                    isHls = video.isHls,
                    userAgent = video.userAgent,
                    referer = video.referer,
                    cookies = video.cookies
                )
                val remuxOk = runFfmpegAsync(
                    args = remuxArgs,
                    knownDurationMs = knownDurationMs,
                    baseProgress = baseProgress,
                    progressSpan = progressSpan,
                    activeSessionId = activeSessionId,
                    onProgress = { p, statText ->
                        onStatus("FFmpeg remuxing to MP4…", p, statText)
                    }
                )
                if (remuxOk && outputMp4.exists() && outputMp4.length() > 1024L) {
                    true
                } else if (!isCancelled.get()) {
                    outputMp4.delete()
                    onStatus(
                        "FFmpeg encoding MP4 (${targetResolution.shortLabel})…",
                        baseProgress,
                        "Transcoding video & audio to MP4"
                    )
                    runTranscodeWithFallback(
                        input = inputForFfmpeg,
                        output = outputMp4,
                        resolution = targetResolution,
                        isRemoteOnly = isRemoteOnly,
                        video = video,
                        knownDurationMs = knownDurationMs,
                        baseProgress = baseProgress,
                        progressSpan = progressSpan,
                        isCancelled = isCancelled,
                        activeSessionId = activeSessionId,
                        onProgress = { p, statText ->
                            onStatus("FFmpeg encoding MP4…", p, statText)
                        }
                    )
                } else {
                    false
                }
            } else {
                onStatus(
                    "FFmpeg scaling to ${targetResolution.shortLabel} MP4…",
                    baseProgress,
                    "Encoding H.264 + AAC"
                )
                runTranscodeWithFallback(
                    input = inputForFfmpeg,
                    output = outputMp4,
                    resolution = targetResolution,
                    isRemoteOnly = isRemoteOnly,
                    video = video,
                    knownDurationMs = knownDurationMs,
                    baseProgress = baseProgress,
                    progressSpan = progressSpan,
                    isCancelled = isCancelled,
                    activeSessionId = activeSessionId,
                    onProgress = { p, statText ->
                        onStatus("FFmpeg encoding ${targetResolution.shortLabel} MP4…", p, statText)
                    }
                )
            }

            if (isCancelled.get()) {
                throw InterruptedException("Download cancelled")
            }

            if (!succeeded || !outputMp4.exists() || outputMp4.length() < 1024L) {
                throw IllegalStateException("FFmpeg failed to produce a valid MP4 file.")
            }

            onStatus("Finalizing MP4 metadata…", 0.97f, "Saving to device gallery")
            val finalProbe = probeMedia(outputMp4.absolutePath)

            FfmpegProcessResult(
                outputFile = outputMp4,
                width = if (finalProbe.width > 0) finalProbe.width else video.sourceWidth,
                height = if (finalProbe.height > 0) finalProbe.height else (targetResolution.targetHeight ?: video.sourceHeight),
                durationMs = if (finalProbe.durationMs > 0L) finalProbe.durationMs else knownDurationMs
            )
        } catch (t: Throwable) {
            outputMp4.delete()
            throw t
        } finally {
            tempSource.delete()
        }
    }

    private suspend fun runTranscodeWithFallback(
        input: String,
        output: File,
        resolution: VideoResolution,
        isRemoteOnly: Boolean,
        video: DetectedVideo,
        knownDurationMs: Long,
        baseProgress: Float,
        progressSpan: Float,
        isCancelled: AtomicBoolean,
        activeSessionId: AtomicLong,
        onProgress: (Float, String) -> Unit
    ): Boolean {
        val x264Args = FfmpegCommandBuilder.buildTranscodeArgs(
            input = input,
            outputPath = output.absolutePath,
            resolution = resolution,
            isRemoteStream = isRemoteOnly,
            userAgent = video.userAgent,
            referer = video.referer,
            cookies = video.cookies,
            useMpeg4Fallback = false
        )
        val ok = runFfmpegAsync(
            args = x264Args,
            knownDurationMs = knownDurationMs,
            baseProgress = baseProgress,
            progressSpan = progressSpan,
            activeSessionId = activeSessionId,
            onProgress = onProgress
        )
        if (ok && output.exists() && output.length() > 1024L) return true
        if (isCancelled.get()) return false

        output.delete()
        val mpeg4Args = FfmpegCommandBuilder.buildTranscodeArgs(
            input = input,
            outputPath = output.absolutePath,
            resolution = resolution,
            isRemoteStream = isRemoteOnly,
            userAgent = video.userAgent,
            referer = video.referer,
            cookies = video.cookies,
            useMpeg4Fallback = true
        )
        return runFfmpegAsync(
            args = mpeg4Args,
            knownDurationMs = knownDurationMs,
            baseProgress = baseProgress,
            progressSpan = progressSpan,
            activeSessionId = activeSessionId,
            onProgress = onProgress
        )
    }

    private suspend fun runFfmpegAsync(
        args: Array<String>,
        knownDurationMs: Long,
        baseProgress: Float,
        progressSpan: Float,
        activeSessionId: AtomicLong,
        onProgress: (Float, String) -> Unit
    ): Boolean = suspendCancellableCoroutine { cont ->
        val session = FFmpegKit.executeWithArgumentsAsync(
            args,
            { completedSession ->
                activeSessionId.compareAndSet(completedSession.sessionId, 0L)
                val success = ReturnCode.isSuccess(completedSession.returnCode)
                if (cont.isActive) {
                    cont.resume(success)
                }
            },
            { /* log callback unused in hot loop */ },
            { stats ->
                val encodedMs = stats.time.toLong().coerceAtLeast(0L)
                val fraction = if (knownDurationMs > 0L) {
                    val ratio = (encodedMs.toFloat() / knownDurationMs.toFloat()).coerceIn(0f, 1f)
                    baseProgress + ratio * progressSpan
                } else {
                    (baseProgress + progressSpan * 0.5f).coerceAtMost(0.9f)
                }
                val speedText = if (stats.speed > 0f) {
                    String.format("%.1fx • %d KB", stats.speed, stats.size / 1024L)
                } else {
                    "${stats.size / 1024L} KB encoded"
                }
                onProgress(fraction, speedText)
            }
        )
        activeSessionId.set(session.sessionId)
        cont.invokeOnCancellation {
            FFmpegKit.cancel(session.sessionId)
        }
    }

    private fun probeMedia(pathOrUrl: String): MediaProbeInfo {
        return runCatching {
            val session = FFprobeKit.getMediaInformation(pathOrUrl)
            val info = session.mediaInformation ?: return MediaProbeInfo(0L, 0, 0)
            val durationSec = info.duration?.toDoubleOrNull() ?: 0.0
            val videoStream = info.streams?.firstOrNull {
                it.type.equals("video", ignoreCase = true)
            }
            MediaProbeInfo(
                durationMs = (durationSec * 1000.0).toLong().coerceAtLeast(0L),
                width = videoStream?.width?.toInt() ?: 0,
                height = videoStream?.height?.toInt() ?: 0
            )
        }.getOrDefault(MediaProbeInfo(0L, 0, 0))
    }
}
