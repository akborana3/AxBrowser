package com.akay.feature.downloads.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.File
import kotlin.coroutines.coroutineContext

class YtDlpEngine(private val context: Context) {

    suspend fun getFormats(url: String): List<MediaFormat> {
        val binary = YtDlpSetup.getBinaryFile(context).absolutePath
        val process = ProcessBuilder(binary, "-F", "--no-playlist", url)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return parseFormats(output)
    }

    fun download(
        url: String,
        outputPath: String,
        formatId: String? = null
    ): Flow<DownloadProgressUnified> = flow {
        val binary = YtDlpSetup.getBinaryFile(context).absolutePath
        val cmd = mutableListOf(
            binary,
            "--no-playlist",
            "--newline",
            "--progress-template", "%(progress)j",
            "-o", outputPath
        )
        if (formatId != null) cmd.addAll(listOf("-f", formatId))
        cmd.add(url)

        val process = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()

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
                emit(DownloadProgressUnified.Failed("yt-dlp exited with code $exitCode"))
            }
        } finally {
            process.destroyForcibly()
        }
    }.flowOn(Dispatchers.IO)

    private fun parseProgressLine(line: String): DownloadProgressUnified? {
        val regex = Regex("""\[download\]\s+([\d.]+)%\s+of\s+([\d.]+\w+)\s+at\s+([\d.]+\w+/s)""")
        val match = regex.find(line) ?: return null
        val percent = match.groupValues[1].toFloatOrNull() ?: return null
        val totalStr = match.groupValues[2]
        val speedStr = match.groupValues[3]
        return DownloadProgressUnified.Running(
            percent = percent,
            totalBytesStr = totalStr,
            speedStr = speedStr
        )
    }

    private fun parseFormats(output: String): List<MediaFormat> {
        val formats = mutableListOf<MediaFormat>()
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

sealed class YtDlpProgress {
    data class Running(val percent: Float, val totalBytesStr: String, val speedStr: String) : YtDlpProgress()
    data object Completed : YtDlpProgress()
    data class Failed(val reason: String) : YtDlpProgress()
}
