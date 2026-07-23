# AxBrowser — Fix Prompt v3
# Based on full re-read of repo after latest agent changes

---

## WHAT I AUDITED

Every Kotlin file in the current repo. Here is exactly what is broken RIGHT NOW:

---

## THE CRASH — Exact Root Cause

**File:** `feature/feature-downloads/src/main/kotlin/com/akay/feature/downloads/engine/YtDlpEngine.kt`

```kotlin
fun download(...): Flow<DownloadProgressUnified> = flow {
    val binary = YtDlpSetup.getBinaryFile(context).absolutePath
    val cmd = mutableListOf(binary, "--no-playlist", ...)
    cmd.add(url)

    val process = ProcessBuilder(cmd)       // ← THIS LINE
        .redirectErrorStream(true)
        .start()                            // ← THROWS IOException IF BINARY MISSING

    try {                                   // ← try starts AFTER the crash
        ...
    } finally {
        process.destroyForcibly()
    }
}.flowOn(Dispatchers.IO)
```

`ProcessBuilder.start()` is **outside** the `try` block. When the binary does not exist or is not executable, it throws `IOException`. This exception is NOT caught. It propagates out of the `flow { }` block into `viewModelScope.launch { }` in `DownloadViewModel.startDownload()`. An uncaught exception inside `viewModelScope.launch` **crashes the app**.

This is why tapping YT-DLP crashes.

---

## ALL BUGS FOUND (in priority order)

| # | File | Bug |
|---|---|---|
| 1 | `YtDlpEngine.kt` | ProcessBuilder.start() outside try block → crash |
| 2 | `DownloadViewModel.kt` | `getExternalFilesDir(null)` can return null → NPE crash |
| 3 | `BrowserNavHost.kt` | BrowserScreen and DownloadManagerScreen get **different** DownloadViewModel instances → downloads started in browser are invisible in Downloads tab |
| 4 | `AndroidManifest.xml` | `usesCleartextTraffic="false"` → OkHttp cannot download HTTP media URLs |
| 5 | `YtDlpSetup.kt` | `Runtime.getRuntime().exec("chmod +x")` is unreliable on Android → binary stays non-executable |
| 6 | `DownloadViewModel.kt` | When `useYtDlp=true` but `ytDlpReady=false`, silently falls to direct download of YouTube URL → HTTP 403 → confusing failure with no explanation |
| 7 | `DownloadViewModel.kt` | No error state when yt-dlp setup fails (network error etc.) → user sees nothing, no retry button |
| 8 | `BrowserScreen.kt` | `erudaEnabled` captured once at WebView creation time → Eruda toggle in Settings does nothing until app restart |
| 9 | `BrowserScreen.kt` | PasteLinkDialog "Open" just navigates browser to URL — does not offer download option |
| 10 | `YtDlpSetup.kt` | Downloads only `yt-dlp_android` (ARM64) — fails silently on x86_64 emulators and 32-bit devices |

---

## FIX 1 — CRASH: Wrap ProcessBuilder in try-catch

**File to rewrite:** `feature/feature-downloads/src/main/kotlin/com/akay/feature/downloads/engine/YtDlpEngine.kt`

Replace the entire `download()` function with this:

