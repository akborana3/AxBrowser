# AxBrowser — Full Upgrade Agent Prompt
# Based on complete audit of: https://github.com/akborana3/AxBrowser

---

## WHAT I READ BEFORE WRITING THIS

I cloned and read every file in the repo. Here is exactly what is broken, missing, or incomplete:

| File | Problem |
|---|---|
| `BrowserScreen.kt` | Creates WebView with inline anonymous WebViewClient — `AxWebViewClient.kt` exists but is NEVER used |
| `BrowserScreen.kt` | Download buttons in MediaBottomSheet call `showMediaSheet = false` — never calls `viewModel.enqueue()` |
| `BrowserScreen.kt` | Eruda injection code present but `erudaEnabled` is hardcoded nowhere — eruda never actually fires |
| `BrowserScreen.kt` | No history written when user navigates to a URL |
| `BrowserNavHost.kt` | Only has 2 routes (`browser`, `downloads`) — Settings, History, Bookmarks, FileManager, VideoPlayer unreachable |
| `BrowserNavHost.kt` | No BottomNavigationBar — no way to switch screens |
| `DownloadViewModel.kt` | `flow.collect { progress -> when(progress) }` mixes `YtDlpProgress` and `DirectDownloadProgress` which are different sealed classes — this does not compile or behave correctly |
| `DownloadManagerScreen.kt` | Tab row `onClick = { }` is empty — tabs never actually switch pages |
| `DownloadManagerScreen.kt` | No "Open" or "Share" button on completed downloads |
| `SettingsScreen.kt` | No `onBack` parameter — no NavigationIcon — no back button |
| `SettingsScreen.kt` | No Dev Console / Eruda toggle |
| `SettingsScreen.kt` | No yt-dlp installation status or "Reinstall" button |
| `BrowserViewModel.kt` | `navigateToUrl` — `"google.com"` incorrectly goes to Google search instead of `https://google.com` |
| `BrowserViewModel.kt` | History never recorded on any navigation |
| `AxWebViewClient.kt` | `shouldInterceptRequest` never calls `NetworkInterceptor.onRequest()` |
| `AxWebViewClient.kt` | No media URL detection at all |
| `MediaDetector.kt` | Uses `JavascriptInterface` approach (`AxBrowserMediaDetector`) but it is never added to WebView — dead code |
| ALL UI screens | Theme colors defined but glassmorphism used nowhere except `GlassCard` which is never used in screens |
| `BookmarkScreen.kt` | No `onBack` callback — unreachable from navigation anyway |
| `HistoryScreen.kt` | No `onBack` callback — unreachable from navigation anyway |
| `FileManagerScreen.kt` | No video launch callback |

---

## YOUR JOB

Fix all of the above. Do not rewrite working files unless the file has a bug. Add what is missing. Every instruction below is specific — follow it exactly.

---

## FIX 1 — DownloadViewModel: Fix the sealed class type mismatch

**File:** `feature/feature-downloads/src/main/kotlin/com/akay/feature/downloads/viewmodel/DownloadViewModel.kt`

**Problem:** `YtDlpProgress` and `DirectDownloadProgress` are separate sealed classes. You cannot `when` on a value that is sometimes one type and sometimes the other. The current code does this and it is wrong.

**Fix:** Create a unified progress type and convert both engines to use it.

**Step 1 — Create a unified progress sealed class. Add this new file:**

`feature/feature-downloads/src/main/kotlin/com/akay/feature/downloads/engine/DownloadProgressUnified.kt`
```kotlin
package com.akay.feature.downloads.engine

sealed class DownloadProgressUnified {
    data class Running(
        val percent: Float,
        val speedStr: String,
        val totalBytesStr: String
    ) : DownloadProgressUnified()
    data object Completed : DownloadProgressUnified()
    data class Failed(val reason: String) : DownloadProgressUnified()
}
```

**Step 2 — Change `YtDlpEngine.download()` to return `Flow<DownloadProgressUnified>`** instead of `Flow<YtDlpProgress>`. Replace the return type and replace `YtDlpProgress.Running(...)` → `DownloadProgressUnified.Running(...)`, `YtDlpProgress.Completed` → `DownloadProgressUnified.Completed`, `YtDlpProgress.Failed(...)` → `DownloadProgressUnified.Failed(...)`.

