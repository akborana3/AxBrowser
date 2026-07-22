# AGENT TASK — AxBrowser: Add Download Manager, Dev Console & UI Overhaul

---

## CONTEXT — What Already Exists

You have a working Android browser built with:
- Kotlin + Jetpack Compose
- WebView with basic WebViewClient
- Simple tab switching
- Basic URL bar

**What is MISSING and what you must BUILD now:**

| Feature | Status |
|---|---|
| Download Manager (yt-dlp + direct) | ❌ Missing |
| Media Detection (URL intercept + DOM scan) | ❌ Missing |
| Dev Console (Network Inspector + Eruda) | ❌ Missing |
| Premium UI (Glassmorphism + Material 3) | ❌ Missing |
| Fast Background Downloads + Notifications | ❌ Missing |

---

## RULES BEFORE YOU WRITE ANY CODE

1. **Do NOT rewrite what already works.** Modify existing files. Add new files.
2. **All async work uses Kotlin Coroutines + Flow.** No threads, no callbacks.
3. **All UI is Jetpack Compose.** No XML.
4. **yt-dlp is the primary download strategy** for video sites. OkHttp is for direct file URLs.
5. **The Dev Console is a native Compose panel**, not a webpage. Eruda.js handles JS-side only.
6. **Write every file completely.** No `// TODO` stubs except where marked.

---

## PART 1 — DOWNLOAD MANAGER

### 1A — yt-dlp Binary Setup

**Create file:** `app/src/main/kotlin/com/akay/axbrowser/downloader/ytdlp/YtDlpSetup.kt`

```kotlin
package com.akay.axbrowser.downloader.ytdlp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object YtDlpSetup {

    // ARM64 build of yt-dlp (works on 99% of Android phones)
    private const val YTDLP_DOWNLOAD_URL =
        "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_android"

    fun getBinaryFile(context: Context): File =
        File(context.filesDir, "bin/yt-dlp")

    fun isInstalled(context: Context): Boolean =
        getBinaryFile(context).let { it.exists() && it.canExecute() }

    /**
     * Call this once on first app launch.
     * Downloads the yt-dlp binary to internal storage and makes it executable.
     * Shows progress via [onProgress] (0–100).
     */
    suspend fun setup(
        context: Context,
        client: OkHttpClient,
        onProgress: (Int) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val binDir = File(context.filesDir, "bin").also { it.mkdirs() }
            val binFile = File(binDir, "yt-dlp")

            val request = Request.Builder().url(YTDLP_DOWNLOAD_URL).build()
            val response = client.newCall(request).execute()
            val body = response.body ?: error("Empty response body")
            val totalBytes = body.contentLength()

            binFile.outputStream().use { out ->
                var downloaded = 0L
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytes: Int
                    while (input.read(buffer).also { bytes = it } != -1) {
                        out.write(buffer, 0, bytes)
                        downloaded += bytes
                        if (totalBytes > 0) {
                            onProgress(((downloaded * 100) / totalBytes).toInt())
                        }
                    }
                }
            }

            // Make executable
            Runtime.getRuntime().exec(arrayOf("chmod", "+x", binFile.absolutePath)).waitFor()
            check(binFile.canExecute()) { "Binary not executable after chmod" }
        }
    }
}
```

---

### 1B — Media Detection (URL Intercept + JavaScript DOM Scanner)

**Modify your existing `WebViewClient`** to detect media in two ways:

**1. URL pattern matching** — already partially there, expand it:

In your existing WebViewClient's `shouldInterceptRequest`, add or replace the media detection block with this:

```kotlin
// Inside shouldInterceptRequest — paste this block
private val videoExtensions = setOf(".mp4", ".webm", ".mkv", ".avi", ".mov", ".m3u8", ".mpd", ".ts", ".flv")
private val audioExtensions = setOf(".mp3", ".m4a", ".aac", ".ogg", ".wav", ".flac", ".opus")
private val mediaContentTypes = setOf("video/", "audio/", "application/x-mpegurl", "application/dash+xml")

// Call this inside shouldInterceptRequest:
fun detectMedia(url: String, headers: Map<String, String>): DetectedMedia? {
    val lowerUrl = url.lowercase()
    val accept = headers["Accept"] ?: ""
    val isMediaByUrl = videoExtensions.any { lowerUrl.contains(it) }
        || audioExtensions.any { lowerUrl.contains(it) }
    val isMediaByHeader = mediaContentTypes.any { accept.contains(it) }
    if (!isMediaByUrl && !isMediaByHeader) return null

    val filename = lowerUrl.substringAfterLast("/").substringBefore("?")
        .ifBlank { "media_${System.currentTimeMillis()}" }
    val mime = when {
        lowerUrl.contains(".mp4") -> "video/mp4"
        lowerUrl.contains(".m3u8") -> "application/x-mpegurl"
        lowerUrl.contains(".mp3") -> "audio/mpeg"
        lowerUrl.contains(".webm") -> "video/webm"
        else -> null
    }
    return DetectedMedia(
        id = java.util.UUID.randomUUID().toString(),
        pageUrl = currentPageUrl,   // track this as onPageStarted fires
        mediaUrl = url,
        filename = filename,
        mimeType = mime,
        detectionMethod = DetectionMethod.URL_INTERCEPT
    )
}
```

