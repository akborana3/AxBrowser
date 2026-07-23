# AxBrowser — Fix Prompt v4
# Full audit of latest repo. Fix yt-dlp permission denied + runtime permissions + notifications.

---

## EXACT DIAGNOSIS FROM SCREENSHOT

**Error 1:** `Failed to launch yt-dlp: Cannot run program ".../files/bin/yt-dlp": error=13, Permission denied`

**Root cause:** Android 10+ enforces **W^X (Write XOR Execute)** policy. The directory
`/data/user/0/[package]/files/` is mounted with the `noexec` flag at the kernel level.
Even if you call `setExecutable(true)` or `chmod +x`, the kernel refuses to exec
the file. **`setExecutable()` only sets the permission bit — it does NOT override the
mount flag.** This is why it still fails after our last fix.

**The ONLY solution:** Native binaries on Android must live in `nativeLibraryDir`
(`/data/app/[package]-[hash]/lib/[abi]/`), which is the ONLY directory Android
marks as executable for app-specific files. We rename yt-dlp to `libytdlp.so`,
bundle it in `jniLibs`, and Android's installer puts it in `nativeLibraryDir` automatically.

**Error 2:** `stream was reset: INTERNAL_ERROR`

**Root cause:** `DirectDownloadEngine` uses OkHttp which defaults to HTTP/2.
Servers streaming protected or DRM content drop H2 connections with INTERNAL_ERROR.
Fix: force HTTP/1.1 for the download client.

**Error 3 (user-reported):** App never asks for storage or notification permissions.

**Root cause:** `MainActivity` never calls `requestPermissions()`. Permissions are in
the manifest but are not automatically granted on Android 6+.

---

## ALL CHANGES REQUIRED

---

## CHANGE 1 — Add Gradle task to download yt-dlp binary at build time

**File:** `app/build.gradle.kts`

Add the following block at the **very top**, before the `plugins {}` block:

```kotlin
import java.net.URL

// Download yt-dlp binaries into jniLibs before build.
// They are packaged as .so files so Android extracts them to nativeLibraryDir
// which is the ONLY executable directory on Android 10+ (W^X policy).
tasks.register("downloadYtDlpBinaries") {
    val arm64File = file("src/main/jniLibs/arm64-v8a/libytdlp.so")
    val x86File   = file("src/main/jniLibs/x86_64/libytdlp.so")
    outputs.files(arm64File, x86File)

    doLast {
        arm64File.parentFile.mkdirs()
        x86File.parentFile.mkdirs()

        // ARM64 — covers >95% of physical Android devices
        if (!arm64File.exists() || arm64File.length() < 1_000_000L) {
            println("⬇ Downloading yt-dlp for arm64-v8a...")
            URL("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_android")
                .openStream().use { src -> arm64File.outputStream().use { src.copyTo(it) } }
            println("✓ arm64 binary: ${arm64File.length()} bytes → ${arm64File.path}")
        } else {
            println("✓ arm64 yt-dlp already present (${arm64File.length()} bytes)")
        }

        // x86_64 — emulators / Chrome OS
        if (!x86File.exists() || x86File.length() < 1_000_000L) {
            println("⬇ Downloading yt-dlp for x86_64...")
            URL("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux")
                .openStream().use { src -> x86File.outputStream().use { src.copyTo(it) } }
            println("✓ x86_64 binary: ${x86File.length()} bytes → ${x86File.path}")
        } else {
            println("✓ x86_64 yt-dlp already present (${x86File.length()} bytes)")
        }
    }
}

// Run before every build automatically
tasks.configureEach {
    if (name == "preBuild") dependsOn("downloadYtDlpBinaries")
}
```

Then inside the existing `android { }` block, add a `packaging` section:

```kotlin
android {
    // ... existing config ...

    packaging {
        jniLibs {
            useLegacyPackaging = true   // Forces APK to extract .so to nativeLibraryDir
        }
    }
}
```

Also add `core-library-desugaring` for Java 8+ APIs if not already present:
```kotlin
compileOptions {
    isCoreLibraryDesugaringEnabled = true
}
dependencies {
    coreLibraryDesugaring("com.android.tools.build:desugaring:2.0.4")
    // ... existing deps ...
}
```

