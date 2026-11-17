package com.example.data.ffmpeg

import com.example.data.browser.VideoResolution

object FfmpegCommandBuilder {

    fun buildHeaderString(referer: String, cookies: String): String {
        val sb = StringBuilder()
        if (referer.isNotBlank()) {
            sb.append("Referer: ").append(referer).append("\r\n")
        }
        if (cookies.isNotBlank()) {
            sb.append("Cookie: ").append(cookies).append("\r\n")
        }
        return sb.toString()
    }

    fun buildRemuxArgs(
        input: String,
        outputPath: String,
        isRemoteStream: Boolean,
        isHls: Boolean,
        userAgent: String,
        referer: String,
        cookies: String
    ): Array<String> {
        val args = mutableListOf("-y")
        if (isRemoteStream) {
            appendNetworkArgs(args, userAgent, referer, cookies)
        }
        args.addAll(listOf("-i", input))
        args.addAll(listOf("-c", "copy"))
        if (isHls) {
            args.addAll(listOf("-bsf:a", "aac_adtstoasc"))
        }
        args.addAll(listOf("-movflags", "+faststart", outputPath))
        return args.toTypedArray()
    }

    fun buildTranscodeArgs(
        input: String,
        outputPath: String,
        resolution: VideoResolution,
        isRemoteStream: Boolean,
        userAgent: String,
        referer: String,
        cookies: String,
        useMpeg4Fallback: Boolean = false
    ): Array<String> {
        val args = mutableListOf("-y")
        if (isRemoteStream) {
            appendNetworkArgs(args, userAgent, referer, cookies)
        }
        args.addAll(listOf("-i", input))

        val target = resolution.targetHeight
        if (target != null) {
            // Preserve portrait vs landscape aspect ratio while enforcing even dimensions
            val scaleFilter = "scale='if(gte(iw,ih),-2,$target)':'if(gte(iw,ih),$target,-2)'"
            args.addAll(listOf("-vf", scaleFilter))
        }

        if (useMpeg4Fallback) {
            args.addAll(listOf("-c:v", "mpeg4", "-q:v", "4"))
        } else {
            args.addAll(
                listOf(
                    "-c:v", "libx264",
                    "-preset", "veryfast",
                    "-crf", "23",
                    "-pix_fmt", "yuv420p"
                )
            )
        }

        args.addAll(
            listOf(
                "-c:a", "aac",
                "-b:a", "128k",
                "-ac", "2",
                "-movflags", "+faststart",
                outputPath
            )
        )
        return args.toTypedArray()
    }

    private fun appendNetworkArgs(
        args: MutableList<String>,
        userAgent: String,
        referer: String,
        cookies: String
    ) {
        if (userAgent.isNotBlank()) {
            args.addAll(listOf("-user_agent", userAgent))
        }
        val headerStr = buildHeaderString(referer, cookies)
        if (headerStr.isNotBlank()) {
            args.addAll(listOf("-headers", headerStr))
        }
        args.addAll(listOf("-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "4"))
    }
}