**2. JavaScript DOM scanner** — inject after every page load.

**Create file:** `app/src/main/assets/js/media_scanner.js`

```javascript
(function() {
    var found = [];
    
    // Scan <video> and <audio> elements
    document.querySelectorAll('video, audio').forEach(function(el) {
        var src = el.src || el.currentSrc;
        if (src && src.startsWith('http')) found.push({url: src, type: el.tagName.toLowerCase()});
        el.querySelectorAll('source').forEach(function(s) {
            if (s.src) found.push({url: s.src, type: 'source', quality: s.getAttribute('label') || ''});
        });
    });
    
    // Scan <a> tags for downloadable media links
    document.querySelectorAll('a[href]').forEach(function(a) {
        var href = a.href;
        if (/\.(mp4|webm|mp3|m4a|mkv|avi|mov|flac|wav)(\?|$)/i.test(href)) {
            found.push({url: href, type: 'link', title: a.textContent.trim()});
        }
    });
    
    // Return as JSON string for Android to parse
    return JSON.stringify(found);
})();
```

**In your WebViewClient's `onPageFinished`**, inject this script:

```kotlin
override fun onPageFinished(view: WebView, url: String) {
    // Load the JS file from assets
    val js = view.context.assets.open("js/media_scanner.js").bufferedReader().readText()
    view.evaluateJavascript(js) { result ->
        if (!result.isNullOrEmpty() && result != "null") {
            // Parse JSON result and report back
            onDomMediaFound(result)
        }
    }
    
    // Also inject Eruda if dev mode is enabled (see Part 2)
    if (erudaEnabled) injectEruda(view)
}
```

---

### 1C — yt-dlp Engine

**Create file:** `app/src/main/kotlin/com/akay/axbrowser/downloader/engine/YtDlpEngine.kt`

```kotlin
package com.akay.axbrowser.downloader.engine

import android.content.Context
import com.akay.axbrowser.downloader.ytdlp.YtDlpSetup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.File

/**
 * Downloads video using the yt-dlp binary running as a local process.
 * Handles YouTube, Twitter, Instagram, Reddit, TikTok, Vimeo, and 1000+ sites.
 */
class YtDlpEngine(private val context: Context) {

    /**
     * Get available formats for a URL.
     * Returns list of [MediaFormat] the user can choose from.
     */
    suspend fun getFormats(url: String): List<MediaFormat> {
        val binary = YtDlpSetup.getBinaryFile(context).absolutePath
        val process = ProcessBuilder(binary, "-F", "--no-playlist", url)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return parseFormats(output)
    }

    /**
     * Download a URL with yt-dlp.
     * [formatId] — yt-dlp format id (e.g. "137+140" for 1080p video+audio, null = best)
     * Emits [DownloadProgress] updates until complete or failed.
     */
    fun download(
        url: String,
        outputPath: String,
        formatId: String? = null,
        onProgress: (DownloadProgress) -> Unit
    ): Flow<DownloadProgress> = flow {
        val binary = YtDlpSetup.getBinaryFile(context).absolutePath
        val cmd = mutableListOf(
            binary,
            "--no-playlist",
            "--newline",                         // one line per progress update
            "--progress-template", "%(progress)j", // JSON progress
            "-o", outputPath
        )
        if (formatId != null) cmd.addAll(listOf("-f", formatId))
        cmd.add(url)

        val process = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()

        try {
            process.inputStream.bufferedReader().forEachLine { line ->
                if (!isActive) {
                    process.destroy()
                    return@forEachLine
                }
                // yt-dlp progress line format: [download]  45.3% of 23.40MiB at 2.50MiB/s
                val progress = parseProgressLine(line)
                if (progress != null) emit(progress)
            }
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                emit(DownloadProgress.Completed)
            } else {
                emit(DownloadProgress.Failed("yt-dlp exited with code $exitCode"))
            }
        } finally {
            process.destroyForcibly()
        }
    }.flowOn(Dispatchers.IO)

    private fun parseProgressLine(line: String): DownloadProgress? {
        // Match: [download]  45.3% of 23.40MiB at 2.50MiB/s ETA 00:10
        val regex = Regex("""\[download\]\s+([\d.]+)%\s+of\s+([\d.]+\w+)\s+at\s+([\d.]+\w+/s)""")
        val match = regex.find(line) ?: return null
        val percent = match.groupValues[1].toFloatOrNull() ?: return null
        val totalStr = match.groupValues[2]
        val speedStr = match.groupValues[3]
        return DownloadProgress.Running(
            percent = percent,
            totalBytesStr = totalStr,
            speedStr = speedStr
        )
    }

    private fun parseFormats(output: String): List<MediaFormat> {
        val formats = mutableListOf<MediaFormat>()
        // Parse yt-dlp -F output table
        // Format: ID  EXT  RESOLUTION  FPS  FILESIZE  TBR  PROTO  ...
        output.lines().forEach { line ->
            if (line.startsWith("--") || line.isBlank()) return@forEach
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size >= 3 && parts[0].matches(Regex("\\d+[a-z]?"))) {
                formats.add(MediaFormat(
                    id = parts[0],
                    ext = parts.getOrElse(1) { "" },
                    resolution = parts.getOrElse(2) { "" },
                    description = line.trim()
                ))
            }
        }
        return formats
    }
}

data class MediaFormat(
    val id: String,
    val ext: String,
    val resolution: String,
    val description: String
)

sealed class DownloadProgress {
    data class Running(val percent: Float, val totalBytesStr: String, val speedStr: String) : DownloadProgress()
    object Completed : DownloadProgress()
    data class Failed(val reason: String) : DownloadProgress()
}
```

