package com.akay.feature.downloads.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akay.core.domain.engine.DirectDownloadEngine
import com.akay.core.domain.engine.DirectDownloadProgress
import com.akay.core.domain.engine.YtDlpEngine
import com.akay.core.domain.engine.YtDlpProgress
import com.akay.core.domain.engine.YtDlpSetup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class DownloadItem(
    val id: String,
    val url: String,
    val filename: String,
    val outputPath: String,
    val progress: Float = 0f,
    val speedStr: String = "",
    val totalStr: String = "",
    val status: ItemStatus = ItemStatus.QUEUED,
    val errorMsg: String? = null,
    val useYtDlp: Boolean = false,
    val formatId: String? = null
)

enum class ItemStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }

data class DownloadManagerState(
    val downloads: List<DownloadItem> = emptyList(),
    val ytDlpReady: Boolean = false,
    val ytDlpSetupProgress: Int = 0,
    val isSettingUpYtDlp: Boolean = false
) {
    val active get() = downloads.filter { it.status == ItemStatus.RUNNING || it.status == ItemStatus.QUEUED }
    val completed get() = downloads.filter { it.status == ItemStatus.COMPLETED }
    val failed get() = downloads.filter { it.status == ItemStatus.FAILED || it.status == ItemStatus.CANCELLED }
}

@HiltViewModel
class DownloadViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(DownloadManagerState())
    val state: StateFlow<DownloadManagerState> = _state.asStateFlow()

    private val ytDlpEngine = YtDlpEngine(application)
    private val directEngine = DirectDownloadEngine(
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.SECONDS)
            .build()
    )
    private val downloadJobs = mutableMapOf<String, Job>()
    private val downloadDir = File(application.getExternalFilesDir(null), "AxBrowser Downloads")
        .also { it.mkdirs() }

    init {
        checkYtDlp()
    }

    private fun checkYtDlp() {
        val ready = YtDlpSetup.isInstalled(getApplication())
        _state.update { it.copy(ytDlpReady = ready) }
        if (!ready) setupYtDlp()
    }

    fun setupYtDlp() {
        viewModelScope.launch {
            _state.update { it.copy(isSettingUpYtDlp = true) }
            YtDlpSetup.setup(
                context = getApplication(),
                client = OkHttpClient(),
                onProgress = { p -> _state.update { it.copy(ytDlpSetupProgress = p) } }
            ).onSuccess {
                _state.update { it.copy(ytDlpReady = true, isSettingUpYtDlp = false) }
            }.onFailure {
                _state.update { it.copy(isSettingUpYtDlp = false) }
            }
        }
    }

    fun enqueue(url: String, filename: String, useYtDlp: Boolean, formatId: String? = null): String {
        val id = UUID.randomUUID().toString()
        val outputFile = uniqueFile(downloadDir, filename)
        val item = DownloadItem(
            id = id, url = url, filename = outputFile.name,
            outputPath = outputFile.absolutePath, useYtDlp = useYtDlp, formatId = formatId
        )
        _state.update { it.copy(downloads = it.downloads + item) }
        startDownload(item)
        return id
    }

    private fun startDownload(item: DownloadItem) {
        val job = viewModelScope.launch {
            updateItem(item.id) { it.copy(status = ItemStatus.RUNNING) }
            val flow = if (item.useYtDlp && _state.value.ytDlpReady) {
                ytDlpEngine.download(item.url, item.outputPath, item.formatId)
            } else {
                directEngine.download(item.id, item.url, File(item.outputPath))
            }
            flow.collect { progress ->
                when (progress) {
                    is YtDlpProgress.Running -> updateItem(item.id) {
                        it.copy(progress = progress.percent, speedStr = progress.speedStr, totalStr = progress.totalBytesStr, status = ItemStatus.RUNNING)
                    }
                    is YtDlpProgress.Completed -> updateItem(item.id) { it.copy(progress = 100f, status = ItemStatus.COMPLETED) }
                    is YtDlpProgress.Failed -> updateItem(item.id) { it.copy(status = ItemStatus.FAILED, errorMsg = progress.reason) }
                    is DirectDownloadProgress.Running -> updateItem(item.id) {
                        it.copy(progress = progress.percent, speedStr = progress.speedStr, totalStr = progress.totalBytesStr, status = ItemStatus.RUNNING)
                    }
                    is DirectDownloadProgress.Completed -> updateItem(item.id) { it.copy(progress = 100f, status = ItemStatus.COMPLETED) }
                    is DirectDownloadProgress.Failed -> updateItem(item.id) { it.copy(status = ItemStatus.FAILED, errorMsg = progress.reason) }
                }
            }
        }
        downloadJobs[item.id] = job
    }

    fun pause(id: String) {
        val item = _state.value.downloads.find { it.id == id } ?: return
        directEngine.pause(id, (item.progress / 100f * 1_000_000L).toLong())
        downloadJobs[id]?.cancel()
        updateItem(id) { it.copy(status = ItemStatus.PAUSED) }
    }

    fun resume(id: String) {
        val item = _state.value.downloads.find { it.id == id } ?: return
        startDownload(item)
    }

    fun cancel(id: String) {
        directEngine.cancel(id)
        downloadJobs[id]?.cancel()
        updateItem(id) { it.copy(status = ItemStatus.CANCELLED) }
    }

    fun retry(id: String) {
        val item = _state.value.downloads.find { it.id == id } ?: return
        updateItem(id) { it.copy(status = ItemStatus.QUEUED, progress = 0f, errorMsg = null) }
        startDownload(item.copy(status = ItemStatus.QUEUED))
    }

    fun delete(id: String) {
        val item = _state.value.downloads.find { it.id == id } ?: return
        File(item.outputPath).delete()
        downloadJobs[id]?.cancel()
        _state.update { it.copy(downloads = it.downloads.filter { d -> d.id != id }) }
    }

    private fun updateItem(id: String, block: (DownloadItem) -> DownloadItem) {
        _state.update { state ->
            state.copy(downloads = state.downloads.map { if (it.id == id) block(it) else it })
        }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        var count = 1
        val base = name.substringBeforeLast(".")
        val ext = name.substringAfterLast(".", "")
        while (file.exists()) {
            file = File(dir, if (ext.isNotEmpty()) "$base($count).$ext" else "$base($count)")
            count++
        }
        return file
    }
}
