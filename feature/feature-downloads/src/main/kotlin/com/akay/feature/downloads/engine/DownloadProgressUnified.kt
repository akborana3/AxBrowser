package com.akay.feature.downloads.engine

sealed class DownloadProgressUnified {
    data class Running(
        val percent: Float,
        val speedStr: String,
        val totalBytesStr: String
    ) : DownloadProgressUnified()
    data object Completed : DownloadProgressUnified()
    data class Failed(val reason: String) : DownloadProgressUnified()
}