---

### 1D — Direct Download Engine (OkHttp — for plain file URLs)

**Create file:** `app/src/main/kotlin/com/akay/axbrowser/downloader/engine/DirectDownloadEngine.kt`

```kotlin
package com.akay.axbrowser.downloader.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile

/**
 * Downloads direct media URLs using OkHttp with Range-based pause/resume support.
 * Used for: direct .mp4, .mp3, .m3u8 segment files, and any URL yt-dlp can't handle.
 */
class DirectDownloadEngine(private val client: OkHttpClient) {

    // Holds byte offsets for paused downloads: downloadId -> bytesDownloaded
    private val pausedOffsets = mutableMapOf<String, Long>()
    private val activeJobs = mutableMapOf<String, Boolean>() // id -> cancelled

    fun download(
        downloadId: String,
        url: String,
        destFile: File,
        extraHeaders: Map<String, String> = emptyMap()
    ): Flow<DownloadProgress> = flow {
        val resumeFrom = pausedOffsets.remove(downloadId) ?: 0L
        activeJobs[downloadId] = false

        val requestBuilder = Request.Builder().url(url)
        extraHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        if (resumeFrom > 0) requestBuilder.addHeader("Range", "bytes=$resumeFrom-")

        val response = client.newCall(requestBuilder.build()).execute()
        val body = response.body ?: run {
            emit(DownloadProgress.Failed("Empty response")); return@flow
        }

        val contentLength = body.contentLength()
        val totalBytes = if (resumeFrom > 0 && contentLength > 0) contentLength + resumeFrom else contentLength
        var downloadedBytes = resumeFrom
        val startTime = System.currentTimeMillis()
        var lastSpeedCheck = startTime
        var bytesAtLastCheck = downloadedBytes

        // Open file for append if resuming, write from start if fresh
        val raf = RandomAccessFile(destFile, "rw")
        if (resumeFrom > 0) raf.seek(resumeFrom) else raf.setLength(0)

        try {
            body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                var bytes: Int
                while (input.read(buffer).also { bytes = it } != -1) {
                    if (activeJobs[downloadId] == true) {
                        // Cancelled
                        raf.close()
                        emit(DownloadProgress.Failed("Cancelled")); return@flow
                    }
                    raf.write(buffer, 0, bytes)
                    downloadedBytes += bytes

                    val now = System.currentTimeMillis()
                    if (now - lastSpeedCheck >= 500) {
                        val speed = ((downloadedBytes - bytesAtLastCheck) * 1000L) / (now - lastSpeedCheck)
                        val percent = if (totalBytes > 0) (downloadedBytes * 100f) / totalBytes else 0f
                        emit(DownloadProgress.Running(
                            percent = percent,
                            totalBytesStr = formatSize(totalBytes),
                            speedStr = "${formatSize(speed)}/s"
                        ))
                        bytesAtLastCheck = downloadedBytes
                        lastSpeedCheck = now
                    }
                }
            }
            raf.close()
            emit(DownloadProgress.Completed)
        } catch (e: Exception) {
            raf.close()
            emit(DownloadProgress.Failed(e.message ?: "Unknown error"))
        } finally {
            activeJobs.remove(downloadId)
        }
    }.flowOn(Dispatchers.IO)

    fun pause(downloadId: String, bytesDownloaded: Long) {
        pausedOffsets[downloadId] = bytesDownloaded
    }

    fun cancel(downloadId: String) {
        activeJobs[downloadId] = true
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024f)}KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024f * 1024f))}MB"
        else -> "${"%.2f".format(bytes / (1024f * 1024f * 1024f))}GB"
    }
}
```

---

### 1E — Download Manager ViewModel

**Create file:** `app/src/main/kotlin/com/akay/axbrowser/downloader/DownloadManagerViewModel.kt`