```kotlin
fun download(
    url: String,
    outputPath: String,
    formatId: String? = null
): Flow<DownloadProgressUnified> = flow {

    // Safety check before touching ProcessBuilder
    val binaryFile = YtDlpSetup.getBinaryFile(context)
    if (!binaryFile.exists()) {
        emit(DownloadProgressUnified.Failed("yt-dlp binary not found. Open Downloads tab to reinstall."))
        return@flow
    }
    if (!binaryFile.canExecute()) {
        // Try to fix permissions before giving up
        binaryFile.setExecutable(true, false)
    }
    if (!binaryFile.canExecute()) {
        emit(DownloadProgressUnified.Failed("yt-dlp binary not executable. Try reinstalling in Downloads tab."))
        return@flow
    }

    val binary = binaryFile.absolutePath
    val cmd = mutableListOf(
        binary,
        "--no-playlist",
        "--newline",
        "--no-warnings",
        "-o", outputPath
    )
    if (formatId != null) cmd.addAll(listOf("-f", formatId))
    cmd.add(url)

    val process = try {
        ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
    } catch (e: IOException) {
        emit(DownloadProgressUnified.Failed("Failed to launch yt-dlp: ${e.message}"))
        return@flow
    } catch (e: Exception) {
        emit(DownloadProgressUnified.Failed("Unexpected error starting yt-dlp: ${e.message}"))
        return@flow
    }

    try {
        process.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                if (!coroutineContext.isActive) {
                    process.destroy()
                    return@useLines
                }
                val progress = parseProgressLine(line)
                if (progress != null) emit(progress)
            }
        }
        val exitCode = process.waitFor()
        if (exitCode == 0) {
            emit(DownloadProgressUnified.Completed)
        } else {
            emit(DownloadProgressUnified.Failed("yt-dlp failed (exit $exitCode). URL may be unsupported or geo-blocked."))
        }
    } catch (e: Exception) {
        emit(DownloadProgressUnified.Failed("Download interrupted: ${e.message}"))
    } finally {
        process.destroyForcibly()
    }
}.flowOn(Dispatchers.IO)
```

Also add the missing import at the top of the file:
```kotlin
import java.io.IOException
import kotlin.coroutines.coroutineContext
```

---

## FIX 2 — CRASH: Null-safe download directory

**File:** `feature/feature-downloads/src/main/kotlin/com/akay/feature/downloads/viewmodel/DownloadViewModel.kt`

Replace:
```kotlin
private val downloadDir = File(application.getExternalFilesDir(null), "AxBrowser Downloads")
    .also { it.mkdirs() }
```

With:
```kotlin
private val downloadDir: File = run {
    val ext = application.getExternalFilesDir(null)
    val base = ext ?: application.filesDir  // fallback to internal if external not available
    File(base, "AxBrowser Downloads").also { it.mkdirs() }
}
```

---

## FIX 3 — CRITICAL: Share one DownloadViewModel across all screens

**Root cause:** Both `BrowserScreen` and `DownloadManagerScreen` call `hiltViewModel()` which creates separate ViewModel instances scoped to their own BackStackEntry. Downloads started in BrowserScreen never appear in DownloadManagerScreen.

**File to modify:** `feature/feature-browser/src/main/kotlin/com/akay/feature/browser/ui/BrowserNavHost.kt`

Create the DownloadViewModel once at the NavHost level and pass it to both screens.

Replace the entire file:

```kotlin
package com.akay.feature.browser.ui

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.*
import androidx.navigation.compose.*
import com.akay.feature.bookmarks.ui.BookmarkScreen
import com.akay.feature.downloads.ui.DownloadManagerScreen
import com.akay.feature.downloads.viewmodel.DownloadViewModel
import com.akay.feature.filemanager.ui.FileManagerScreen
import com.akay.feature.history.ui.HistoryScreen
import com.akay.feature.settings.ui.SettingsScreen
import com.akay.feature.videoplayer.ui.VideoPlayerScreen

private sealed class NavRoute(val route: String, val label: String, val icon: ImageVector) {
    object Browser   : NavRoute("browser",   "Browser",   Icons.Default.Language)
    object Downloads : NavRoute("downloads", "Downloads", Icons.Default.Download)
    object Bookmarks : NavRoute("bookmarks", "Bookmarks", Icons.Default.Bookmark)
    object History   : NavRoute("history",   "History",   Icons.Default.History)
    object Settings  : NavRoute("settings",  "Settings",  Icons.Default.Settings)
}

private val bottomNavItems = listOf(
    NavRoute.Browser, NavRoute.Downloads, NavRoute.Bookmarks, NavRoute.History, NavRoute.Settings
)

@Composable
fun BrowserNavHost() {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route
    val showBottomBar = bottomNavItems.any { it.route == currentRoute }

    // ONE shared DownloadViewModel for the entire app — scoped to Activity
    val activity = LocalActivity.current
    val downloadViewModel: DownloadViewModel = if (activity != null) {
        hiltViewModel(viewModelStoreOwner = activity)
    } else {
        hiltViewModel()
    }

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
                // Pass the shared DownloadViewModel — same instance browser and downloads share
                BrowserScreen(downloadViewModel = downloadViewModel)
            }
            composable(NavRoute.Downloads.route) {
                DownloadManagerScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = downloadViewModel   // same instance
                )
            }
            composable(NavRoute.Bookmarks.route) {
                BookmarkScreen(
                    onBookmarkClick = { navController.navigate(NavRoute.Browser.route) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(NavRoute.History.route) {
                HistoryScreen(
                    onHistoryClick = { navController.navigate(NavRoute.Browser.route) },
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
                        navController.navigate("videoplayer?url=${Uri.encode("file://$path")}")
                    }
                )
            }
        }
    }
}
```

