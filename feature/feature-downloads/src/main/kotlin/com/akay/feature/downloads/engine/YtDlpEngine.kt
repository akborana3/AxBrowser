package com.akay.feature.downloads.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

class YtDlpEngine(private val context: Context) {

    fun download(
        url: String,
        outputPath: String,
        formatId: String? = null
    ): Flow<DownloadProgressUnified> = flow {
        val binaryPath = runCatching { requireBinary() }.getOrElse { e ->
            emit(DownloadProgressUnified.Failed(e.message ?: "Binary error"))
            return@flow
        }

        val cmd = buildList {
            add(binaryPath)
            add("--no-playlist")
            add("--newline")
            add("--no-warnings")
            add("--no-check-certificates")
            add("--no-part")
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
            ProcessBuilder(cmd).redirectErrorStream(true).start()
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
                exitCode == 1 -> emit(DownloadProgressUnified.Failed("yt-dlp error (exit 1) - URL may be geo-blocked or requires login"))
                else -> emit(DownloadProgressUnified.Failed("yt-dlp exited with code $exitCode"))
            }
        } catch (e: Exception) {
            emit(DownloadProgressUnified.Failed("Download interrupted: ${e.message}"))
        } finally {
            runCatching { process.destroyForcibly() }
        }
    }.flowOn(Dispatchers.IO)

    private fun requireBinary(): String {
        val path = YtDlpSetup.getBinaryPath(context)
        val file = File(path)
        check(file.exists()) { "yt-dlp binary not found at $path - rebuild the app" }
        check(file.length() > 1_000_000L) { "yt-dlp binary corrupted (${file.length()} bytes)" }
        return path
    }

    private fun parseProgressLine(line: String): DownloadProgressUnified.Running? {
        val regex = Regex("""\[download\]\s+([\d.]+)%\s+of\s+~?([\d.]+\w+)\s+at\s+([\d.]+\w+/s)""")
        val match = regex.find(line) ?: return null
        val percent = match.groupValues[1].toFloatOrNull() ?: return null
        return DownloadProgressUnified.Running(percent = percent, totalBytesStr = match.groupValues[2], speedStr = match.groupValues[3])
    }
}