```kotlin
package com.akay.axbrowser.downloader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akay.axbrowser.downloader.engine.*
import com.akay.axbrowser.downloader.ytdlp.YtDlpSetup
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
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
class DownloadManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadManagerState())
    val state: StateFlow<DownloadManagerState> = _state.asStateFlow()

    private val ytDlpEngine = YtDlpEngine(context)
    private val directEngine = DirectDownloadEngine(
        okhttp3.OkHttpClient.Builder()
            .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    )
    private val downloadJobs = mutableMapOf<String, Job>()
    private val downloadDir = File(context.getExternalFilesDir(null), "AxBrowser Downloads")
        .also { it.mkdirs() }

    init {
        checkYtDlp()
    }

    private fun checkYtDlp() {
        val ready = YtDlpSetup.isInstalled(context)
        _state.update { it.copy(ytDlpReady = ready) }
        if (!ready) setupYtDlp()
    }

    fun setupYtDlp() {
        viewModelScope.launch {
            _state.update { it.copy(isSettingUpYtDlp = true) }
            YtDlpSetup.setup(
                context = context,
                client = okhttp3.OkHttpClient(),
                onProgress = { p -> _state.update { it.copy(ytDlpSetupProgress = p) } }
            ).onSuccess {
                _state.update { it.copy(ytDlpReady = true, isSettingUpYtDlp = false) }
            }.onFailure { e ->
                _state.update { it.copy(isSettingUpYtDlp = false) }
            }
        }
    }

    /**
     * Enqueue a download.
     * [useYtDlp] = true for video sites (YouTube, Twitter, etc.)
     * [useYtDlp] = false for direct file URLs (.mp4, .mp3, etc.)
     */
    fun enqueue(
        url: String,
        filename: String,
        useYtDlp: Boolean,
        formatId: String? = null
    ): String {
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
                ytDlpEngine.download(item.url, item.outputPath, item.formatId) {}
            } else {
                directEngine.download(item.id, item.url, File(item.outputPath))
            }
            flow.collect { progress ->
                when (progress) {
                    is DownloadProgress.Running -> updateItem(item.id) {
                        it.copy(progress = progress.percent, speedStr = progress.speedStr, totalStr = progress.totalBytesStr, status = ItemStatus.RUNNING)
                    }
                    DownloadProgress.Completed -> updateItem(item.id) { it.copy(progress = 100f, status = ItemStatus.COMPLETED) }
                    is DownloadProgress.Failed -> updateItem(item.id) { it.copy(status = ItemStatus.FAILED, errorMsg = progress.reason) }
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
```

---

### 1F — Download Manager UI Screen

**Create file:** `app/src/main/kotlin/com/akay/axbrowser/ui/screens/DownloadManagerScreen.kt`

```kotlin
package com.akay.axbrowser.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.akay.axbrowser.downloader.DownloadItem
import com.akay.axbrowser.downloader.DownloadManagerViewModel
import com.akay.axbrowser.downloader.ItemStatus
import com.akay.axbrowser.ui.theme.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerScreen(
    onBack: () -> Unit,
    viewModel: DownloadManagerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val tabs = listOf("Active (${state.active.size})", "Completed", "Failed")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // yt-dlp setup banner
            if (state.isSettingUpYtDlp) {
                YtDlpSetupBanner(progress = state.ytDlpSetupProgress)
            }

            // Tab row
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { /* handled by pager */ },
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
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800), repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // File icon + name + status badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon based on type
                val icon = when {
                    item.filename.endsWith(".mp4") || item.filename.endsWith(".mkv") -> Icons.Default.VideoFile
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
                            ItemStatus.RUNNING -> "${item.speedStr} • ${item.totalStr}"
                            ItemStatus.PAUSED -> "Paused"
                            ItemStatus.COMPLETED -> "Completed • ${item.totalStr}"
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
                // Status badge
                StatusBadge(status = item.status)
            }

            // Progress bar (only for active)
            if (item.status == ItemStatus.RUNNING || item.status == ItemStatus.PAUSED) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { item.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small),
                    color = if (item.status == ItemStatus.PAUSED)
                        MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${"%.1f".format(item.progress)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action buttons
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
        ItemStatus.RUNNING -> Pair(Color(0xFF6C63FF), "↓")
        ItemStatus.PAUSED -> Pair(Color(0xFFFF9800), "⏸")
        ItemStatus.COMPLETED -> Pair(Color(0xFF4CAF50), "✓")
        ItemStatus.FAILED -> Pair(Color(0xFFFF5252), "✗")
        ItemStatus.QUEUED -> Pair(Color(0xFF9E9E9E), "…")
        ItemStatus.CANCELLED -> Pair(Color(0xFF757575), "✗")
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
```

---

### 1G — Media Bottom Sheet (shown when user taps the floating download button)

**Create file:** `app/src/main/kotlin/com/akay/axbrowser/ui/components/MediaBottomSheet.kt`

```kotlin
package com.akay.axbrowser.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akay.axbrowser.downloader.engine.MediaFormat

/**
 * Bottom sheet shown when the user taps the floating "N Media" button.
 * Lists all detected media items. Tapping one opens [MediaDetailSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaBottomSheet(
    detectedUrls: List<DetectedMediaUi>,      // list of media found on current page
    onDownloadDirect: (url: String, filename: String) -> Unit,
    onDownloadWithYtDlp: (url: String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                "Detected Media",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            Divider()
            LazyColumn {
                items(detectedUrls) { media ->
                    MediaItemRow(
                        media = media,
                        onDownloadDirect = { onDownloadDirect(media.url, media.filename) },
                        onDownloadYtDlp = { onDownloadWithYtDlp(media.url) }
                    )
                    Divider(modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
        }
    }
}

@Composable
fun MediaItemRow(
    media: DetectedMediaUi,
    onDownloadDirect: () -> Unit,
    onDownloadYtDlp: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (media.isVideo) Icons.Default.VideoFile else Icons.Default.AudioFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(media.filename, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(media.mimeType ?: media.url, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        // Show two options: direct download and yt-dlp
        Column {
            SmallButton("Direct", onClick = onDownloadDirect)
            Spacer(Modifier.height(4.dp))
            SmallButton("YT-DLP", onClick = onDownloadYtDlp, isPrimary = true)
        }
    }
}

@Composable
fun SmallButton(label: String, onClick: () -> Unit, isPrimary: Boolean = false) {
    if (isPrimary) {
        FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    } else {
        OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

data class DetectedMediaUi(
    val id: String,
    val url: String,
    val filename: String,
    val mimeType: String?,
    val isVideo: Boolean
)
```