**Also update `BrowserScreen` signature** — it already accepts `downloadViewModel` as a parameter, which is correct. Verify it looks like this:
```kotlin
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel = hiltViewModel(),
    downloadViewModel: DownloadViewModel = hiltViewModel()   // default kept for preview
)
```
No change needed here — the NavHost passes the shared instance explicitly.

**Also update `DownloadManagerScreen` signature** — add `viewModel` as an explicit parameter with default:
```kotlin
@Composable
fun DownloadManagerScreen(
    onBack: () -> Unit,
    viewModel: DownloadViewModel = hiltViewModel()  // default kept for standalone use
)
```
No change needed here either — NavHost passes the shared instance.

---

## FIX 4 — HTTP downloads blocked by usesCleartextTraffic

**File:** `app/src/main/AndroidManifest.xml`

The current manifest has `android:usesCleartextTraffic="false"` which blocks all HTTP traffic from OkHttp. Many media URLs (especially HLS segments, direct .mp4 links) are served over HTTP.

**Option A (simple):** Remove `usesCleartextTraffic="false"` entirely. The WebViewClient's HTTPS upgrade handles browser browsing. OkHttp can then connect to HTTP URLs for downloads.

Remove this attribute from `<application>`:
```xml
android:usesCleartextTraffic="false"    ← DELETE THIS LINE
```