**Step 3 — Change `DirectDownloadEngine.download()` to return `Flow<DownloadProgressUnified>`** the same way. Replace `DirectDownloadProgress.*` with `DownloadProgressUnified.*`.

**Step 4 — Fix `DownloadViewModel.startDownload()`** so it collects `Flow<DownloadProgressUnified>`:
```kotlin
private fun startDownload(item: DownloadItem) {
    val job = viewModelScope.launch {
        updateItem(item.id) { it.copy(status = ItemStatus.RUNNING) }
        val flow: Flow<DownloadProgressUnified> = if (item.useYtDlp && _state.value.ytDlpReady) {
            ytDlpEngine.download(item.url, item.outputPath, item.formatId)
        } else {
            directEngine.download(item.id, item.url, File(item.outputPath))
        }
        flow.collect { progress ->
            when (progress) {
                is DownloadProgressUnified.Running -> updateItem(item.id) {
                    it.copy(
                        progress = progress.percent,
                        speedStr = progress.speedStr,
                        totalStr = progress.totalBytesStr,
                        status = ItemStatus.RUNNING
                    )
                }
                DownloadProgressUnified.Completed -> updateItem(item.id) {
                    it.copy(progress = 100f, status = ItemStatus.COMPLETED)
                }
                is DownloadProgressUnified.Failed -> updateItem(item.id) {
                    it.copy(status = ItemStatus.FAILED, errorMsg = progress.reason)
                }
            }
        }
    }
    downloadJobs[item.id] = job
}
```

---

## FIX 2 — Wire AxWebViewClient into BrowserScreen (stop using inline WebViewClient)

**File:** `feature/feature-browser/src/main/kotlin/com/akay/feature/browser/ui/BrowserScreen.kt`

**Problem:** The entire `WebViewClient` is written inline inside `AndroidView { factory = ... }`. The existing `AxWebViewClient.kt` in the `webview` package is never used. This also means:
- `NetworkInterceptor` is never called from `AxWebViewClient`
- Media URL detection is missing from `AxWebViewClient`

**Fix in two parts:**

### Part A — Update `AxWebViewClient.kt`

**File:** `feature/feature-browser/src/main/kotlin/com/akay/feature/browser/webview/AxWebViewClient.kt`

Replace the entire file with this complete implementation:

```kotlin
package com.akay.feature.browser.webview

import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.akay.feature.browser.devconsole.NetworkInterceptor
import com.akay.feature.browser.devconsole.NetworkRequest
import java.io.ByteArrayInputStream

class AxWebViewClient(
    private val context: Context,
    private val onPageStarted: (url: String) -> Unit,
    private val onPageFinished: (url: String, title: String?) -> Unit,
    private val onError: (String) -> Unit,
    private val adBlockerEnabled: Boolean = true,
    private val httpsUpgradeEnabled: Boolean = true,
    private val onMediaDetected: (url: String, mimeType: String?) -> Unit = { _, _ -> }
) : WebViewClient() {

    private val blockedDomains = mutableSetOf<String>()

    private val videoExtensions = listOf(".mp4", ".webm", ".mkv", ".avi", ".mov", ".m3u8", ".mpd", ".ts", ".flv")
    private val audioExtensions = listOf(".mp3", ".m4a", ".aac", ".ogg", ".wav", ".flac", ".opus")

    fun setBlockedDomains(domains: Set<String>) {
        blockedDomains.clear()
        blockedDomains.addAll(domains)
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val req = request ?: return null
        val url = req.url.toString()

        // 1. Report to NetworkInterceptor for Dev Console
        val netReq = NetworkRequest(
            url = url,
            method = req.method ?: "GET",
            requestHeaders = req.requestHeaders ?: emptyMap()
        )
        NetworkInterceptor.onRequest(netReq)

        // 2. Ad blocking
        if (adBlockerEnabled && isBlocked(url)) {
            NetworkInterceptor.markBlocked(url)
            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
        }

        // 3. Media URL detection by extension
        val lower = url.lowercase()
        val isVideo = videoExtensions.any { lower.contains(it) }
        val isAudio = audioExtensions.any { lower.contains(it) }
        if (isVideo || isAudio) {
            val mime = guessMime(lower)
            onMediaDetected(url, mime)
        }

        // 4. Media detection by Accept header
        val accept = req.requestHeaders?.get("Accept") ?: ""
        if (accept.contains("video/") || accept.contains("audio/")) {
            onMediaDetected(url, null)
        }

        return null
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        if (httpsUpgradeEnabled && url.startsWith("http://")) {
            view?.loadUrl(url.replaceFirst("http://", "https://"))
            return true
        }
        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        url?.let { onPageStarted(it) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onPageFinished(url ?: "", view?.title)

        // Inject media scanner JS
        val js = runCatching {
            context.assets.open("js/media_scanner.js").bufferedReader().readText()
        }.getOrNull()
        js?.let { view?.evaluateJavascript(it, null) }
    }

    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?
    ) {
        onError(description ?: "Unknown error")
    }

    private fun isBlocked(url: String): Boolean {
        return try {
            val host = java.net.URI(url).host ?: return false
            blockedDomains.any { domain -> host == domain || host.endsWith(".$domain") }
        } catch (e: Exception) { false }
    }

    private fun guessMime(url: String): String? = when {
        url.contains(".mp4") -> "video/mp4"
        url.contains(".webm") -> "video/webm"
        url.contains(".mkv") -> "video/x-matroska"
        url.contains(".m3u8") -> "application/x-mpegurl"
        url.contains(".mpd") -> "application/dash+xml"
        url.contains(".mp3") -> "audio/mpeg"
        url.contains(".m4a") -> "audio/mp4"
        url.contains(".aac") -> "audio/aac"
        url.contains(".ogg") -> "audio/ogg"
        else -> null
    }
}
```