---

## CHANGE 2 — AndroidManifest.xml: Add extractNativeLibs + missing permissions + network config

**File:** `app/src/main/AndroidManifest.xml`

Replace the entire file:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Network -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!-- Storage — legacy (Android ≤ 9) -->
    <uses-permission
        android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />
    <uses-permission
        android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />

    <!-- Storage — granular media (Android 13+) -->
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />

    <!-- Notifications (Android 13+) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Background services for downloads -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <application
        android:name=".AxBrowserApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.AxBrowser"
        android:enableOnBackInvokedCallback="true"
        android:extractNativeLibs="true"
        android:usesCleartextTraffic="true"
        android:networkSecurityConfig="@xml/network_security_config">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|fontScale"
            android:theme="@style/Theme.AxBrowser"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="http" />
                <data android:scheme="https" />
            </intent-filter>
        </activity>

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>

    </application>
</manifest>
```

**Create file:** `app/src/main/res/xml/network_security_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!--
        Allow cleartext HTTP for media downloads.
        Browser enforces HTTPS via AxWebViewClient.shouldOverrideUrlLoading.
    -->
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

---

## CHANGE 3 — Rewrite YtDlpSetup.kt completely

**File:** `feature/feature-downloads/src/main/kotlin/com/akay/feature/downloads/engine/YtDlpSetup.kt`

Replace the entire file:

```kotlin
package com.akay.feature.downloads.engine

import android.content.Context
import java.io.File

/**
 * yt-dlp binary is bundled as libytdlp.so in jniLibs.
 * Android's installer extracts it to nativeLibraryDir, which is the ONLY
 * directory on Android 10+ (W^X policy) where native binaries can be executed.
 *
 * NO runtime downloading. Binary ships with the APK.
 * Gradle task "downloadYtDlpBinaries" downloads it at build time.
 */
object YtDlpSetup {

    /** Absolute path to yt-dlp binary extracted by Android installer. */
    fun getBinaryPath(context: Context): String =
        "${context.applicationInfo.nativeLibraryDir}/libytdlp.so"

    /** Returns true if the binary exists and has content. */
    fun isInstalled(context: Context): Boolean {
        val file = File(getBinaryPath(context))
        return file.exists() && file.length() > 1_000_000L   // real binary is > 1 MB
    }

    /**
     * Returns a human-readable status string for display in Settings / Downloads.
     */
    fun statusString(context: Context): String {
        val file = File(getBinaryPath(context))
        return when {
            !file.exists()               -> "Not found — rebuild the app"
            file.length() < 1_000_000L  -> "Corrupted — rebuild the app (${file.length()} bytes)"
            !file.canExecute()           -> "Not executable — reinstall app"
            else                         -> "Ready ✓ (${formatSize(file.length())})"
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 * 1024        -> "${bytes / 1024}KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024f * 1024f))}MB"
        else                        -> "${"%.2f".format(bytes / (1024f * 1024f * 1024f))}GB"
    }
}
```

---

## CHANGE 4 — Rewrite YtDlpEngine.kt: use nativeLibraryDir path

**File:** `feature/feature-downloads/src/main/kotlin/com/akay/feature/downloads/engine/YtDlpEngine.kt`

Replace the entire file:

