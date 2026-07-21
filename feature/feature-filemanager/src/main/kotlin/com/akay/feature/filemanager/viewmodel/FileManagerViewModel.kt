package com.akay.feature.filemanager.viewmodel

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class FileManagerUiState(
    val currentPath: String = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
    val files: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

@HiltViewModel
class FileManagerViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FileManagerUiState())
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

    init {
        loadFiles()
    }

    private fun loadFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val directory = File(_uiState.value.currentPath)
                if (directory.exists() && directory.isDirectory) {
                    val files = directory.listFiles()?.map { file ->
                        FileItem(
                            name = file.name,
                            path = file.absolutePath,
                            isDirectory = file.isDirectory,
                            size = if (file.isFile) file.length() else 0,
                            lastModified = file.lastModified()
                        )
                    }?.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenBy { it.name }) ?: emptyList()

                    _uiState.value = _uiState.value.copy(
                        files = files,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Directory not found",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    isLoading = false
                )
            }
        }
    }

    fun navigateTo(path: String) {
        val file = File(path)
        if (file.isDirectory) {
            _uiState.value = _uiState.value.copy(currentPath = path)
            loadFiles()
        }
    }

    fun navigateUp() {
        val currentDir = File(_uiState.value.currentPath)
        val parentDir = currentDir.parentFile
        if (parentDir != null && parentDir.exists()) {
            navigateTo(parentDir.absolutePath)
        }
    }

    fun deleteFile(path: String) {
        viewModelScope.launch {
            try {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                    loadFiles()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