### Part B — Rewrite the AndroidView block in `BrowserScreen.kt`

Find the `AndroidView` block (the one with `factory = { context -> WebView(context).apply { ... } }`) and replace the entire `AndroidView` composable with this:

```kotlin
// Stateful ref for the WebView and Eruda enabled
var currentWebView by remember { mutableStateOf<WebView?>(null) }

// Observe Eruda pref — inject when enabled
val erudaEnabled by viewModel.erudaEnabled.collectAsState(initial = false)

AndroidView(
    factory = { ctx ->
        WebView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

            webViewClient = AxWebViewClient(
                context = ctx,
                onPageStarted = { url -> viewModel.updateUrl(url) },
                onPageFinished = { url, title ->
                    viewModel.updateProgress(100)
                    title?.let { viewModel.updateTitle(it) }
                    // Save to history
                    if (url.isNotBlank() && url != "about:blank") {
                        viewModel.recordHistory(url, title ?: url)
                    }
                    // Inject Eruda if enabled
                    if (erudaEnabled) {
                        val js = ctx.assets.open("js/eruda_init.js").bufferedReader().readText()
                        evaluateJavascript(js, null)
                    }
                    // Run media scanner DOM scan and add results to NetworkInterceptor
                    val scanJs = ctx.assets.open("js/media_scanner.js").bufferedReader().readText()
                    evaluateJavascript(scanJs) { result ->
                        if (!result.isNullOrEmpty() && result != "null") {
                            parseAndReportDomMedia(result)
                        }
                    }
                },
                onError = { viewModel.updateTitle("Error") },
                onMediaDetected = { url, mime ->
                    NetworkInterceptor.onRequest(
                        com.akay.feature.browser.devconsole.NetworkRequest(url = url, mimeType = mime)
                    )
                }
            )

            webChromeClient = AxWebChromeClient(
                onProgressChanged = { viewModel.updateProgress(it) },
                onTitleChanged = { viewModel.updateTitle(it) },
                onFaviconChanged = {},
                onPermissionRequest = {}
            )

            currentWebView = this
            webView = this
            loadUrl(uiState.url)
        }
    },
    update = { wv ->
        if (uiState.url.isNotEmpty() && uiState.url != lastNavigatedUrl) {
            lastNavigatedUrl = uiState.url
            wv.loadUrl(uiState.url)
        }
    },
    modifier = Modifier.fillMaxSize()
)
```

