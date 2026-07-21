package com.akay.core.domain.model

import java.util.UUID

data class Download(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val filename: String,
    val destinationPath: String,
    val mimeType: String? = null,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val errorMessage: String? = null,
    val speedBps: Long = 0,
    val enqueuedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val retryCount: Int = 0
) {
    val progress: Float
        get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f

    val isComplete: Boolean
        get() = status == DownloadStatus.COMPLETED

    val isActive: Boolean
        get() = status == DownloadStatus.RUNNING || status == DownloadStatus.QUEUED

    val isPaused: Boolean
        get() = status == DownloadStatus.PAUSED

    val hasFailed: Boolean
        get() = status == DownloadStatus.FAILED
}

enum class DownloadStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED
}