---

## PART 2 — DEV CONSOLE (Native Network Inspector + Eruda)

This is a **native Compose panel** that slides up from the bottom. It has 4 tabs:
- **Network** — every HTTP request the WebView makes, with URL, method, status, headers, timing
- **Console** — JavaScript logs and errors (powered by Eruda.js)
- **Elements** — current page HTML source
- **Storage** — cookies, localStorage preview

### 2A — Network Request Interceptor

**Create file:** `app/src/main/kotlin/com/akay/axbrowser/devconsole/NetworkInterceptor.kt`

```kotlin
package com.akay.axbrowser.devconsole

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class NetworkRequest(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val method: String = "GET",
    val requestHeaders: Map<String, String> = emptyMap(),
    val responseStatus: Int? = null,
    val responseHeaders: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val startTime: Long = System.currentTimeMillis(),
    val isBlocked: Boolean = false
)

/**
 * Singleton that collects all network requests from the WebView.
 * Feed it from WebViewClient.shouldInterceptRequest.
 * BrowserViewModel observes this.
 */
object NetworkInterceptor {
    private val _requests = MutableStateFlow<List<NetworkRequest>>(emptyList())
    val requests: StateFlow<List<NetworkRequest>> = _requests.asStateFlow()

    private const val MAX_ENTRIES = 500

    fun onRequest(request: NetworkRequest) {
        _requests.update { current ->
            val updated = current + request
            if (updated.size > MAX_ENTRIES) updated.drop(updated.size - MAX_ENTRIES) else updated
        }
    }

    fun onResponse(requestId: String, status: Int, headers: Map<String, String>, sizeBytes: Long) {
        _requests.update { list ->
            list.map { req ->
                if (req.id == requestId) req.copy(
                    responseStatus = status,
                    responseHeaders = headers,
                    sizeBytes = sizeBytes,
                    durationMs = System.currentTimeMillis() - req.startTime
                ) else req
            }
        }
    }

    fun clear() { _requests.value = emptyList() }

    fun markBlocked(url: String) {
        _requests.update { list ->
            list.map { if (it.url == url) it.copy(isBlocked = true) else it }
        }
    }
}
```

**Modify your existing WebViewClient's `shouldInterceptRequest`** to report to NetworkInterceptor:

```kotlin
// Add this at the TOP of shouldInterceptRequest, before any other logic:
override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
    val netReq = NetworkRequest(
        url = request.url.toString(),
        method = request.method ?: "GET",
        requestHeaders = request.requestHeaders ?: emptyMap()
    )
    NetworkInterceptor.onRequest(netReq)
    
    // ... rest of your existing shouldInterceptRequest logic ...
}
```

---

### 2B — Eruda JS Injection

**Create file:** `app/src/main/assets/js/eruda_init.js`

```javascript
// Only inject if not already present
if (typeof eruda === 'undefined') {
    var script = document.createElement('script');
    script.src = 'https://cdn.jsdelivr.net/npm/eruda';
    script.onload = function() {
        eruda.init({
            tool: ['console', 'elements', 'network', 'resources', 'info'],
            useShadowDom: true,
            autoScale: true,
            defaults: {
                displaySize: 60,
                transparency: 0.95,
                theme: 'Dark'
            }
        });
    };
    document.head.appendChild(script);
} else {
    eruda.show();
}
```

**Add this function to your WebViewClient or Browser utils:**

```kotlin
fun injectEruda(webView: WebView) {
    val js = webView.context.assets.open("js/eruda_init.js").bufferedReader().readText()
    webView.evaluateJavascript(js, null)
}
```

---

### 2C — Native Dev Console Panel (Compose)

**Create file:** `app/src/main/kotlin/com/akay/axbrowser/devconsole/DevConsolePanel.kt`