```kotlin
package com.akay.feature.downloads.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive

class YtDlpEngine(private val context: Context) {

    /**
     * Get available formats for a URL.
     * Returns list of [MediaFormat] so the user can choose quality.
     */
    suspend fun getFormats(url: String): Result<List<MediaFormat>> = runCatching {
        val binary = requireBinary()
        val process = ProcessBuilder(binary, "-F", "--no-playlist", "--no-warnings", url)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        parseFormats(output)
    }

    /**
     * Download a video using yt-dlp.
     * Binary lives in nativeLibraryDir — always executable on Android 10+.
     */
    fun download(
        url: String,
        outputPath: String,
        formatId: String? = null
    ): Flow<DownloadProgressUnified> = flow {

        // Verify binary before touching ProcessBuilder
        val binaryPath = runCatching { requireBinary() }.getOrElse { e ->
            emit(DownloadProgressUnified.Failed(e.message ?: "Binary error"))
            return@flow
        }

        val cmd = buildList {
            add(binaryPath)
            add("--no-playlist")
            add("--newline")                    // one progress line per stdout write
            add("--no-warnings")
            add("--no-check-certificates")      // avoid SSL issues on some Android builds
            add("--no-part")                    // no .part temp files
            add("--retries")
            add("3")
            add("--fragment-retries")
            add("3")
            add("-o")
            add(outputPath)
            if (formatId != null) { add("-f"); add(formatId) }
            add(url)
        }

        val process = try {
            ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
        } catch (e: IOException) {
            emit(DownloadProgressUnified.Failed("Cannot start yt-dlp: ${e.message}"))
            return@flow
        } catch (e: Exception) {
            emit(DownloadProgressUnified.Failed("Unexpected launch error: ${e.message}"))
            return@flow
        }

        try {
            val reader = process.inputStream.bufferedReader()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (!coroutineContext.isActive) {
                    process.destroy()
                    return@flow
                }
                val l = line ?: continue
                val progress = parseProgressLine(l)
                if (progress != null) emit(progress)
            }
            val exitCode = process.waitFor()
            when {
                exitCode == 0 -> emit(DownloadProgressUnified.Completed)
                exitCode == 1 -> emit(DownloadProgressUnified.Failed(
                    "yt-dlp error (exit 1) — URL may be geo-blocked or requires login"
                ))
                else -> emit(DownloadProgressUnified.Failed(
                    "yt-dlp exited with code $exitCode — check URL and try again"
                ))
            }
        } catch (e: Exception) {
            emit(DownloadProgressUnified.Failed("Download interrupted: ${e.message}"))
        } finally {
            runCatching { process.destroyForcibly() }
        }
    }.flowOn(Dispatchers.IO)

    // ─────────────────────────────────────────────────────────────

    /** Verifies and returns the binary path, throws with clear message if missing. */
    private fun requireBinary(): String {
        val path = YtDlpSetup.getBinaryPath(context)
        val file = File(path)
        check(file.exists()) {
            "yt-dlp binary not found at $path — rebuild the app to re-bundle it"
        }
        check(file.length() > 1_000_000L) {
            "yt-dlp binary appears corrupted (${file.length()} bytes) — rebuild the app"
        }
        check(file.canExecute()) {
            "yt-dlp binary not executable — nativeLibraryDir permission issue"
        }
        return path
    }

    private fun parseProgressLine(line: String): DownloadProgressUnified.Running? {
        // Match: [download]  45.3% of ~23.40MiB at  2.50MiB/s ETA 00:10
        val regex = Regex("""\[download\]\s+([\d.]+)%\s+of\s+~?([\d.]+\w+)\s+at\s+([\d.]+\w+/s)""")
        val match = regex.find(line) ?: return null
        val percent = match.groupValues[1].toFloatOrNull() ?: return null
        return DownloadProgressUnified.Running(
            percent      = percent,
            totalBytesStr = match.groupValues[2],
            speedStr     = match.groupValues[3]
        )
    }

    private fun parseFormats(output: String): List<MediaFormat> =
        output.lines()
            .filter { it.isNotBlank() && !it.startsWith("[") && !it.startsWith("-") }
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 3 && parts[0].matches(Regex("\\d+[a-z]?")))
                    MediaFormat(id = parts[0], ext = parts.getOrElse(1) { "" },
                        resolution = parts.getOrElse(2) { "" }, description = line.trim())
                else null
            }
}

data class MediaFormat(val id: String, val ext: String, val resolution: String, val description: String)
```

---

## CHANGE 5 — DownloadViewModel: Remove setup logic, keep binary check simple

**File:** `feature/feature-downloads/src/main/kotlin/com/akay/feature/downloads/viewmodel/DownloadViewModel.kt`

**Step A — Remove `setupYtDlp()`, `watchForSetupAndStart()`, and the setup error fields.**

