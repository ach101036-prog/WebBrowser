package com.example.data.ffmpeg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object OkHttpStreamDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun downloadToTempFile(
        url: String,
        targetFile: File,
        userAgent: String,
        referer: String,
        cookies: String,
        isCancelled: AtomicBoolean,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder().url(url)
        if (userAgent.isNotBlank()) {
            reqBuilder.header("User-Agent", userAgent)
        }
        if (referer.isNotBlank()) {
            reqBuilder.header("Referer", referer)
        }
        if (cookies.isNotBlank()) {
            reqBuilder.header("Cookie", cookies)
        }

        client.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} while downloading media")
            }
            val body = response.body ?: throw IOException("Empty media response body")
            val totalBytes = body.contentLength()
            var downloaded = 0L
            val buffer = ByteArray(32 * 1024)

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    while (true) {
                        if (isCancelled.get()) {
                            return@withContext false
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, totalBytes)
                    }
                    output.flush()
                }
            }
            true
        }
    }
}
