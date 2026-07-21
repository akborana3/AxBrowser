package com.akay.core.domain.engine

data class DownloadRequest(
    val id: String,
    val url: String,
    val destinationPath: String,
    val headers: Map<String, String> = emptyMap(),
    val expectedMimeType: String? = null
)
