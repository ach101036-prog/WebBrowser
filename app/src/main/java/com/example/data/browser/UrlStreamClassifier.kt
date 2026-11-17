package com.example.data.browser

import java.net.URI
import java.net.URLEncoder
import java.util.Locale

data class VideoUrlClassification(
    val mimeType: String,
    val isHls: Boolean,
    val isDash: Boolean
)

object UrlStreamClassifier {

    private val ignoredExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "svg", "ico", "bmp",
        "css", "js", "mjs", "map", "woff", "woff2", "ttf", "otf", "eot",
        "json", "xml", "txt", "vtt", "srt", "ass", "html", "htm",
        "ts", "m4s", "aac", "mp3", "ogg", "wav"
    )

    private val directVideoExtensions = mapOf(
        "mp4" to "video/mp4",
        "m4v" to "video/mp4",
        "webm" to "video/webm",
        "mov" to "video/quicktime",
        "mkv" to "video/x-matroska",
        "flv" to "video/x-flv",
        "3gp" to "video/3gpp"
    )

    fun normalizeAddressInput(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.equals(BrowserTab.HOME_URL, ignoreCase = true)) {
            return BrowserTab.HOME_URL
        }
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }
        val looksLikeDomain = !trimmed.contains(" ") &&
            (trimmed.contains(".") || trimmed.startsWith("localhost", ignoreCase = true))
        return if (looksLikeDomain) {
            "https://$trimmed"
        } else {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            "https://www.google.com/search?q=$encoded"
        }
    }

    fun classifyVideoUrl(url: String): VideoUrlClassification? {
        val lower = url.trim().lowercase(Locale.US)
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return null
        }

        // Strip query & fragment for extension inspection
        val pathOnly = lower.substringBefore('?').substringBefore('#')
        val lastSegment = pathOnly.substringAfterLast('/')
        val ext = if ('.' in lastSegment) lastSegment.substringAfterLast('.') else ""

        if (ext in ignoredExtensions) {
            return null
        }

        if (ext == "m3u8" || lower.contains(".m3u8?") || lower.contains("format=m3u8")) {
            return VideoUrlClassification(
                mimeType = "application/vnd.apple.mpegurl",
                isHls = true,
                isDash = false
            )
        }

        if (ext == "mpd" || lower.contains(".mpd?")) {
            return VideoUrlClassification(
                mimeType = "application/dash+xml",
                isHls = false,
                isDash = true
            )
        }

        val directMime = directVideoExtensions[ext]
        if (directMime != null) {
            return VideoUrlClassification(
                mimeType = directMime,
                isHls = false,
                isDash = false
            )
        }

        // Catch CDN URLs with explicit video mime query hints
        if (lower.contains("mime=video%2fmp4") ||
            lower.contains("mime=video/mp4") ||
            lower.contains("content_type=video/mp4")
        ) {
            return VideoUrlClassification(
                mimeType = "video/mp4",
                isHls = false,
                isDash = false
            )
        }

        return null
    }

    fun extractDomain(url: String): String {
        if (url == BrowserTab.HOME_URL || url.isBlank()) return "Start Page"
        return runCatching {
            val host = URI(url).host ?: return url
            host.removePrefix("www.")
        }.getOrDefault(url)
    }

    fun sanitizeFileName(raw: String): String {
        val cleaned = raw
            .replace(Regex("[^A-Za-z0-9._\\- ]"), "_")
            .trim()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(64)
        val base = cleaned.ifBlank { "flow_video_${System.currentTimeMillis()}" }
        return if (base.endsWith(".mp4", ignoreCase = true)) base else "$base.mp4"
    }
}
