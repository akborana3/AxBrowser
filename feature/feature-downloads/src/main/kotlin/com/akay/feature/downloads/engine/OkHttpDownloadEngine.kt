package com.akay.feature.downloads.engine

import com.akay.core.domain.engine.DownloadEngine
import com.akay.core.domain.engine.DownloadProgress
import com.akay.core.domain.engine.DownloadRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OkHttpDownloadEngine @Inject constructor(
    private val okHttpClient: OkHttpClient
) : DownloadEngine {

    private val activeDownloads = mutableMapOf<String, Boolean>()

    override fun download(request: DownloadRequest): Flow<DownloadProgress> = flow {
        val file = File(request.destinationPath)
        file.parentFile?.mkdirs()

        val downloadedBytes = if (file.exists()) file.length() else 0L
        val requestBuilder = Request.Builder().url(request.url)

        request.headers.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }

        if (downloadedBytes > 0) {
            requestBuilder.addHeader("Range", "bytes=$downloadedBytes-")
        }

        try {
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            val body = response.body ?: throw Exception("Empty response body")

            val totalBytes = response.header("Content-Length")?.toLongOrNull()?.let {
                it + downloadedBytes
            } ?: 0L

            val outputStream = FileOutputStream(file, downloadedBytes > 0)
            val inputStream = body.byteStream()
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead = downloadedBytes
            val startTime = System.currentTimeMillis()

            activeDownloads[request.id] = true

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (activeDownloads[request.id] != true) {
                    outputStream.close()
                    inputStream.close()
                    emit(DownloadProgress.Paused(totalRead))
                    return@flow
                }

                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead

                val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                val speed = if (elapsed > 0) (totalRead - downloadedBytes) / elapsed else 0.0

                emit(DownloadProgress.Running(
                    bytesDownloaded = totalRead,
                    totalBytes = totalBytes,
                    speedBps = speed.toLong()
                ))

                delay(500)
            }

            outputStream.close()
            inputStream.close()
            activeDownloads.remove(request.id)

            emit(DownloadProgress.Completed)
        } catch (e: Exception) {
            activeDownloads.remove(request.id)
            emit(DownloadProgress.Failed(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun pause(downloadId: String) {
        activeDownloads[downloadId] = false
    }

    override suspend fun resume(downloadId: String) {
        activeDownloads[downloadId] = true
    }

    override suspend fun cancel(downloadId: String) {
        activeDownloads.remove(downloadId)
    }

    override suspend fun retry(downloadId: String) {
        activeDownloads[downloadId] = true
    }
}
