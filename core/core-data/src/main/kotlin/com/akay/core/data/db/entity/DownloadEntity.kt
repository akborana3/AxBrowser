package com.akay.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val url: String,
    val filename: String,
    @ColumnInfo(name = "destination_path") val destinationPath: String,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    @ColumnInfo(name = "total_bytes") val totalBytes: Long = 0,
    @ColumnInfo(name = "downloaded_bytes") val downloadedBytes: Long = 0,
    val status: String,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
    @ColumnInfo(name = "speed_bps") val speedBps: Long = 0,
    @ColumnInfo(name = "enqueued_at") val enqueuedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0
)
