package com.example.data.local

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class DownloadRepository(
    private val context: Context,
    private val dao: DownloadedVideoDao
) {
    fun observeAllDownloads(): Flow<List<DownloadedVideoEntity>> = dao.observeAll()

    fun searchDownloads(query: String): Flow<List<DownloadedVideoEntity>> =
        if (query.isBlank()) dao.observeAll() else dao.search(query.trim())

    fun observeDownload(id: Long): Flow<DownloadedVideoEntity?> = dao.observeById(id)

    suspend fun saveDownload(entity: DownloadedVideoEntity): Long = dao.insert(entity)

    suspend fun deleteDownload(entity: DownloadedVideoEntity) {
        withContext(Dispatchers.IO) {
            if (entity.filePath.isNotBlank()) {
                runCatching { File(entity.filePath).delete() }
            }
            if (entity.contentUri.startsWith("content://")) {
                runCatching {
                    context.contentResolver.delete(Uri.parse(entity.contentUri), null, null)
                }
            }
        }
        dao.deleteById(entity.id)
    }
}