**Option B (precise):** Create a network security config that allows HTTP only for OkHttp-based downloads. Create `app/src/main/res/xml/network_security_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

Then in `AndroidManifest.xml` replace `android:usesCleartextTraffic="false"` with:
```xml
android:networkSecurityConfig="@xml/network_security_config"
```

**Use Option A** — simpler and the HTTPS upgrade in WebViewClient provides sufficient browser-level security.

---

## FIX 5 — YtDlpSetup: Use setExecutable() instead of Runtime.exec

**File:** `feature/feature-downloads/src/main/kotlin/com/akay/feature/downloads/engine/YtDlpSetup.kt`

Replace the entire `setup()` function:

```kotlin
suspend fun setup(
    context: Context,
    client: OkHttpClient,
    onProgress: (Int) -> Unit = {}
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val binDir = File(context.filesDir, "bin").also { it.mkdirs() }
        val binFile = File(binDir, "yt-dlp")

        // Pick correct binary for device ABI
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val downloadUrl = when {
            abi.contains("x86_64") -> "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux"
            abi.contains("x86")    -> "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux"
            else                   -> "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_android"
        }

        val request = Request.Builder()
            .url(downloadUrl)
            .addHeader("User-Agent", "AxBrowser/1.0")
            .build()

        val response = client.newCall(request).execute()
        check(response.isSuccessful) { "HTTP ${response.code} downloading yt-dlp" }
        val body = response.body ?: error("Empty response body")
        val totalBytes = body.contentLength()

        binFile.outputStream().use { out ->
            var downloaded = 0L
            body.byteStream().use { input ->
                val buffer = ByteArray(32768)
                var bytes: Int
                while (input.read(buffer).also { bytes = it } != -1) {
                    out.write(buffer, 0, bytes)
                    downloaded += bytes
                    if (totalBytes > 0) onProgress(((downloaded * 100) / totalBytes).toInt())
                }
            }
        }

        // Use Java API to set executable — more reliable than Runtime.exec on Android
        binFile.setExecutable(true, false)
        binFile.setReadable(true, false)

        // Verify it worked
        if (!binFile.canExecute()) {
            // Fallback: try via ProcessBuilder (different from Runtime.exec)
            try {
                ProcessBuilder("chmod", "755", binFile.absolutePath)
                    .start().waitFor()
            } catch (_: Exception) {}
        }

        check(binFile.exists()) { "Binary file does not exist after download" }
        // Note: canExecute() might return false on some devices even when it works
        // We proceed as long as the file exists and has content
        check(binFile.length() > 0) { "Binary file is empty" }
    }
}
```

---

## FIX 6 — Handle ytDlpReady=false with proper error instead of silent fallback

**File:** `feature/feature-downloads/src/main/kotlin/com/akay/feature/downloads/viewmodel/DownloadViewModel.kt`

**Step 1:** Add `setupError` and `retrySetup` to state:

```kotlin
data class DownloadManagerState(
    val downloads: List<DownloadItem> = emptyList(),
    val ytDlpReady: Boolean = false,
    val ytDlpSetupProgress: Int = 0,
    val isSettingUpYtDlp: Boolean = false,
    val setupError: String? = null      // ADD THIS
) {
    val active get() = downloads.filter { it.status == ItemStatus.RUNNING || it.status == ItemStatus.QUEUED }
    val completed get() = downloads.filter { it.status == ItemStatus.COMPLETED }
    val failed get() = downloads.filter { it.status == ItemStatus.FAILED || it.status == ItemStatus.CANCELLED }
}
```

**Step 2:** Update `setupYtDlp()` to capture errors:

```kotlin
fun setupYtDlp() {
    viewModelScope.launch {
        _state.update { it.copy(isSettingUpYtDlp = true, setupError = null) }
        YtDlpSetup.setup(
            context = getApplication(),
            client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)   // 5 min for large binary
                .build(),
            onProgress = { p -> _state.update { it.copy(ytDlpSetupProgress = p) } }
        ).onSuccess {
            _state.update { it.copy(ytDlpReady = true, isSettingUpYtDlp = false, setupError = null) }
        }.onFailure { error ->
            _state.update {
                it.copy(
                    isSettingUpYtDlp = false,
                    ytDlpReady = false,
                    setupError = "Setup failed: ${error.message ?: "Unknown error"}. Check internet connection."
                )
            }
        }
    }
}
```

**Step 3:** Update `enqueue()` to check readiness:

```kotlin
fun enqueue(url: String, filename: String, useYtDlp: Boolean, formatId: String? = null): String {
    val id = UUID.randomUUID().toString()

    // If user wants yt-dlp but it's not ready, either queue it or show clear error
    val effectiveUseYtDlp = useYtDlp && _state.value.ytDlpReady

    if (useYtDlp && !_state.value.ytDlpReady) {
        // Add as a queued item that will wait for setup
        val outputFile = uniqueFile(downloadDir, filename)
        val item = DownloadItem(
            id = id,
            url = url,
            filename = outputFile.name,
            outputPath = outputFile.absolutePath,
            useYtDlp = true,
            formatId = formatId,
            status = ItemStatus.QUEUED,
            errorMsg = if (_state.value.isSettingUpYtDlp)
                "Waiting for yt-dlp setup to complete..."
            else
                "yt-dlp not installed. Tap retry after setup completes."
        )
        _state.update { it.copy(downloads = it.downloads + item) }
        // If setup is in progress, watch for completion and then start
        if (_state.value.isSettingUpYtDlp) {
            watchForSetupAndStart(item)
        }
        return id
    }

    val outputFile = uniqueFile(downloadDir, filename)
    val item = DownloadItem(
        id = id, url = url, filename = outputFile.name,
        outputPath = outputFile.absolutePath,
        useYtDlp = effectiveUseYtDlp,
        formatId = formatId
    )
    _state.update { it.copy(downloads = it.downloads + item) }
    startDownload(item)
    return id
}

