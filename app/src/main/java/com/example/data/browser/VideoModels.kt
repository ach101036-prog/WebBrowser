package com.example.data.browser

enum class VideoResolution(
    val label: String,
    val shortLabel: String,
    val targetHeight: Int?,
    val description: String
) {
    ORIGINAL(
        label = "Original Quality",
        shortLabel = "Source",
        targetHeight = null,
        description = "Keep source resolution (fast MP4 mux/transcode)"
    ),
    P1080(
        label = "1080p Full HD",
        shortLabel = "1080p",
        targetHeight = 1080,
        description = "1920×1080 • Crisp high-definition H.264 MP4"
    ),
    P720(
        label = "720p HD",
        shortLabel = "720p",
        targetHeight = 720,
        description = "1280×720 • Balanced clarity & file size"
    ),
    P480(
        label = "480p SD",
        shortLabel = "480p",
        targetHeight = 480,
        description = "854×480 • Fast FFmpeg encode, compact size"
    ),
    P360(
        label = "360p Compact",
        shortLabel = "360p",
        targetHeight = 360,
        description = "640×360 • Data-saver mobile MP4"
    )
}

data class DetectedVideo(
    val id: String,
    val url: String,
    val pageUrl: String,
    val title: String,
    val mimeType: String,
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val durationSec: Double = 0.0,
    val estimatedSizeBytes: Long = 0L,
    val isPlaying: Boolean = false,
    val isHls: Boolean = false,
    val isDash: Boolean = false,
    val cookies: String = "",
    val userAgent: String = "",
    val referer: String = "",
    val detectedAtMillis: Long = System.currentTimeMillis()
) {
    val resolutionBadge: String
        get() = when {
            sourceHeight > 0 -> "${sourceHeight}p"
            isHls -> "HLS"
            isDash -> "DASH"
            else -> "MP4"
        }

    val formatLabel: String
        get() = when {
            isHls -> "HLS Stream → MP4"
            isDash -> "DASH Stream → MP4"
            mimeType.contains("webm", ignoreCase = true) -> "WebM → MP4"
            else -> "Direct Video → MP4"
        }
}

data class BrowserTab(
    val id: String,
    val title: String = "New Tab",
    val url: String = HOME_URL,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val detectedVideos: List<DetectedVideo> = emptyList()
) {
    val isHomePage: Boolean get() = url == HOME_URL || url.isBlank()
    val hasPlayingVideo: Boolean get() = detectedVideos.any { it.isPlaying }
    val primaryVideo: DetectedVideo?
        get() = detectedVideos.firstOrNull { it.isPlaying } ?: detectedVideos.firstOrNull()

    companion object {
        const val HOME_URL = "about:home"
    }
}

data class QuickLink(
    val title: String,
    val subtitle: String,
    val url: String,
    val tag: String
)

object DefaultQuickLinks {
    val items = listOf(
        QuickLink(
            title = "Big Buck Bunny",
            subtitle = "HTML5 MP4 Demo",
            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            tag = "1080p MP4"
        ),
        QuickLink(
            title = "W3Schools Video",
            subtitle = "HTML5 <video> Player",
            url = "https://www.w3schools.com/html/html5_video.asp",
            tag = "Web Player"
        ),
        QuickLink(
            title = "Wikimedia Video",
            subtitle = "Open Commons Clips",
            url = "https://commons.wikimedia.org/wiki/Category:Featured_videos",
            tag = "Open Media"
        ),
        QuickLink(
            title = "Internet Archive",
            subtitle = "Public Domain Films",
            url = "https://archive.org/details/movies",
            tag = "Cinema"
        ),
        QuickLink(
            title = "Vimeo Watch",
            subtitle = "HD Creator Showcase",
            url = "https://vimeo.com/watch",
            tag = "HD Streams"
        ),
        QuickLink(
            title = "Pexels Videos",
            subtitle = "Free Stock Footage",
            url = "https://www.pexels.com/videos/",
            tag = "4K / HD"
        )
    )
}