Replace `init {}` and `checkYtDlp()` with:
```kotlin
init {
    checkYtDlp()
}

private fun checkYtDlp() {
    val installed = YtDlpSetup.isInstalled(getApplication())
    _state.update { it.copy(ytDlpReady = installed) }
    if (!installed) {
        _state.update {
            it.copy(setupError = "yt-dlp not found. Rebuild the app to rebundle it.\n" +
                    "Status: ${YtDlpSetup.statusString(getApplication())}")
        }
    }
}
```

**Step B — Replace `setupYtDlp()` function** (keep the function signature so the UI can call "Retry" which just re-checks):
```kotlin
/** Re-checks binary status. Called by "Retry" button in UI. */
fun retryYtDlpCheck() {
    checkYtDlp()
}
```

**Step C — Simplify `enqueue()`** — remove the `watchForSetupAndStart` path:
```kotlin
fun enqueue(url: String, filename: String, useYtDlp: Boolean, formatId: String? = null): String {
    val id = UUID.randomUUID().toString()
    val outputFile = uniqueFile(downloadDir, filename)
    val canUseYtDlp = useYtDlp && _state.value.ytDlpReady

    val item = DownloadItem(
        id          = id,
        url         = url,
        filename    = outputFile.name,
        outputPath  = outputFile.absolutePath,
        useYtDlp   = canUseYtDlp,
        formatId    = formatId,
        errorMsg    = if (useYtDlp && !_state.value.ytDlpReady)
                          "yt-dlp not available — using direct download instead"
                      else null
    )
    _state.update { it.copy(downloads = it.downloads + item) }
    startDownload(item)
    return id
}
```

**Step D — Fix `DirectDownloadEngine` HTTP/2 crash** by replacing the `directEngine` init:
```kotlin
private val directEngine = DirectDownloadEngine(
    OkHttpClient.Builder()
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))   // force HTTP/1.1 — avoids INTERNAL_ERROR
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .addHeader("User-Agent", "Mozilla/5.0 (Android) AxBrowser/1.0")
                .build()
            chain.proceed(req)
        }
        .build()
)
```

---

## CHANGE 6 — AxBrowserApp: Create notification channels on startup

**File:** `app/src/main/kotlin/com/akay/axbrowser/AxBrowserApp.kt`

Replace the entire file:

```kotlin
package com.akay.axbrowser

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AxBrowserApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = getSystemService(NotificationManager::class.java)

        // Active download progress — low importance (no sound, no popup)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DOWNLOAD_PROGRESS,
                "Download Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active download progress"
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }
        )

        // Download complete — default importance (makes sound)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DOWNLOAD_COMPLETE,
                "Download Complete",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a download finishes"
            }
        )

        // Download failed
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DOWNLOAD_FAILED,
                "Download Failed",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when a download fails"
            }
        )
    }

    companion object {
        const val CHANNEL_DOWNLOAD_PROGRESS = "ax_dl_progress"
        const val CHANNEL_DOWNLOAD_COMPLETE = "ax_dl_complete"
        const val CHANNEL_DOWNLOAD_FAILED   = "ax_dl_failed"
    }
}
```

---

## CHANGE 7 — DownloadNotificationManager: Show real notifications

**Create new file:** `feature/feature-downloads/src/main/kotlin/com/akay/feature/downloads/notification/DownloadNotificationManager.kt`

