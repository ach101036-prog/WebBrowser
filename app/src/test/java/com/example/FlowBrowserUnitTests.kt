package com.example

import com.example.data.browser.BrowserTab
import com.example.data.browser.DetectedVideo
import com.example.data.browser.UrlStreamClassifier
import com.example.data.browser.VideoResolution
import com.example.data.ffmpeg.FfmpegCommandBuilder
import com.example.service.DownloadCoordinator
import com.example.service.DownloadRequest
import com.example.service.JobStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowBrowserUnitTests {

    @Test
    fun normalizeAddressInput_handlesUrlsDomainsAndSearchQueries() {
        assertEquals(BrowserTab.HOME_URL, UrlStreamClassifier.normalizeAddressInput(""))
        assertEquals("https://vimeo.com/watch", UrlStreamClassifier.normalizeAddressInput("vimeo.com/watch"))
        assertEquals("http://example.com/video.mp4", UrlStreamClassifier.normalizeAddressInput("http://example.com/video.mp4"))
        assertEquals(
            "https://www.google.com/search?q=big+buck+bunny",
            UrlStreamClassifier.normalizeAddressInput("big buck bunny")
        )
    }

    @Test
    fun classifyVideoUrl_detectsMp4HlsDashAndIgnoresAssets() {
        val mp4 = UrlStreamClassifier.classifyVideoUrl("https://cdn.example.com/movie/clip.mp4?token=123")
        assertNotNull(mp4)
        assertEquals("video/mp4", mp4?.mimeType)
        assertFalse(mp4?.isHls == true)

        val hls = UrlStreamClassifier.classifyVideoUrl("https://stream.example.com/live/master.m3u8?hdnea=abc")
        assertNotNull(hls)
        assertTrue(hls?.isHls == true)

        val dash = UrlStreamClassifier.classifyVideoUrl("https://stream.example.com/manifest.mpd")
        assertNotNull(dash)
        assertTrue(dash?.isDash == true)

        assertNull(UrlStreamClassifier.classifyVideoUrl("https://cdn.example.com/thumb.jpg"))
        assertNull(UrlStreamClassifier.classifyVideoUrl("https://cdn.example.com/segment_01.ts"))
        assertNull(UrlStreamClassifier.classifyVideoUrl("blob:https://example.com/1234"))
    }

    @Test
    fun sanitizeFileName_createsSafeMp4Filename() {
        assertEquals("My_Cool_Video.mp4", UrlStreamClassifier.sanitizeFileName("My Cool Video!@#"))
        assertEquals("clip.mp4", UrlStreamClassifier.sanitizeFileName("clip.mp4"))
    }

    @Test
    fun ffmpegCommandBuilder_includesScaleFilterForChosenResolution() {
        val args720 = FfmpegCommandBuilder.buildTranscodeArgs(
            input = "/tmp/src.tmp",
            outputPath = "/tmp/out.mp4",
            resolution = VideoResolution.P720,
            isRemoteStream = false,
            userAgent = "TestAgent",
            referer = "https://example.com/",
            cookies = "a=b"
        ).toList()

        assertTrue(args720.contains("-vf"))
        val vfIndex = args720.indexOf("-vf")
        assertTrue(args720[vfIndex + 1].contains("720"))
        assertTrue(args720.contains("libx264"))
        assertTrue(args720.contains("aac"))
        assertTrue(args720.contains("+faststart"))
    }

    @Test
    fun downloadCoordinator_tracksQueuedRunningAndCompletedJobs() {
        val coordinator = DownloadCoordinator()
        val video = DetectedVideo(
            id = "v1",
            url = "https://example.com/sample.mp4",
            pageUrl = "https://example.com/",
            title = "Sample Clip",
            mimeType = "video/mp4"
        )
        val req = DownloadRequest(
            jobId = "job_1",
            video = video,
            resolution = VideoResolution.P1080,
            outputFileName = "Sample_Clip.mp4"
        )

        coordinator.registerQueued(req)
        assertEquals(JobStatus.QUEUED, coordinator.jobs.value.first().status)

        coordinator.updateProgress("job_1", "Encoding 1080p MP4", 0.65f, "1.8x")
        val running = coordinator.jobs.value.first()
        assertEquals(JobStatus.RUNNING, running.status)
        assertEquals(0.65f, running.progress, 0.001f)

        coordinator.markCompleted("job_1", 42L, "Sample_Clip.mp4")
        val completed = coordinator.jobs.value.first()
        assertEquals(JobStatus.COMPLETED, completed.status)
        assertEquals(42L, completed.savedVideoId)
    }
}