```kotlin
package com.akay.axbrowser.devconsole

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full-screen dev console panel.
 * Shows network requests, console logs, and page info.
 * Toggle visibility from browser menu or via [isVisible].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DevConsolePanel(
    isVisible: Boolean,
    currentPageUrl: String,
    currentPageHtml: String = "",
    onDismiss: () -> Unit
) {
    val requests by NetworkInterceptor.requests.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedRequest by remember { mutableStateOf<NetworkRequest?>(null) }
    val tabs = listOf("Network (${requests.size})", "Console", "Elements", "Info")

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Header bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.BugReport, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Dev Console", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (selectedTab == 0) {
                        IconButton(onClick = { NetworkInterceptor.clear() }) {
                            Icon(Icons.Default.DeleteSweep, "Clear")
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }

                // Tab row
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    edgePadding = 0.dp
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index }) {
                            Text(title, modifier = Modifier.padding(12.dp), fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider()

                when (selectedTab) {
                    0 -> NetworkTab(requests = requests, onSelectRequest = { selectedRequest = it })
                    1 -> ConsoleTab()
                    2 -> ElementsTab(html = currentPageHtml)
                    3 -> InfoTab(url = currentPageUrl, requestCount = requests.size)
                }
            }

            // Request detail overlay
            selectedRequest?.let { req ->
                RequestDetailSheet(request = req, onDismiss = { selectedRequest = null })
            }
        }
    }
}

@Composable
fun NetworkTab(requests: List<NetworkRequest>, onSelectRequest: (NetworkRequest) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(requests.reversed(), key = { it.id }) { req ->
            NetworkRequestRow(request = req, onClick = { onSelectRequest(req) })
            HorizontalDivider(thickness = 0.5.dp)
        }
    }
}

@Composable
fun NetworkRequestRow(request: NetworkRequest, onClick: () -> Unit) {
    val statusColor = when {
        request.isBlocked -> Color(0xFFFF5252)
        request.responseStatus == null -> Color(0xFF9E9E9E)
        request.responseStatus in 200..299 -> Color(0xFF4CAF50)
        request.responseStatus in 300..399 -> Color(0xFF2196F3)
        request.responseStatus in 400..499 -> Color(0xFFFF9800)
        else -> Color(0xFFFF5252)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator dot
        Box(
            modifier = Modifier.size(8.dp).background(statusColor, shape = MaterialTheme.shapes.small)
        )
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = request.url.removePrefix("https://").removePrefix("http://").let {
                    if (it.length > 60) it.take(57) + "…" else it
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = request.method,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                if (request.mimeType != null) {
                    Text(request.mimeType, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                }
                if (request.durationMs > 0) {
                    Text("${request.durationMs}ms", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                }
                if (request.isBlocked) {
                    Text("BLOCKED", fontSize = 10.sp, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            }
        }

        // Status code
        if (request.responseStatus != null) {
            Text(
                text = "${request.responseStatus}",
                color = statusColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestDetailSheet(request: NetworkRequest, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Text("Request Detail", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            // URL (selectable + copyable)
            SelectionContainer {
                Text(request.url, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))

            Text("Request Headers", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            request.requestHeaders.forEach { (k, v) ->
                HeaderRow(name = k, value = v)
            }

            if (request.responseHeaders.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Response Headers", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                request.responseHeaders.forEach { (k, v) ->
                    HeaderRow(name = k, value = v)
                }
            }
        }
    }
}

@Composable
fun HeaderRow(name: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = name,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(140.dp)
        )
        SelectionContainer {
            Text(
                text = value,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface.copy(0.8f)
            )
        }
    }
}

@Composable
fun ConsoleTab() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Code, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary.copy(0.5f))
            Spacer(Modifier.height(8.dp))
            Text("Eruda console is active on the page.", style = MaterialTheme.typography.bodySmall)
            Text("Tap the Eruda icon on the webpage to open it.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        }
    }
}

@Composable
fun ElementsTab(html: String) {
    if (html.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Page source will appear here.", color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        }
    } else {
        val scroll = rememberScrollState()
        Box(modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)) {
            SelectionContainer {
                Text(html, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
fun InfoTab(url: String, requestCount: Int) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoRow("Current URL", url)
        InfoRow("Total Requests", requestCount.toString())
        InfoRow("Eruda", "Injected when Dev Mode is ON")
        InfoRow("Network Interception", "Active via WebViewClient")
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(160.dp), fontSize = 13.sp)
        SelectionContainer {
            Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
        }
    }
}
```

---

### 2D — Hook Dev Console into BrowserScreen

**Modify your existing `BrowserScreen.kt`**:

1. Add a `devConsoleVisible` state var.
2. Add a dev console icon button in the top bar or bottom menu.
3. Render `DevConsolePanel` at the bottom of the Scaffold.

```kotlin
// ADD inside BrowserScreen:
var devConsoleVisible by remember { mutableStateOf(false) }
var pageHtml by remember { mutableStateOf("") }

// ADD inside your top bar / menu (wherever you have a MoreVert menu):
DropdownMenuItem(
    text = { Text("Dev Console") },
    leadingIcon = { Icon(Icons.Default.BugReport, null) },
    onClick = {
        devConsoleVisible = true
        // Also capture current page HTML
        webView.evaluateJavascript("document.documentElement.outerHTML") { html ->
            pageHtml = html?.removeSurrounding("\"")
                ?.replace("\\n", "\n")
                ?.replace("\\\"", "\"")
                ?: ""
        }
    }
)

// ADD at the bottom of your Scaffold's content block:
DevConsolePanel(
    isVisible = devConsoleVisible,
    currentPageUrl = uiState.currentUrl,
    currentPageHtml = pageHtml,
    onDismiss = { devConsoleVisible = false }
)
```

---

## PART 3 — UI OVERHAUL

### 3A — Theme (Replace or fully update your existing Theme.kt)

**Update file:** `app/src/main/kotlin/com/akay/axbrowser/ui/theme/Theme.kt`

