package com.akay.core.domain.engine

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

            Runtime.getRuntime().exec(arrayOf("chmod", "+x", binFile.absolutePath)).waitFor()
            check(binFile.canExecute()) { "Binary not executable after chmod" }
        }
    }
}