```kotlin
package com.akay.feature.downloads.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.akay.axbrowser.AxBrowserApp
import com.akay.axbrowser.MainActivity

object DownloadNotificationManager {

    private const val BASE_NOTIFICATION_ID = 9000

    /** Show or update a progress notification for an active download. */
    fun showProgress(
        context: Context,
        downloadId: String,
        filename: String,
        percent: Float,
        speedStr: String,
        totalStr: String
    ) {
        if (!hasPermission(context)) return

        val notifId = notifIdFor(downloadId)
        val tapIntent = tapPendingIntent(context)

        val notification = NotificationCompat.Builder(context, AxBrowserApp.CHANNEL_DOWNLOAD_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading: $filename")
            .setContentText("${"%.1f".format(percent)}% · $speedStr · $totalStr")
            .setProgress(100, percent.toInt(), percent == 0f)
            .setOngoing(true)           // user cannot dismiss while downloading
            .setOnlyAlertOnce(true)     // don't ping every update
            .setContentIntent(tapIntent)
            .setSilent(true)
            .build()

        NotificationManagerCompat.from(context).notify(notifId, notification)
    }

    /** Show a "Download complete" notification. */
    fun showComplete(context: Context, downloadId: String, filename: String) {
        if (!hasPermission(context)) return

        val notifId = notifIdFor(downloadId)
        val tapIntent = tapPendingIntent(context)

        NotificationManagerCompat.from(context).notify(
            notifId,
            NotificationCompat.Builder(context, AxBrowserApp.CHANNEL_DOWNLOAD_COMPLETE)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download complete")
                .setContentText(filename)
                .setAutoCancel(true)
                .setContentIntent(tapIntent)
                .build()
        )
    }

    /** Show a "Download failed" notification. */
    fun showFailed(context: Context, downloadId: String, filename: String, reason: String) {
        if (!hasPermission(context)) return

        val notifId = notifIdFor(downloadId)
        val tapIntent = tapPendingIntent(context)

        NotificationManagerCompat.from(context).notify(
            notifId,
            NotificationCompat.Builder(context, AxBrowserApp.CHANNEL_DOWNLOAD_FAILED)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Download failed: $filename")
                .setContentText(reason)
                .setAutoCancel(true)
                .setContentIntent(tapIntent)
                .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
                .build()
        )
    }

    /** Cancel a notification (call when user cancels or deletes). */
    fun cancel(context: Context, downloadId: String) {
        NotificationManagerCompat.from(context).cancel(notifIdFor(downloadId))
    }

    // ─────────────────────────────────────────

    private fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun notifIdFor(downloadId: String): Int =
        BASE_NOTIFICATION_ID + (downloadId.hashCode() and 0x00FFFFFF)

    private fun tapPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
```

**Note:** `DownloadNotificationManager` imports `MainActivity` from `:app`. Since `feature-downloads` is a library module and `:app` depends on it (not the reverse), you have a circular dependency issue here. **Use this alternative approach** — pass the `Context` and let it find the launcher Activity dynamically:

Replace `tapPendingIntent` function with:
```kotlin
private fun tapPendingIntent(context: Context): PendingIntent {
    // Launch whatever the main launcher activity is — avoids circular dep on :app
    val pm = context.packageManager
    val intent = pm.getLaunchIntentForPackage(context.packageName)
        ?: Intent().apply { `package` = context.packageName }
    intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    return PendingIntent.getActivity(
        context, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
```

And remove the `import com.akay.axbrowser.MainActivity` import.

Keep `import com.akay.axbrowser.AxBrowserApp` — that import IS fine since the
constant `CHANNEL_*` strings need to come from somewhere. Alternatively, just
inline them as string literals in this file:

```kotlin
// Replace AxBrowserApp.CHANNEL_* with literal strings to avoid cross-module dependency:
private const val CHANNEL_PROGRESS = "ax_dl_progress"
private const val CHANNEL_COMPLETE = "ax_dl_complete"
private const val CHANNEL_FAILED   = "ax_dl_failed"
```

And remove the `import com.akay.axbrowser.AxBrowserApp` line entirely.

---

## CHANGE 8 — Wire notifications into DownloadViewModel.startDownload()

**File:** `feature/feature-downloads/src/main/kotlin/com/akay/feature/downloads/viewmodel/DownloadViewModel.kt`

In `startDownload()`, after each `when (progress)` branch, add notification calls:

