package com.akay.core.testing

import com.akay.core.domain.model.Download
import com.akay.core.domain.model.DownloadStatus
import com.akay.core.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FakeDownloadRepository : DownloadRepository {

    private val downloads = MutableStateFlow<List<Download>>(emptyList())

    override fun getAllDownloads(): Flow<List<Download>> = downloads

    override fun getDownloadsByStatus(status: DownloadStatus): Flow<List<Download>> {
        return flowOf(downloads.value.filter { it.status == status })
    }

    override suspend fun getDownload(id: String): Download? {
        return downloads.value.find { it.id == id }
    }

    override suspend fun createDownload(download: Download): String {
        downloads.value = downloads.value + download
        return download.id
    }

    override suspend fun updateDownload(download: Download) {
        downloads.value = downloads.value.map {
            if (it.id == download.id) download else it
        }
    }

    override suspend fun updateDownloadStatus(id: String, status: DownloadStatus) {
        downloads.value = downloads.value.map {
            if (it.id == id) it.copy(status = status) else it
        }
    }

    override suspend fun updateDownloadProgress(id: String, downloadedBytes: Long, speedBps: Long) {
        downloads.value = downloads.value.map {
            if (it.id == id) it.copy(downloadedBytes = downloadedBytes, speedBps = speedBps) else it
        }
    }

    override suspend fun deleteDownload(id: String) {
        downloads.value = downloads.value.filter { it.id != id }
    }

    override suspend fun deleteCompletedDownloads() {
        downloads.value = downloads.value.filter { it.status != DownloadStatus.COMPLETED }
    }

    override suspend fun getActiveDownloadCount(): Int {
        return downloads.value.count { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED }
    }
}