Add this helper function at the bottom of `BrowserScreen.kt` (file scope, not inside the composable):
```kotlin
private fun parseAndReportDomMedia(json: String) {
    try {
        val cleaned = json.trim().removeSurrounding("\"")
            .replace("\\\"", "\"").replace("\\n", "\n").replace("\\/", "/")
            .replace("\\u003C", "<")
        if (!cleaned.startsWith("[")) return
        var i = 0
        while (i < cleaned.length) {
            val urlStart = cleaned.indexOf("\"url\"", i).takeIf { it >= 0 } ?: break
            val u1 = cleaned.indexOf("\"", cleaned.indexOf(":", urlStart) + 1) + 1
            val u2 = cleaned.indexOf("\"", u1)
            val url = cleaned.substring(u1, u2)
            val typeStart = cleaned.indexOf("\"type\"", u2).takeIf { it >= 0 } ?: break
            val t1 = cleaned.indexOf("\"", cleaned.indexOf(":", typeStart) + 1) + 1
            val t2 = cleaned.indexOf("\"", t1)
            val type = cleaned.substring(t1, t2)
            if (url.startsWith("http")) NetworkInterceptor.addDomMedia(url, type)
            i = t2 + 1
        }
    } catch (_: Exception) {}
}
```

---

## FIX 3 — MediaBottomSheet must actually trigger downloads

**File:** `feature/feature-browser/src/main/kotlin/com/akay/feature/browser/ui/BrowserScreen.kt`

Find this block:
```kotlin
if (showMediaSheet) {
    val allMedia = networkMedia.map { ... }
    if (allMedia.isNotEmpty()) {
        MediaBottomSheet(
            detectedUrls = allMedia,
            onDownloadDirect = { url, filename -> showMediaSheet = false },
            onDownloadWithYtDlp = { url -> showMediaSheet = false },
            onDismiss = { showMediaSheet = false }
        )
    }
}
```

Replace with:
```kotlin
if (showMediaSheet) {
    val allMedia = networkMedia.map {
        DetectedMediaUi(it.id, it.url, it.filename, it.mimeType, it.isVideo)
    }
    if (allMedia.isNotEmpty()) {
        MediaBottomSheet(
            detectedUrls = allMedia,
            onDownloadDirect = { url, filename ->
                showMediaSheet = false
                downloadViewModel.enqueue(url = url, filename = filename, useYtDlp = false)
            },
            onDownloadWithYtDlp = { url ->
                showMediaSheet = false
                val filename = url.substringAfterLast("/").substringBefore("?")
                    .ifBlank { "video_${System.currentTimeMillis()}.mp4" }
                downloadViewModel.enqueue(url = url, filename = filename, useYtDlp = true)
            },
            onDismiss = { showMediaSheet = false }
        )
    }
}
```

Add `downloadViewModel` to `BrowserScreen`:
```kotlin
// Add this parameter at the top of BrowserScreen composable (after viewModel):
val downloadViewModel: DownloadViewModel = hiltViewModel()
```

---

## FIX 4 — BrowserViewModel: Fix URL processing + Add history recording + Add erudaEnabled flow

**File:** `feature/feature-browser/src/main/kotlin/com/akay/feature/browser/viewmodel/BrowserViewModel.kt`

**Fix 1 — navigateToUrl:** Replace the `navigateToUrl` function with this:
```kotlin
fun navigateToUrl(url: String) {
    val processedUrl = when {
        url.isBlank() -> return
        url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:") -> url
        // Has a dot and no spaces → treat as domain
        url.contains(".") && !url.contains(" ") -> "https://$url"
        // Otherwise → search
        else -> "https://www.google.com/search?q=${java.net.URLEncoder.encode(url, "UTF-8")}"
    }
    _uiState.value = _uiState.value.copy(url = processedUrl, displayUrl = processedUrl)
    viewModelScope.launch {
        _uiState.value.activeTab?.let { tab ->
            tabRepository.updateTab(tab.copy(url = processedUrl, lastAccessed = System.currentTimeMillis()))
        }
    }
}
```

**Fix 2 — Add history recording.** Add `HistoryRepository` to the constructor and add this function:
```kotlin
// Add to constructor:
// private val historyRepository: HistoryRepository

fun recordHistory(url: String, title: String) {
    viewModelScope.launch {
        historyRepository.addOrUpdateHistory(
            com.akay.core.domain.model.HistoryItem(
                id = java.util.UUID.randomUUID().toString(),
                url = url,
                title = title,
                lastVisited = System.currentTimeMillis()
            )
        )
    }
}
```