private fun watchForSetupAndStart(item: DownloadItem) {
    viewModelScope.launch {
        // Poll until setup is done (max 5 minutes)
        var attempts = 0
        while (attempts < 300) {
            kotlinx.coroutines.delay(1000L)
            if (_state.value.ytDlpReady) {
                startDownload(item.copy(status = ItemStatus.QUEUED, errorMsg = null))
                return@launch
            }
            if (!_state.value.isSettingUpYtDlp) {
                // Setup finished but failed
                updateItem(item.id) {
                    it.copy(status = ItemStatus.FAILED, errorMsg = "yt-dlp setup failed. Tap retry.")
                }
                return@launch
            }
            attempts++
        }
        updateItem(item.id) { it.copy(status = ItemStatus.FAILED, errorMsg = "Timeout waiting for yt-dlp.") }
    }
}
```

---

## FIX 7 — Show setup error with retry button in DownloadManagerScreen

**File:** `feature/feature-downloads/src/main/kotlin/com/akay/feature/downloads/ui/DownloadManagerScreen.kt`

After the `if (state.isSettingUpYtDlp) { YtDlpSetupBanner(...) }` block, add:

```kotlin
// After the setup banner, add error banner:
state.setupError?.let { error ->
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Download engine setup failed",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { viewModel.setupYtDlp() }) {
                Text("Retry", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
```

Also update `DownloadManagerState` reference in the composable — `state.setupError` should now be accessible since you added it to the state class.

---

## FIX 8 — erudaEnabled dynamic reading

**File:** `feature/feature-browser/src/main/kotlin/com/akay/feature/browser/ui/BrowserScreen.kt`

**Problem:** `erudaEnabled` is captured once when the WebView factory lambda runs. Changes in Settings after app start don't take effect.

**Fix:** Use a `MutableState` holder that the WebViewClient closure can read dynamically:

At the top of `BrowserScreen`, after existing state declarations, add:
```kotlin
// Use a holder that can be updated without recreating WebView
val erudaEnabledState = remember { mutableStateOf(false) }
val erudaEnabled by viewModel.erudaEnabled.collectAsState(initial = false)

// Sync the state holder whenever erudaEnabled changes
LaunchedEffect(erudaEnabled) {
    erudaEnabledState.value = erudaEnabled
}
```

Then inside the `factory` lambda's `onPageFinished` callback, replace:
```kotlin
if (erudaEnabled) {
```
With:
```kotlin
if (erudaEnabledState.value) {
```

The closure now reads from `erudaEnabledState` which is a `MutableState` — Kotlin closures read the current value of mutable state each time they execute, so this will always use the latest setting.

---

## FIX 9 — PasteLinkDialog: Add Download option alongside Open

**File:** `feature/feature-browser/src/main/kotlin/com/akay/feature/browser/ui/BrowserScreen.kt`

Find `PasteLinkDialog` and its call site. The dialog currently only opens the URL in the browser.

**Step 1 — Update dialog to show two buttons:**

Replace the current `PasteLinkDialog` composable:
```kotlin
@Composable
fun PasteLinkDialog(
    url: String,
    onUrlChange: (String) -> Unit,
    onOpenInBrowser: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Link, contentDescription = null) },
        title = { Text("Paste Link") },
        text = {
            Column {
                Text(
                    "Enter a URL to open or download",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDownload, enabled = url.isNotBlank()) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Download")
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onOpenInBrowser, enabled = url.isNotBlank()) {
                    Text("Open")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
```

**Step 2 — Update the call site** in `BrowserScreen`:

```kotlin
if (showPasteLinkDialog) {
    PasteLinkDialog(
        url = pasteUrl,
        onUrlChange = { pasteUrl = it },
        onOpenInBrowser = {
            showPasteLinkDialog = false
            if (pasteUrl.isNotBlank()) {
                viewModel.navigateToUrl(pasteUrl)
                pasteUrl = ""
            }
        },
        onDownload = {
            showPasteLinkDialog = false
            if (pasteUrl.isNotBlank()) {
                val fname = pasteUrl.substringAfterLast("/").substringBefore("?")
                    .ifBlank { "download_${System.currentTimeMillis()}" }
                // Try yt-dlp first (works for video sites), direct fallback otherwise
                downloadViewModel.enqueue(url = pasteUrl, filename = fname, useYtDlp = true)
                pasteUrl = ""
            }
        },
        onDismiss = { showPasteLinkDialog = false }
    )
}
```

---

## FIX 10 — AxWebChromeClient: Stop suppressing JS alerts (breaks sites)

**File:** `feature/feature-browser/src/main/kotlin/com/akay/feature/browser/webview/AxWebChromeClient.kt`

Currently `onJsAlert` and `onJsConfirm` call `result?.cancel()` silently. This breaks download confirmation dialogs on many sites.

Replace:
```kotlin
override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
    result?.cancel()
    return true
}

override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
    result?.cancel()
    return true
}
```

With: **delete both functions entirely** and let the default WebChromeClient behavior handle JS dialogs, OR if you want native dialogs instead of WebView dialogs, replace with proper AlertDialog flow (advanced, skip for now). Deleting them is the correct fix.

---

## EXECUTION ORDER

Run these changes in this exact order:

```
1. Fix YtDlpEngine.kt          (FIX 1)  — stops the crash
2. Fix DownloadViewModel.kt    (FIX 2)  — stops NPE
3. Fix AndroidManifest.xml     (FIX 4)  — allows HTTP downloads
4. Fix YtDlpSetup.kt          (FIX 5)  — makes binary actually executable
5. Fix DownloadViewModel.kt    (FIX 6)  — proper ytDlp-not-ready handling
6. Fix DownloadManagerState    (FIX 6 Step 1) — add setupError field
7. Fix DownloadManagerScreen   (FIX 7)  — show error + retry UI
8. Fix BrowserNavHost.kt       (FIX 3)  — shared DownloadViewModel
9. Fix BrowserScreen.kt        (FIX 8 + 9) — erudaEnabled + PasteLinkDialog
10. Fix AxWebChromeClient.kt   (FIX 10) — remove JS dialog suppression
```

---

## BUILD VERIFICATION

After all changes:

```bash
./gradlew :feature:feature-downloads:compileDebugKotlin   # must pass
./gradlew :feature:feature-browser:compileDebugKotlin     # must pass
./gradlew assembleDebug                                    # must produce APK
```

---

## RUNTIME VERIFICATION

Test on a real device (not emulator if possible — yt-dlp_android is ARM64):

**Test 1 — No more crash on YT-DLP tap:**
1. Open browser → go to any website with a video (e.g. a direct `.mp4` link)
2. Floating download button appears
3. Tap it → MediaBottomSheet opens
4. Tap "YT-DLP"
5. ✅ App does NOT crash
6. ✅ Download appears in Downloads tab with correct status

**Test 2 — yt-dlp setup feedback:**
1. Clear app data (so binary is gone)
2. Open app → go to Downloads tab immediately
3. ✅ Setup banner shows with progress 0%→100%
4. If setup fails → ✅ Red error card appears with "Retry" button

**Test 3 — Downloads visible in Downloads tab:**
1. Open browser → tap floating download button → tap YT-DLP
2. ✅ Immediately go to Downloads tab
3. ✅ Download appears there (not empty!) with status QUEUED or RUNNING

**Test 4 — Paste Link Download:**
1. Copy a direct .mp4 URL
2. Tap the link icon (Paste Link) in browser
3. Paste URL
4. ✅ Two buttons appear: "Download" and "Open"
5. Tap "Download" → download appears in Downloads tab

**Test 5 — Eruda toggle works without restart:**
1. Open Settings → enable Dev Console
2. Go back to browser → load any page
3. ✅ Eruda gear icon appears on page without restarting app
