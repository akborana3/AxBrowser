package com.akay.feature.downloads.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akay.core.domain.model.Download
import com.akay.core.domain.model.DownloadStatus
import com.akay.core.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsUiState(
    val downloads: List<Download> = emptyList(),
    val activeDownloads: List<Download> = emptyList(),
    val completedDownloads: List<Download> = emptyList(),
    val failedDownloads: List<Download> = emptyList(),
    val isLoading: Boolean = false,
    val selectedTab: DownloadTab = DownloadTab.ACTIVE
)

enum class DownloadTab {
    ACTIVE, COMPLETED, FAILED
}

@HiltViewModel
class DownloadViewModel @Inject constructor(
    application: Application,
    private val downloadRepository: DownloadRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        loadDownloads()
    }

    private fun loadDownloads() {
        viewModelScope.launch {
            downloadRepository.getAllDownloads().collect { downloads ->
                _uiState.value = _uiState.value.copy(
                    downloads = downloads,
                    activeDownloads = downloads.filter { it.isActive },
                    completedDownloads = downloads.filter { it.isComplete },
                    failedDownloads = downloads.filter { it.hasFailed }
                )
            }
        }
    }

    fun selectTab(tab: DownloadTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun pauseDownload(downloadId: String) {
        viewModelScope.launch {
            downloadRepository.updateDownloadStatus(downloadId, DownloadStatus.PAUSED)
        }
    }

    fun resumeDownload(downloadId: String) {
        viewModelScope.launch {
            downloadRepository.updateDownloadStatus(downloadId, DownloadStatus.QUEUED)
        }
    }

    fun cancelDownload(downloadId: String) {
        viewModelScope.launch {
            downloadRepository.deleteDownload(downloadId)
        }
    }

    fun retryDownload(downloadId: String) {
        viewModelScope.launch {
            downloadRepository.updateDownloadStatus(downloadId, DownloadStatus.QUEUED)
        }
    }

    fun deleteDownload(downloadId: String) {
        viewModelScope.launch {
            downloadRepository.deleteDownload(downloadId)
        }
    }

    fun clearCompletedDownloads() {
        viewModelScope.launch {
            downloadRepository.deleteCompletedDownloads()
        }
    }
}