```kotlin
package com.akay.axbrowser.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark palette
val Ax_Purple = Color(0xFF6C63FF)
val Ax_PurpleContainer = Color(0xFF1A1040)
val Ax_DarkBg = Color(0xFF0D0D1A)
val Ax_Surface = Color(0xFF12121F)
val Ax_SurfaceVariant = Color(0xFF1C1C2E)
val Ax_Accent = Color(0xFF00D9F5)
val Ax_Error = Color(0xFFFF5252)
val Ax_Success = Color(0xFF4CAF50)
val Ax_OnSurface = Color(0xFFE8E8F0)
val Ax_OnSurfaceVariant = Color(0xFF9898B0)

val AxDarkColorScheme = darkColorScheme(
    primary = Ax_Purple,
    onPrimary = Color.White,
    primaryContainer = Ax_PurpleContainer,
    onPrimaryContainer = Ax_Purple,
    secondary = Ax_Accent,
    onSecondary = Color(0xFF001A1F),
    background = Ax_DarkBg,
    onBackground = Ax_OnSurface,
    surface = Ax_Surface,
    onSurface = Ax_OnSurface,
    surfaceVariant = Ax_SurfaceVariant,
    onSurfaceVariant = Ax_OnSurfaceVariant,
    error = Ax_Error,
    outline = Color(0xFF2A2A40)
)

val AxLightColorScheme = lightColorScheme(
    primary = Color(0xFF5A52D5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDEBFF),
    background = Color(0xFFF5F5FF),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F0FA),
    onSurface = Color(0xFF0D0D1A),
    onSurfaceVariant = Color(0xFF555570)
)

@Composable
fun AxBrowserTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !darkTheme ->
            dynamicLightColorScheme(androidx.compose.ui.platform.LocalContext.current)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(androidx.compose.ui.platform.LocalContext.current)
        darkTheme -> AxDarkColorScheme
        else -> AxLightColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, typography = AxTypography, content = content)
}
```

**Create file:** `app/src/main/kotlin/com/akay/axbrowser/ui/theme/Type.kt`

```kotlin
package com.akay.axbrowser.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Use system default sans-serif but override weights for premium feel
val AxTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 57.sp, letterSpacing = (-0.25).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp)
)
```

---

### 3B — Glassmorphism Modifier Utility

**Create file:** `app/src/main/kotlin/com/akay/axbrowser/ui/theme/GlassModifier.kt`

```kotlin
package com.akay.axbrowser.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

fun Modifier.glassCard(
    alpha: Float = 0.08f,
    borderAlpha: Float = 0.15f,
    cornerRadius: Dp = 16.dp
): Modifier = composed {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
    this
        .background(color = Color.White.copy(alpha = alpha), shape = shape)
        .border(width = 1.dp, color = Color.White.copy(alpha = borderAlpha), shape = shape)
}

fun Modifier.glassSurface(alpha: Float = 0.05f): Modifier = composed {
    this.background(color = Color.White.copy(alpha = alpha))
}
```

---

### 3C — Animated Floating Download Button

**Create file:** `app/src/main/kotlin/com/akay/axbrowser/ui/components/FloatingDownloadButton.kt`

```kotlin
package com.akay.axbrowser.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.akay.axbrowser.ui.theme.Ax_Purple

/**
 * Pulsing FAB that appears when media is detected on the current page.
 * Shows count of detected media items.
 * Pulses to draw attention, settles after 3 seconds.
 */
@Composable
fun FloatingDownloadButton(
    mediaCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fab_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    ExtendedFloatingActionButton(
        modifier = modifier.scale(scale),
        onClick = onClick,
        containerColor = Ax_Purple,
        contentColor = Color.White,
        icon = { Icon(Icons.Default.Download, contentDescription = null) },
        text = {
            Text(
                text = if (mediaCount == 1) "Download" else "$mediaCount videos",
                style = MaterialTheme.typography.labelLarge
            )
        }
    )
}
```

---

### 3D — URL Bar Redesign

**Replace your existing UrlBar with this glassmorphism version:**

```kotlin
// Updated UrlBar.kt
@Composable
fun UrlBar(
    url: String,
    isLoading: Boolean,
    isSecure: Boolean = url.startsWith("https"),
    onUrlSubmit: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenTabSwitcher: () -> Unit,
    tabCount: Int = 1,
    canGoBack: Boolean = false,
    canGoForward: Boolean = false,
    modifier: Modifier = Modifier
) {
    var urlInput by remember(url) { mutableStateOf(url) }
    var isFocused by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(onClick = onBack, enabled = canGoBack) {
                Icon(
                    Icons.Default.ArrowBack, null,
                    tint = if (canGoBack) MaterialTheme.colorScheme.onSurface
                           else MaterialTheme.colorScheme.onSurface.copy(0.3f)
                )
            }

            // URL field — glass style
            OutlinedTextField(
                value = if (isFocused) urlInput else prettifyUrl(url),
                onValueChange = { urlInput = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.6f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
                ),
                leadingIcon = {
                    Icon(
                        imageVector = if (isSecure) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = if (isSecure) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                },
                trailingIcon = {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else if (isFocused && urlInput.isNotEmpty()) {
                        IconButton(onClick = { urlInput = "" }, modifier = Modifier.size(18.dp)) {
                            Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Go,
                    keyboardType = KeyboardType.Uri
                ),
                keyboardActions = KeyboardActions(onGo = {
                    onUrlSubmit(urlInput)
                    isFocused = false
                }),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            // Tab count button
            IconButton(onClick = onOpenTabSwitcher) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                        .border(1.5.dp, MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.small)
                ) {
                    Text(
                        text = tabCount.coerceAtMost(99).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            // Menu
            IconButton(onClick = onOpenMenu) {
                Icon(Icons.Default.MoreVert, null)
            }
        }
    }
}

fun prettifyUrl(url: String): String = url
    .removePrefix("https://")
    .removePrefix("http://")
    .removePrefix("www.")
    .let { if (it.length > 50) it.take(47) + "…" else it }
```

