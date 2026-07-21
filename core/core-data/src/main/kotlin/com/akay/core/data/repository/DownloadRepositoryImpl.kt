package com.akay.core.data.repository

import com.akay.core.data.db.dao.DownloadDao
import com.akay.core.data.mapper.toDomain
import com.akay.core.data.mapper.toEntity
import com.akay.core.domain.model.Download
import com.akay.core.domain.model.DownloadStatus
import com.akay.core.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val downloadDao: DownloadDao
) : DownloadRepository {

    override fun getAllDownloads(): Flow<List<Download>> {
        return downloadDao.getAllDownloads().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getDownloadsByStatus(status: DownloadStatus): Flow<List<Download>> {
        return downloadDao.getDownloadsByStatus(status.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getDownload(id: String): Download? {
        return downloadDao.getDownload(id)?.toDomain()
    }

    override suspend fun createDownload(download: Download): String {
        downloadDao.insertDownload(download.toEntity())
        return download.id
    }

    override suspend fun updateDownload(download: Download) {
        downloadDao.updateDownload(download.toEntity())
    }

    override suspend fun updateDownloadStatus(id: String, status: DownloadStatus) {
        downloadDao.updateDownloadStatus(id, status.name)
    }

    override suspend fun updateDownloadProgress(id: String, downloadedBytes: Long, speedBps: Long) {
        downloadDao.updateDownloadProgress(id, downloadedBytes, speedBps)
    }

    override suspend fun deleteDownload(id: String) {
        downloadDao.deleteDownloadById(id)
    }

    override suspend fun deleteCompletedDownloads() {
        downloadDao.deleteCompletedDownloads()
    }

    override suspend fun getActiveDownloadCount(): Int {
        return downloadDao.getActiveDownloadCount()
    }
}
