package com.example.data.recorder

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

data class SavedMp4File(
    val fileName: String,
    val contentUri: String,
    val filePath: String,
    val fileSizeBytes: Long
)

object MediaStoreSaver {

    suspend fun saveMp4ToDevice(
        context: Context,
        tempMp4File: File,
        desiredFileName: String
    ): SavedMp4File = withContext(Dispatchers.IO) {
        val safeName = desiredFileName.let { if (it.endsWith(".mp4")) it else "$it.mp4" }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, safeName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/FlowBrowser")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri: Uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Failed to create MediaStore entry for $safeName")

            try {
                resolver.openOutputStream(itemUri)?.use { out ->
                    FileInputStream(tempMp4File).use { input ->
                        input.copyTo(out, bufferSize = 64 * 1024)
                    }
                } ?: throw IllegalStateException("Unable to open output stream for $itemUri")

                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)

                val size = tempMp4File.length()
                tempMp4File.delete()
                return@withContext SavedMp4File(
                    fileName = safeName,
                    contentUri = itemUri.toString(),
                    filePath = "",
                    fileSizeBytes = size
                )
            } catch (e: Exception) {
                runCatching { resolver.delete(itemUri, null, null) }
                throw e
            }
        } else {
            val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: File(context.filesDir, "downloads")
            val flowDir = File(moviesDir, "FlowBrowser").apply { mkdirs() }
            val destFile = File(flowDir, safeName)
            tempMp4File.copyTo(destFile, overwrite = true)
            tempMp4File.delete()
            val providerUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destFile
            )
            return@withContext SavedMp4File(
                fileName = safeName,
                contentUri = providerUri.toString(),
                filePath = destFile.absolutePath,
                fileSizeBytes = destFile.length()
            )
        }
    }
}
