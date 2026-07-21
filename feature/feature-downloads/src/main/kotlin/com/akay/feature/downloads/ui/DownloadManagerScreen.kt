package com.akay.feature.downloads.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.akay.core.domain.model.Download
import com.akay.core.domain.model.DownloadStatus
import com.akay.core.ui.theme.Primary
import com.akay.feature.downloads.viewmodel.DownloadTab
import com.akay.feature.downloads.viewmodel.DownloadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerScreen(
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedTab == DownloadTab.ACTIVE,
                    onClick = { viewModel.selectTab(DownloadTab.ACTIVE) },
                    label = { Text("Active (${uiState.activeDownloads.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary.copy(alpha = 0.2f)
                    )
                )
                FilterChip(
                    selected = uiState.selectedTab == DownloadTab.COMPLETED,
                    onClick = { viewModel.selectTab(DownloadTab.COMPLETED) },
                    label = { Text("Completed (${uiState.completedDownloads.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary.copy(alpha = 0.2f)
                    )
                )
                FilterChip(
                    selected = uiState.selectedTab == DownloadTab.FAILED,
                    onClick = { viewModel.selectTab(DownloadTab.FAILED) },
                    label = { Text("Failed (${uiState.failedDownloads.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary.copy(alpha = 0.2f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val downloads = when (uiState.selectedTab) {
                DownloadTab.ACTIVE -> uiState.activeDownloads
                DownloadTab.COMPLETED -> uiState.completedDownloads
                DownloadTab.FAILED -> uiState.failedDownloads
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                items(downloads) { download ->
                    DownloadCard(
                        download = download,
                        onPause = { viewModel.pauseDownload(download.id) },
                        onResume = { viewModel.resumeDownload(download.id) },
                        onCancel = { viewModel.cancelDownload(download.id) },
                        onRetry = { viewModel.retryDownload(download.id) },
                        onDelete = { viewModel.deleteDownload(download.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadCard(
    download: Download,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.filename,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1
                )
                Text(
                    text = formatBytes(download.downloadedBytes) + " / " + formatBytes(download.totalBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (download.speedBps > 0) {
                    Text(
                        text = formatSpeed(download.speedBps),
                        style = MaterialTheme.typography.bodySmall,
                        color = Primary
                    )
                }
            }

            Row {
                when (download.status) {
                    DownloadStatus.RUNNING -> {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, "Pause")
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, "Resume")
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            "Completed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    DownloadStatus.FAILED -> {
                        IconButton(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, "Retry", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    DownloadStatus.QUEUED -> {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Cancel, "Cancel")
                        }
                    }
                }
            }
        }

        if (download.status == DownloadStatus.RUNNING || download.status == DownloadStatus.PAUSED) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { download.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = Primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

private fun formatSpeed(bytesPerSecond: Long): String {
    return when {
        bytesPerSecond < 1024 -> "${bytesPerSecond} B/s"
        bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
        else -> "${bytesPerSecond / (1024 * 1024)} MB/s"
    }
}
