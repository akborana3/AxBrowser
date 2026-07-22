package com.akay.feature.browser.devconsole

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class NetworkRequest(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val method: String = "GET",
    val requestHeaders: Map<String, String> = emptyMap(),
    val responseStatus: Int? = null,
    val responseHeaders: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val startTime: Long = System.currentTimeMillis(),
    val isBlocked: Boolean = false
) {
    val isMedia: Boolean
        get() {
            val lowerUrl = url.lowercase()
            val contentType = mimeType?.lowercase() ?: ""
            val videoExts = listOf(".mp4", ".webm", ".mkv", ".avi", ".mov", ".m3u8", ".mpd", ".ts", ".flv")
            val audioExts = listOf(".mp3", ".m4a", ".aac", ".ogg", ".wav", ".flac", ".opus")
            val mediaTypes = listOf("video/", "audio/", "application/x-mpegurl", "application/dash+xml", "application/octet-stream")

            return videoExts.any { lowerUrl.contains(it) }
                || audioExts.any { lowerUrl.contains(it) }
                || mediaTypes.any { contentType.contains(it) }
        }
}

data class DetectedMedia(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val filename: String,
    val mimeType: String?,
    val isVideo: Boolean,
    val source: String
)

object NetworkInterceptor {
    private val _requests = MutableStateFlow<List<NetworkRequest>>(emptyList())
    val requests: StateFlow<List<NetworkRequest>> = _requests.asStateFlow()

    private val _detectedMedia = MutableStateFlow<List<DetectedMedia>>(emptyList())
    val detectedMedia: StateFlow<List<DetectedMedia>> = _detectedMedia.asStateFlow()

    private const val MAX_ENTRIES = 500
    private val seenMediaUrls = mutableSetOf<String>()

    fun onRequest(request: NetworkRequest) {
        _requests.update { current ->
            val updated = current + request
            if (updated.size > MAX_ENTRIES) updated.drop(updated.size - MAX_ENTRIES) else updated
        }
        if (request.isMedia && !seenMediaUrls.contains(request.url)) {
            seenMediaUrls.add(request.url)
            val filename = request.url.substringAfterLast("/").substringBefore("?")
                .ifBlank { "media_${System.currentTimeMillis()}" }
            val isVideo = request.mimeType?.startsWith("video") == true
                || listOf(".mp4", ".webm", ".mkv", ".avi", ".mov", ".m3u8", ".mpd", ".ts").any { request.url.lowercase().contains(it) }

            val media = DetectedMedia(
                url = request.url,
                filename = filename,
                mimeType = request.mimeType,
                isVideo = isVideo,
                source = "network"
            )
            _detectedMedia.update { it + media }
        }
    }

    fun onResponse(requestId: String, status: Int, headers: Map<String, String>, sizeBytes: Long) {
        _requests.update { list ->
            list.map { req ->
                if (req.id == requestId) req.copy(
                    responseStatus = status,
                    responseHeaders = headers,
                    sizeBytes = sizeBytes,
                    durationMs = System.currentTimeMillis() - req.startTime
                ) else req
            }
        }
    }

    fun addDomMedia(url: String, type: String) {
        if (seenMediaUrls.contains(url)) return
        seenMediaUrls.add(url)
        val filename = url.substringAfterLast("/").substringBefore("?")
            .ifBlank { "media_${System.currentTimeMillis()}" }
        val isVideo = type == "video" || type == "source"
            || listOf(".mp4", ".webm", ".mkv", ".avi", ".mov", ".m3u8").any { url.lowercase().contains(it) }

        val media = DetectedMedia(
            url = url,
            filename = filename,
            mimeType = null,
            isVideo = isVideo,
            source = "dom"
        )
        _detectedMedia.update { it + media }
    }

    fun clearDetectedMedia() {
        _detectedMedia.value = emptyList()
        seenMediaUrls.clear()
    }

    fun clear() {
        _requests.value = emptyList()
        clearDetectedMedia()
    }

    fun markBlocked(url: String) {
        _requests.update { list ->
            list.map { if (it.url == url) it.copy(isBlocked = true) else it }
        }
    }
}
