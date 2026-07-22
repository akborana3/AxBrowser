package com.akay.feature.downloads.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.akay.feature.downloads.viewmodel.DownloadItem
import com.akay.feature.downloads.viewmodel.DownloadViewModel
import com.akay.feature.downloads.viewmodel.ItemStatus

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DownloadManagerScreen(
    onBack: () -> Unit,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val tabs = listOf("Active (${state.active.size})", "Completed", "Failed")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isSettingUpYtDlp) {
                YtDlpSetupBanner(progress = state.ytDlpSetupProgress)
            }

            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { },
                        text = { Text(title, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            HorizontalPager(state = pagerState) { page ->
                val list = when (page) {
                    0 -> state.active
                    1 -> state.completed
                    else -> state.failed
                }
                if (list.isEmpty()) {
                    EmptyDownloadsPlaceholder(page)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(list, key = { it.id }) { item ->
                            DownloadCard(
                                item = item,
                                onPause = { viewModel.pause(it) },
                                onResume = { viewModel.resume(it) },
                                onCancel = { viewModel.cancel(it) },
                                onRetry = { viewModel.retry(it) },
                                onDelete = { viewModel.delete(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadCard(
    item: DownloadItem,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when {
                    item.filename.endsWith(".mp4") || item.filename.endsWith(".mkv") -> Icons.Default.Download
                    item.filename.endsWith(".mp3") || item.filename.endsWith(".m4a") -> Icons.Default.AudioFile
                    else -> Icons.Default.Download
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.filename,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when (item.status) {
                            ItemStatus.RUNNING -> "${item.speedStr} - ${item.totalStr}"
                            ItemStatus.PAUSED -> "Paused"
                            ItemStatus.COMPLETED -> "Completed - ${item.totalStr}"
                            ItemStatus.FAILED -> "Failed: ${item.errorMsg ?: "Unknown"}"
                            ItemStatus.QUEUED -> "Waiting..."
                            ItemStatus.CANCELLED -> "Cancelled"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (item.status) {
                            ItemStatus.FAILED, ItemStatus.CANCELLED -> MaterialTheme.colorScheme.error
                            ItemStatus.COMPLETED -> Color(0xFF4CAF50)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                StatusBadge(status = item.status)
            }

            if (item.status == ItemStatus.RUNNING || item.status == ItemStatus.PAUSED) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { item.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small),
                    color = if (item.status == ItemStatus.PAUSED) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${"%.1f".format(item.progress)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                when (item.status) {
                    ItemStatus.RUNNING -> {
                        IconButton(onClick = { onPause(item.id) }) { Icon(Icons.Default.Pause, "Pause") }
                        IconButton(onClick = { onCancel(item.id) }) { Icon(Icons.Default.Cancel, "Cancel") }
                    }
                    ItemStatus.PAUSED -> {
                        IconButton(onClick = { onResume(item.id) }) { Icon(Icons.Default.PlayArrow, "Resume") }
                        IconButton(onClick = { onCancel(item.id) }) { Icon(Icons.Default.Cancel, "Cancel") }
                    }
                    ItemStatus.FAILED -> {
                        TextButton(onClick = { onRetry(item.id) }) { Text("Retry") }
                        IconButton(onClick = { onDelete(item.id) }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                    ItemStatus.COMPLETED -> {
                        IconButton(onClick = { onDelete(item.id) }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
fun YtDlpSetupBanner(progress: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Setting up download engine... $progress%", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun StatusBadge(status: ItemStatus) {
    val (color, label) = when (status) {
        ItemStatus.RUNNING -> Pair(Color(0xFF6C63FF), "DL")
        ItemStatus.PAUSED -> Pair(Color(0xFFFF9800), "||")
        ItemStatus.COMPLETED -> Pair(Color(0xFF4CAF50), "OK")
        ItemStatus.FAILED -> Pair(Color(0xFFFF5252), "!")
        ItemStatus.QUEUED -> Pair(Color(0xFF9E9E9E), "...")
        ItemStatus.CANCELLED -> Pair(Color(0xFF757575), "X")
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color, fontSize = 12.sp)
    }
}

@Composable
fun EmptyDownloadsPlaceholder(page: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = when (page) {
                    0 -> Icons.Default.Download; 1 -> Icons.Default.CheckCircle; else -> Icons.Default.Warning
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = when (page) {
                    0 -> "No active downloads"; 1 -> "No completed downloads"; else -> "No failed downloads"
                },
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}
