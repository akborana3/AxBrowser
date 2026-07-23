package com.akay.feature.downloads.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object YtDlpSetup {

    private const val YTDLP_DOWNLOAD_URL =
        "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_android"

    fun getBinaryFile(context: Context): File =
        File(context.filesDir, "bin/yt-dlp")

    fun isInstalled(context: Context): Boolean =
        getBinaryFile(context).let { it.exists() && it.canExecute() }

    suspend fun setup(
        context: Context,
        client: OkHttpClient,
        onProgress: (Int) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val binDir = File(context.filesDir, "bin").also { it.mkdirs() }
            val binFile = File(binDir, "yt-dlp")

            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            val downloadUrl = when {
                abi.contains("x86_64") -> "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux"
                abi.contains("x86")    -> "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux"
                else                   -> YTDLP_DOWNLOAD_URL
            }

            val request = Request.Builder().url(downloadUrl).addHeader("User-Agent", "AxBrowser/1.0").build()
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

            binFile.setExecutable(true, false)
            binFile.setReadable(true, false)

            if (!binFile.canExecute()) {
                try { ProcessBuilder("chmod", "755", binFile.absolutePath).start().waitFor() } catch (_: Exception) {}
            }

            check(binFile.exists()) { "Binary file does not exist after download" }
            check(binFile.length() > 0) { "Binary file is empty" }
        }
    }
}