**Fix 3 — Add erudaEnabled flow** (read from `AxPreferences`):
```kotlin
// Add to constructor: private val preferences: AxPreferences
val erudaEnabled = preferences.erudaEnabled  // already exists in AxPreferences
```

**Fix 4 — Add Hilt injection for HistoryRepository and AxPreferences in the constructor:**
```kotlin
@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val tabRepository: TabRepository,
    private val historyRepository: HistoryRepository,
    private val preferences: AxPreferences
) : ViewModel() { ... }
```

---

## FIX 5 — DownloadManagerScreen: Fix tabs not switching

**File:** `feature/feature-downloads/src/main/kotlin/com/akay/feature/downloads/ui/DownloadManagerScreen.kt`

**Problem:** `onClick = { }` on each Tab does nothing. Pager never changes.

**Fix:** Add a `coroutineScope` and call `pagerState.animateScrollToPage(index)`:

At the top of the composable, add:
```kotlin
val coroutineScope = rememberCoroutineScope()
```

Replace:
```kotlin
Tab(
    selected = pagerState.currentPage == index,
    onClick = { },
    text = { Text(title, fontWeight = FontWeight.SemiBold) }
)
```
With:
```kotlin
Tab(
    selected = pagerState.currentPage == index,
    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
    text = { Text(title, fontWeight = FontWeight.SemiBold) }
)
```

**Also add Open and Share buttons to completed downloads.** In `DownloadCard`, in the `ItemStatus.COMPLETED ->` branch, replace:
```kotlin
ItemStatus.COMPLETED -> {
    IconButton(onClick = { onDelete(item.id) }) { Icon(Icons.Default.Delete, "Delete") }
}
```
With:
```kotlin
ItemStatus.COMPLETED -> {
    IconButton(onClick = { onOpen(item.id) }) { Icon(Icons.Default.OpenInNew, "Open") }
    IconButton(onClick = { onShare(item.id) }) { Icon(Icons.Default.Share, "Share") }
    IconButton(onClick = { onDelete(item.id) }) { Icon(Icons.Default.Delete, "Delete") }
}
```

Add `onOpen` and `onShare` to the `DownloadCard` function signature:
```kotlin
fun DownloadCard(
    item: DownloadItem,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (String) -> Unit,    // ADD
    onShare: (String) -> Unit    // ADD
)
```

Add `onOpen` and `onShare` to `DownloadViewModel`:
```kotlin
fun openFile(id: String, context: android.content.Context) {
    val item = _state.value.downloads.find { it.id == id } ?: return
    val file = java.io.File(item.outputPath)
    if (!file.exists()) return
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", file
    )
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Open with"))
}

fun shareFile(id: String, context: android.content.Context) {
    val item = _state.value.downloads.find { it.id == id } ?: return
    val file = java.io.File(item.outputPath)
    if (!file.exists()) return
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", file
    )
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = context.contentResolver.getType(uri) ?: "*/*"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share via"))
}
```

Add FileProvider to `AndroidManifest.xml` inside `<application>`:
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

Create `app/src/main/res/xml/file_paths.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <external-files-path name="downloads" path="AxBrowser Downloads/" />
    <files-path name="internal" path="." />
</paths>
```

---

## FIX 6 — Complete Navigation: Full BottomNav + All Routes

**File:** `feature/feature-browser/src/main/kotlin/com/akay/feature/browser/ui/BrowserNavHost.kt`

**Replace the entire file** with this complete implementation:

