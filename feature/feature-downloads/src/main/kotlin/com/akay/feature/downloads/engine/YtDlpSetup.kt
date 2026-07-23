package com.akay.feature.downloads.engine

import android.content.Context
import java.io.File

object YtDlpSetup {

    fun getBinaryPath(context: Context): String =
        "${context.applicationInfo.nativeLibraryDir}/libytdlp.so"

    fun isInstalled(context: Context): Boolean {
        val file = File(getBinaryPath(context))
        return file.exists() && file.length() > 1_000_000L
    }

    fun statusString(context: Context): String {
        val file = File(getBinaryPath(context))
        return when {
            !file.exists()              -> "Not found - rebuild the app"
            file.length() < 1_000_000L -> "Corrupted (${file.length()} bytes)"
            !file.canExecute()          -> "Not executable"
            else                        -> "Ready (${formatSize(file.length())})"
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 * 1024        -> "${bytes / 1024}KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024f * 1024f))}MB"
        else                       -> "${"%.2f".format(bytes / (1024f * 1024f * 1024f))}GB"
    }
}
