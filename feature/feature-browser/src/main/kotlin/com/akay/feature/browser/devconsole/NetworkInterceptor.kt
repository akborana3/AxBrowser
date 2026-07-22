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
)

object NetworkInterceptor {
    private val _requests = MutableStateFlow<List<NetworkRequest>>(emptyList())
    val requests: StateFlow<List<NetworkRequest>> = _requests.asStateFlow()

    private const val MAX_ENTRIES = 500

    fun onRequest(request: NetworkRequest) {
        _requests.update { current ->
            val updated = current + request
            if (updated.size > MAX_ENTRIES) updated.drop(updated.size - MAX_ENTRIES) else updated
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

    fun clear() { _requests.value = emptyList() }

    fun markBlocked(url: String) {
        _requests.update { list ->
            list.map { if (it.url == url) it.copy(isBlocked = true) else it }
        }
    }
}
