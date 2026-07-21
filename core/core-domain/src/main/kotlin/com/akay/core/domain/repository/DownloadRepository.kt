package com.akay.core.domain.repository

import com.akay.core.domain.model.Download
import com.akay.core.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun getAllDownloads(): Flow<List<Download>>
    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<Download>>
    suspend fun getDownload(id: String): Download?
    suspend fun createDownload(download: Download): String
    suspend fun updateDownload(download: Download)
    suspend fun updateDownloadStatus(id: String, status: DownloadStatus)
    suspend fun updateDownloadProgress(id: String, downloadedBytes: Long, speedBps: Long)
    suspend fun deleteDownload(id: String)
    suspend fun deleteCompletedDownloads()
    suspend fun getActiveDownloadCount(): Int
}
