package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_videos")
data class DownloadedVideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val sourcePageUrl: String,
    val mediaUrl: String,
    val fileName: String,
    val contentUri: String,
    val filePath: String,
    val resolutionLabel: String,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val createdAtMillis: Long
)
