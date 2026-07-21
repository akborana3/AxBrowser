package com.akay.core.domain.engine

interface DownloadEngine {
    fun download(request: DownloadRequest): kotlinx.coroutines.flow.Flow<DownloadProgress>
    suspend fun pause(downloadId: String)
    suspend fun resume(downloadId: String)
    suspend fun cancel(downloadId: String)
    suspend fun retry(downloadId: String)
}