```kotlin
private fun startDownload(item: DownloadItem) {
    val job = viewModelScope.launch {
        updateItem(item.id) { it.copy(status = ItemStatus.RUNNING) }
        val ctx: Context = getApplication()

        val flow: Flow<DownloadProgressUnified> = if (item.useYtDlp && _state.value.ytDlpReady) {
            ytDlpEngine.download(item.url, item.outputPath, item.formatId)
        } else {
            directEngine.download(item.id, item.url, File(item.outputPath))
        }

        flow.collect { progress ->
            when (progress) {
                is DownloadProgressUnified.Running -> {
                    updateItem(item.id) {
                        it.copy(
                            progress = progress.percent,
                            speedStr = progress.speedStr,
                            totalStr = progress.totalBytesStr,
                            status   = ItemStatus.RUNNING
                        )
                    }
                    // Show / update progress notification
                    DownloadNotificationManager.showProgress(
                        context    = ctx,
                        downloadId = item.id,
                        filename   = item.filename,
                        percent    = progress.percent,
                        speedStr   = progress.speedStr,
                        totalStr   = progress.totalBytesStr
                    )
                }
                DownloadProgressUnified.Completed -> {
                    updateItem(item.id) { it.copy(progress = 100f, status = ItemStatus.COMPLETED) }
                    DownloadNotificationManager.showComplete(ctx, item.id, item.filename)
                }
                is DownloadProgressUnified.Failed -> {
                    updateItem(item.id) {
                        it.copy(status = ItemStatus.FAILED, errorMsg = progress.reason)
                    }
                    DownloadNotificationManager.showFailed(ctx, item.id, item.filename, progress.reason)
                }
            }
        }
    }
    downloadJobs[item.id] = job
}
```

Also call `DownloadNotificationManager.cancel(ctx, id)` in `delete()` and `cancel()`:
```kotlin
fun delete(id: String) {
    val item = _state.value.downloads.find { it.id == id } ?: return
    File(item.outputPath).delete()
    downloadJobs[id]?.cancel()
    DownloadNotificationManager.cancel(getApplication(), id)        // ADD
    _state.update { it.copy(downloads = it.downloads.filter { d -> d.id != id }) }
}

fun cancel(id: String) {
    directEngine.cancel(id)
    downloadJobs[id]?.cancel()
    DownloadNotificationManager.cancel(getApplication(), id)        // ADD
    updateItem(id) { it.copy(status = ItemStatus.CANCELLED) }
}
```

---

## CHANGE 9 — MainActivity: Request runtime permissions on launch

**File:** `app/src/main/kotlin/com/akay/axbrowser/MainActivity.kt`

Replace the entire file:

```kotlin
package com.akay.axbrowser

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.akay.core.ui.theme.AxTheme
import com.akay.feature.browser.ui.BrowserNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // ─── Permission launcher ────────────────────────────────────────────────
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Log results for debugging — non-critical if denied (graceful degradation)
        results.forEach { (perm, granted) ->
            android.util.Log.d("Permissions", "$perm → ${if (granted) "GRANTED" else "DENIED"}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request all permissions we need
        requestRequiredPermissions()

        setContent {
            AxTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BrowserNavHost()
                }
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────

    private fun requestRequiredPermissions() {
        val needed = mutableListOf<String>()

        // POST_NOTIFICATIONS — Android 13+ (API 33)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!isGranted(Manifest.permission.POST_NOTIFICATIONS))
                needed += Manifest.permission.POST_NOTIFICATIONS
        }

        // Granular media permissions — Android 13+ (API 33)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!isGranted(Manifest.permission.READ_MEDIA_VIDEO))
                needed += Manifest.permission.READ_MEDIA_VIDEO
            if (!isGranted(Manifest.permission.READ_MEDIA_AUDIO))
                needed += Manifest.permission.READ_MEDIA_AUDIO
            if (!isGranted(Manifest.permission.READ_MEDIA_IMAGES))
                needed += Manifest.permission.READ_MEDIA_IMAGES
        }

        // Legacy storage — Android 9 and below (API 28-)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (!isGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                needed += Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (!isGranted(Manifest.permission.READ_EXTERNAL_STORAGE))
                needed += Manifest.permission.READ_EXTERNAL_STORAGE
        }

        // Android 10–12 (API 29–32): no runtime storage permission needed for
        // app-specific external directory (getExternalFilesDir) — Android grants it automatically

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
```

---

## CHANGE 10 — SettingsScreen: Update ytDlp status display

**File:** `feature/feature-settings/src/main/kotlin/com/akay/feature/settings/ui/SettingsScreen.kt`