```kotlin
package com.akay.feature.browser.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.*
import androidx.navigation.compose.*
import com.akay.feature.bookmarks.ui.BookmarkScreen
import com.akay.feature.downloads.ui.DownloadManagerScreen
import com.akay.feature.filemanager.ui.FileManagerScreen
import com.akay.feature.history.ui.HistoryScreen
import com.akay.feature.settings.ui.SettingsScreen
import com.akay.feature.videoplayer.ui.VideoPlayerScreen

private sealed class NavRoute(val route: String, val label: String, val icon: ImageVector) {
    object Browser  : NavRoute("browser",   "Browser",   Icons.Default.Language)
    object Downloads: NavRoute("downloads", "Downloads", Icons.Default.Download)
    object Bookmarks: NavRoute("bookmarks", "Bookmarks", Icons.Default.Bookmark)
    object History  : NavRoute("history",   "History",   Icons.Default.History)
    object Settings : NavRoute("settings",  "Settings",  Icons.Default.Settings)
}

private val bottomNavItems = listOf(
    NavRoute.Browser,
    NavRoute.Downloads,
    NavRoute.Bookmarks,
    NavRoute.History,
    NavRoute.Settings
)

@Composable
fun BrowserNavHost() {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    // Only show BottomBar on top-level routes
    val showBottomBar = bottomNavItems.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoute.Browser.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavRoute.Browser.route) {
                BrowserScreen()
            }
            composable(NavRoute.Downloads.route) {
                DownloadManagerScreen(onBack = { navController.popBackStack() })
            }
            composable(NavRoute.Bookmarks.route) {
                BookmarkScreen(
                    onBookmarkClick = { url ->
                        navController.navigate(NavRoute.Browser.route)
                        // Pass URL back to browser — handled via shared ViewModel or argument
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(NavRoute.History.route) {
                HistoryScreen(
                    onHistoryClick = { url ->
                        navController.navigate(NavRoute.Browser.route)
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(NavRoute.Settings.route) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "videoplayer?url={url}&title={title}",
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val url = backStackEntry.arguments?.getString("url") ?: ""
                val title = backStackEntry.arguments?.getString("title") ?: ""
                VideoPlayerScreen(videoUrl = url, title = title, onBack = { navController.popBackStack() })
            }
            composable("filemanager") {
                FileManagerScreen(
                    onFileClick = { path ->
                        val encoded = Uri.encode(path)
                        navController.navigate("videoplayer?url=file://$encoded")
                    }
                )
            }
        }
    }
}
```

---

## FIX 7 — SettingsScreen: Add onBack + Dev Console toggle + yt-dlp status

**File:** `feature/feature-settings/src/main/kotlin/com/akay/feature/settings/ui/SettingsScreen.kt`

**Add `onBack` parameter and NavigationIcon:**
```kotlin
@Composable
fun SettingsScreen(
    onBack: () -> Unit,       // ADD THIS
    viewModel: SettingsViewModel = hiltViewModel()
) {
```

```kotlin
TopAppBar(
    title = { Text("Settings") },
    navigationIcon = {                                    // ADD THIS BLOCK
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, "Back")
        }
    }
)
```

**Add Dev Console section** after the "General" section:
```kotlin
SettingsSection(title = "Developer") {
    SettingsSwitchItem(
        title = "Dev Console / Eruda",
        checked = uiState.isErudaEnabled,
        onCheckedChange = { viewModel.setErudaEnabled(it) }
    )
    Text(
        text = if (uiState.ytDlpInstalled) "yt-dlp: Installed ✓" else "yt-dlp: Not installed",
        style = MaterialTheme.typography.bodySmall,
        color = if (uiState.ytDlpInstalled)
            androidx.compose.ui.graphics.Color(0xFF4CAF50)
        else MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}
```

**Add `isErudaEnabled` and `ytDlpInstalled` to `SettingsUiState`:**
```kotlin
data class SettingsUiState(
    // ... existing fields ...
    val isErudaEnabled: Boolean = false,
    val ytDlpInstalled: Boolean = false
)
```

**Add to `SettingsViewModel`:**
```kotlin
// In constructor, add: private val context: @ApplicationContext Context
// In init/loadPreferences():
viewModelScope.launch {
    preferences.erudaEnabled.collect { _uiState.value = _uiState.value.copy(isErudaEnabled = it) }
}
_uiState.value = _uiState.value.copy(
    ytDlpInstalled = com.akay.feature.downloads.engine.YtDlpSetup.isInstalled(context)
)

fun setErudaEnabled(enabled: Boolean) {
    viewModelScope.launch { preferences.setErudaEnabled(enabled) }
}
```

**Add `setErudaEnabled` to `AxPreferences`** (it already has `erudaEnabled` Flow but may be missing the setter):
```kotlin
suspend fun setErudaEnabled(enabled: Boolean) {
    context.dataStore.edit { it[PreferencesKeys.ERUDA_ENABLED] = enabled }
}
```

---

