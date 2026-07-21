package com.akay.core.domain.model

import java.util.UUID

data class HistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String? = null,
    val visitCount: Int = 1,
    val lastVisited: Long = System.currentTimeMillis()
)