Find the yt-dlp status display and replace with:

```kotlin
// Replace the existing ytDlpInstalled text with:
val ctx = LocalContext.current
SettingsInfo(
    title = "yt-dlp Engine",
    value = if (uiState.ytDlpInstalled)
        YtDlpSetup.statusString(ctx)
    else
        "Not found — rebuild app"
)
```

---

## CHANGE 11 — DownloadManagerScreen: Remove yt-dlp setup banner (no longer needed)

**File:** `feature/feature-downloads/src/main/kotlin/com/akay/feature/downloads/ui/DownloadManagerScreen.kt`

Remove or comment out the `YtDlpSetupBanner` block entirely:

```kotlin
// REMOVE this block — binary is now bundled, no runtime setup:
// if (state.isSettingUpYtDlp) {
//     YtDlpSetupBanner(progress = state.ytDlpSetupProgress)
// }
```

Replace with a simpler banner only when binary is actually missing:
```kotlin
if (!state.ytDlpReady) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning, null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "yt-dlp not available",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    state.setupError ?: "Rebuild the app to rebundle yt-dlp.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}
```

---

## CHANGE 12 — Add .gitignore for jniLibs binaries

**File:** `.gitignore` — add these lines:

```
# yt-dlp binaries — downloaded at build time via Gradle task
app/src/main/jniLibs/arm64-v8a/libytdlp.so
app/src/main/jniLibs/x86_64/libytdlp.so
```

This prevents committing 60–120 MB of binaries to git.
The Gradle task re-downloads them on every fresh clone automatically.

---

## EXECUTION ORDER

```
1.  app/build.gradle.kts              → Add Gradle download task + packaging block
2.  .gitignore                        → Add jniLibs entries
3.  AndroidManifest.xml               → Replace entirely
4.  app/src/main/res/xml/network_security_config.xml → Create
5.  YtDlpSetup.kt                     → Replace entirely
6.  YtDlpEngine.kt                    → Replace entirely
7.  DownloadViewModel.kt              → Remove setup logic, fix directEngine init, wire notifications
8.  DownloadNotificationManager.kt    → Create new file
9.  AxBrowserApp.kt                   → Replace entirely
10. MainActivity.kt                   → Replace entirely
11. DownloadManagerScreen.kt          → Remove setup banner, add missing binary banner
12. SettingsScreen.kt                 → Update yt-dlp status display
```

---

## BUILD & TEST

```bash
# First clean build downloads the binaries
./gradlew clean assembleDebug
```

Watch Gradle output — you should see:
```
⬇ Downloading yt-dlp for arm64-v8a...
✓ arm64 binary: 68123456 bytes → .../libytdlp.so
⬇ Downloading yt-dlp for x86_64...
✓ x86_64 binary: 52345678 bytes → .../libytdlp.so
```

On second build you should see:
```
✓ arm64 yt-dlp already present (68123456 bytes)
✓ x86_64 yt-dlp already present (52345678 bytes)
```

---

## VERIFICATION CHECKLIST

**Permission dialogs:**
- [ ] First app launch shows a system dialog asking for Notifications permission
- [ ] First app launch shows dialog for Read Media Video/Audio (Android 13+)
- [ ] After granting, dialogs don't appear again

**yt-dlp works:**
- [ ] Open browser → go to any page with video
- [ ] Tap floating download button → tap "YT-DLP"
- [ ] App does NOT crash
- [ ] Download appears in Downloads tab as RUNNING (not FAILED)
- [ ] Error msg in failed cards says something useful (not "Permission denied")

**Notifications appear:**
- [ ] While downloading → persistent notification shows filename + progress %
- [ ] On completion → "Download complete: [filename]" notification
- [ ] On failure → "Download failed" notification with reason
- [ ] Tapping notification opens the app

**stream was reset fixed:**
- [ ] Retry a previously failed "INTERNAL_ERROR" download
- [ ] It now either downloads or shows a different, more meaningful error

**Settings shows correct yt-dlp status:**
- [ ] Settings → Developer → yt-dlp shows "Ready ✓ (68.1MB)" or similar
- [ ] NOT "Not found" after a clean install