## FIX 8 — BookmarkScreen and HistoryScreen: Add onBack parameter

**File:** `feature/feature-bookmarks/src/main/kotlin/com/akay/feature/bookmarks/ui/BookmarkScreen.kt`

Change signature:
```kotlin
fun BookmarkScreen(
    viewModel: BookmarkViewModel = hiltViewModel(),
    onBookmarkClick: (String) -> Unit = {},
    onBack: () -> Unit = {}    // ADD
)
```

Add `navigationIcon` to `TopAppBar`:
```kotlin
TopAppBar(
    title = { Text("Bookmarks") },
    navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
    }
)
```

**File:** `feature/feature-history/src/main/kotlin/com/akay/feature/history/ui/HistoryScreen.kt`

Same pattern — add `onBack: () -> Unit = {}` parameter and navigationIcon.

---

## FIX 9 — UI Overhaul: Apply glassmorphism and premium styling to all screens

The `GlassCard` and `glassCard()` modifier exist in `core-ui` but are used nowhere. Apply them now.

### 9A — BrowserScreen URL bar

**In `BrowserScreen.kt`**, replace the `TopAppBar` title block for URL display (the `else` branch of `isEditingUrl`) with a properly styled version. Replace the non-editing title column:

```kotlin
// Replace the else branch title Column with:
Column(modifier = Modifier
    .fillMaxWidth()
    .background(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.extraLarge
    )
    .padding(horizontal = 12.dp, vertical = 6.dp)
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (uiState.displayUrl.startsWith("https")) Icons.Default.Lock else Icons.Default.LockOpen,
            contentDescription = null,
            tint = if (uiState.displayUrl.startsWith("https"))
                androidx.compose.ui.graphics.Color(0xFF4CAF50)
            else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = prettifyUrl(uiState.displayUrl),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
```

Add this helper at file scope:
```kotlin
private fun prettifyUrl(url: String): String =
    url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        .let { if (it.length > 45) it.take(42) + "…" else it }
```

### 9B — DownloadCard glassmorphism

In `DownloadManagerScreen.kt`, change `DownloadCard`'s `Card` to use glass styling. Replace:
```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    shape = MaterialTheme.shapes.large
)
```
With:
```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
        containerColor = com.akay.core.ui.theme.Glass
    ),
    border = androidx.compose.foundation.BorderStroke(
        1.dp, com.akay.core.ui.theme.GlassStroke
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    shape = MaterialTheme.shapes.large
)
```

### 9C — StatusBadge: Use real labels instead of "DL", "||", "OK"

In `DownloadManagerScreen.kt`, replace `StatusBadge`:
```kotlin
val (color, label) = when (status) {
    ItemStatus.RUNNING   -> Pair(Color(0xFF6C63FF), "↓ Downloading")
    ItemStatus.PAUSED    -> Pair(Color(0xFFFF9800), "⏸ Paused")
    ItemStatus.COMPLETED -> Pair(Color(0xFF4CAF50), "✓ Done")
    ItemStatus.FAILED    -> Pair(Color(0xFFFF5252), "✗ Failed")
    ItemStatus.QUEUED    -> Pair(Color(0xFF9E9E9E), "… Queued")
    ItemStatus.CANCELLED -> Pair(Color(0xFF757575), "✗ Cancelled")
}
```

### 9D — DownloadManagerScreen TopAppBar: Add color background
```kotlin
TopAppBar(
    title = { Text("Downloads", fontWeight = FontWeight.Bold) },
    navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
    },
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface
    )
)
```

### 9E — BookmarkScreen and HistoryScreen: Style items as cards

In `BookmarkScreen.kt`, wrap the `BookmarkItem` row with a Card:
```kotlin
items(uiState.bookmarks) { bookmark ->
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = com.akay.core.ui.theme.Glass),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.akay.core.ui.theme.GlassStroke),
        shape = MaterialTheme.shapes.large
    ) {
        BookmarkItem(
            bookmark = bookmark,
            onClick = { onBookmarkClick(bookmark.url) },
            onDelete = { viewModel.deleteBookmark(bookmark.id) }
        )
    }
}
```

Apply the same card wrapper in `HistoryScreen.kt` for `HistoryItem`.

---

## FIX 10 — Eruda: Make the injection actually work end to end

