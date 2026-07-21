package com.akay.core.domain.engine

sealed class DownloadProgress {
    data class Running(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedBps: Long
    ) : DownloadProgress()

    data class Paused(
        val bytesDownloaded: Long
    ) : DownloadProgress()

    data object Completed : DownloadProgress()

    data class Failed(
        val reason: String
    ) : DownloadProgress()
}