---

## PART 4 — WIRING EVERYTHING TOGETHER

### 4A — Main Navigation (NavHost)

**Update your `MainActivity.kt`** or create a NavHost composable:

```kotlin
// Create: app/src/main/kotlin/com/akay/axbrowser/ui/navigation/AxNavHost.kt

@Composable
fun AxNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = "browser") {
        composable("browser") {
            BrowserScreen(
                onOpenDownloadManager = { navController.navigate("downloads") },
                onOpenSettings = { navController.navigate("settings") },
                onOpenDevConsole = { /* handled inside BrowserScreen */ }
            )
        }
        composable("downloads") {
            DownloadManagerScreen(onBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
```

**Update `MainActivity.setContent`:**

```kotlin
setContent {
    AxBrowserTheme {
        AxNavHost()
    }
}
```

---

### 4B — Settings Screen (basic — for toggling Eruda + Ad Blocker)

**Create file:** `app/src/main/kotlin/com/akay/axbrowser/ui/screens/SettingsScreen.kt`

```kotlin
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {

            item {
                SettingsSection("Privacy") {
                    SettingsToggle("Ad Blocker", "Block ads and trackers", prefs.adBlockerEnabled) {
                        viewModel.setAdBlocker(it)
                    }
                    SettingsToggle("HTTPS Upgrade", "Auto-upgrade HTTP to HTTPS", prefs.httpsEnabled) {
                        viewModel.setHttpsUpgrade(it)
                    }
                }
            }

            item {
                SettingsSection("Developer") {
                    SettingsToggle(
                        title = "Dev Console",
                        subtitle = "Enable network inspector and Eruda console",
                        checked = prefs.erudaEnabled,
                        onCheckedChange = { viewModel.setErudaEnabled(it) }
                    )
                }
            }

            item {
                SettingsSection("Downloads") {
                    SettingsInfo("Download Folder", "AxBrowser Downloads (internal storage)")
                    SettingsInfo("yt-dlp", if (prefs.ytDlpReady) "Installed ✓" else "Not installed")
                }
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        content()
        HorizontalDivider()
    }
}

@Composable
fun SettingsToggle(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

@Composable
fun SettingsInfo(title: String, value: String) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    )
}
```

---

## PART 5 — REQUIRED GRADLE DEPENDENCIES

Make sure these are in your `build.gradle.kts` (app level). Add any that are missing:

```kotlin
dependencies {
    // Existing deps...

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Pager (for Download Manager tabs)
    implementation("androidx.compose.foundation:foundation")  // included in BOM

    // WorkManager (for background downloads)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // OkHttp (if not already present)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Material Icons Extended
    implementation("androidx.compose.material:material-icons-extended")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
}
```

---

## PART 6 — REQUIRED ANDROID MANIFEST PERMISSIONS

**Add to `AndroidManifest.xml`** (inside `<manifest>`, outside `<application>`):

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

---

## EXECUTION ORDER FOR THIS AGENT

Do these steps in order. Do not skip:

1. **Add dependencies** to build.gradle — run `./gradlew sync`
2. **Add permissions** to AndroidManifest.xml
3. **Create all files in Part 1** (YtDlpSetup, YtDlpEngine, DirectDownloadEngine, DownloadManagerViewModel, DownloadManagerScreen, MediaBottomSheet)
4. **Modify existing WebViewClient** to call `NetworkInterceptor.onRequest()` in shouldInterceptRequest
5. **Create all files in Part 2** (NetworkInterceptor, eruda_init.js, media_scanner.js, DevConsolePanel)
6. **Wire DevConsolePanel** into your existing BrowserScreen
7. **Create/replace theme files** in Part 3 (Theme.kt, Type.kt, GlassModifier.kt)
8. **Replace FloatingDownloadButton** with the animated version
9. **Replace UrlBar** with the redesigned version
10. **Update NavHost** in MainActivity
11. **Create SettingsScreen**
12. **Run `./gradlew assembleDebug`** — must compile clean
13. **Test on device:**
    - Open YouTube → floating "videos" button must appear or yt-dlp download must trigger
    - Open Dev Console from menu → network requests must list
    - Toggle Eruda in settings → refresh page → Eruda icon appears on page

---

## ACCEPTANCE CRITERIA — ALL MUST PASS

- [ ] App compiles with `./gradlew assembleDebug` — zero errors
- [ ] Loading any website shows network requests in Dev Console
- [ ] Toggling Dev Console in Settings makes it appear/disappear
- [ ] Eruda console icon appears on webpage when "Dev Console" is enabled in Settings
- [ ] Opening a YouTube URL → yt-dlp setup starts on first run (shows progress banner)
- [ ] After yt-dlp is ready → download button appears or media detected
- [ ] Download Manager screen shows Active / Completed / Failed tabs
- [ ] Download progress bar animates smoothly
- [ ] Pause/resume buttons work in Active tab
- [ ] Dark theme is applied with purple/dark navy palette
- [ ] URL bar shows lock icon (green for HTTPS, red for HTTP)
- [ ] Tab count badge shows correct number on tab switcher button
- [ ] Floating download button pulses when media is detected
