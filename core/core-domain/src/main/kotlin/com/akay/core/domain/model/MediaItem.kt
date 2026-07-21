package com.akay.core.domain.model

import java.util.UUID

data class MediaItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val type: MediaType,
    val mimeType: String? = null,
    val title: String? = null,
    val size: Long? = null,
    val detectedAt: Long = System.currentTimeMillis(),
    val sourceUrl: String
)

enum class MediaType {
    VIDEO,
    AUDIO,
    HLS,
    IMAGE,
    DOCUMENT
}