The `eruda_init.js` exists. The injection code exists in BrowserScreen. But `erudaEnabled` was never plumbed through.

After Fix 2 (using `AxWebViewClient`) and Fix 4 (adding `erudaEnabled` flow to `BrowserViewModel`), the injection will work. But also ensure:

1. In `AxPreferences.kt`, confirm `erudaEnabled` Flow and `setErudaEnabled` both exist (add if missing).
2. In `PreferencesKeys.kt`, confirm `val ERUDA_ENABLED = booleanPreferencesKey("eruda_enabled")` exists (already does per file read).
3. The `eruda_init.js` is already correct. No changes needed.

---

## FIX 11 — Fix AxWebChromeClient usage in BrowserScreen

The `AxWebChromeClient` class exists at `feature/feature-browser/src/main/kotlin/com/akay/feature/browser/webview/AxWebChromeClient.kt`. Make sure it is imported and used in the AndroidView factory (as shown in Fix 2 Part B above). Do not create a second anonymous `WebChromeClient` inline.

---

## NEW FILE 1 — Add HistoryRepository.addOrUpdateHistory implementation

**File:** `core/core-data/src/main/kotlin/com/akay/core/data/repository/HistoryRepositoryImpl.kt`

Check if `addOrUpdateHistory` exists. If not, add it:
```kotlin
override suspend fun addOrUpdateHistory(item: HistoryItem) {
    val existing = historyDao.getByUrl(item.url)
    if (existing != null) {
        historyDao.update(existing.copy(
            title = item.title ?: existing.title,
            visitCount = existing.visitCount + 1,
            lastVisited = System.currentTimeMillis()
        ))
    } else {
        historyDao.insert(item.toEntity())
    }
}
```

Also add `getByUrl` to `HistoryDao.kt` if missing:
```kotlin
@Query("SELECT * FROM history WHERE url = :url LIMIT 1")
suspend fun getByUrl(url: String): HistoryEntity?
```

---

## FINAL CHECKLIST — Run after all changes

```
1. ./gradlew :feature:feature-downloads:compileDebugKotlin     → must pass (DownloadProgressUnified fix)
2. ./gradlew :feature:feature-browser:compileDebugKotlin       → must pass (AxWebViewClient wired)
3. ./gradlew :app:compileDebugKotlin                           → must pass (NavHost updated)
4. ./gradlew assembleDebug                                     → must produce APK
```

### Manual verification on device:
- [ ] Open app → Bottom navigation bar shows 5 tabs (Browser, Downloads, Bookmarks, History, Settings)
- [ ] Browse to `google.com` (no http/https) → loads `https://google.com` (not search)
- [ ] Browse to `youtube.com` → floating "N media" button appears with at least 1 detected item
- [ ] Tap floating button → MediaBottomSheet opens → tap "YT-DLP" → download appears in Downloads tab with RUNNING status and progress updating
- [ ] Direct download (tap "Direct" on a .mp4 URL) → download appears and progress bar moves
- [ ] Downloads tab → Active → progress card shows speed and percentage
- [ ] Downloads tab → tap Active / Completed / Failed → pages switch (not stay on same page)
- [ ] Completed download → Open button launches file with system app chooser
- [ ] Completed download → Share button opens share sheet
- [ ] Settings → "Dev Console / Eruda" toggle → flip ON → go back to browser → navigate to any page → Eruda gear icon appears on the page
- [ ] Settings → "Dev Console / Eruda" ON → tap BugReport icon in browser toolbar → native panel slides up → Network tab lists all requests
- [ ] History tab shows recently visited URLs
- [ ] Bookmarks tab shows saved bookmarks (add one via long-press or future menu option)
- [ ] Settings screen has a back arrow
```

---

## IMPORTANT RULES

- Do NOT delete `YtDlpProgress` or `DirectDownloadProgress` sealed classes if other code references them — only update what is needed
- Do NOT delete `MediaDetector.kt` — leave it in place even though it is unused
- Do NOT change `AxTheme.kt` or `Color.kt` — the colors are correct
- Do NOT change `AxWebChromeClient.kt` — it is fine as-is
- The `GlassCard.kt` and `FloatingMediaButton.kt` in `core-ui` are correct — just use them in screens
- Only modify what is listed above. Leave all other files untouched.
