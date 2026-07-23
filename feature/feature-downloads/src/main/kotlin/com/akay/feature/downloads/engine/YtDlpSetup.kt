package com.akay.feature.downloads.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object YtDlpSetup {

    private const val TAG = "YtDlpSetup"

    // yt-dlp release assets - try multiple URLs
    private val BINARY_URLS = listOf(
        "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp",
        "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux"
    )

    fun getBinaryFile(context: Context): File =
        File(context.filesDir, "bin/yt-dlp")

    fun isInstalled(context: Context): Boolean {
        val file = getBinaryFile(context)
        return file.exists() && file.length() > 0
    }

    suspend fun setup(
        context: Context,
        client: OkHttpClient,
        onProgress: (Int) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val binDir = File(context.filesDir, "bin").also { it.mkdirs() }
            val binFile = File(binDir, "yt-dlp")

            // Try each URL until one works
            var lastError: Exception? = null
            var downloaded = false

            for (url in BINARY_URLS) {
                try {
                    onProgress(0)
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("User-Agent", "AxBrowser/1.0")
                        .build()

                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        lastError = Exception("HTTP ${response.code} from $url")
                        continue
                    }

                    val body = response.body ?: continue
                    val totalBytes = body.contentLength()

                    binFile.outputStream().use { out ->
                        var bytesDownloaded = 0L
                        body.byteStream().use { input ->
                            val buffer = ByteArray(32768)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                out.write(buffer, 0, bytesRead)
                                bytesDownloaded += bytesRead
                                if (totalBytes > 0) {
                                    onProgress(((bytesDownloaded * 100) / totalBytes).toInt())
                                }
                            }
                        }
                    }

                    downloaded = true
                    break
                } catch (e: Exception) {
                    lastError = e
                    continue
                }
            }

            if (!downloaded) {
                throw lastError ?: Exception("Failed to download yt-dlp from any source")
            }

            // Make executable
            binFile.setExecutable(true, false)
            binFile.setReadable(true, false)

            // Verify
            check(binFile.exists()) { "Binary file does not exist after download" }
            check(binFile.length() > 1000) { "Binary file too small (${binFile.length()} bytes) - likely not a real binary" }

            onProgress(100)
        }
    }
}
