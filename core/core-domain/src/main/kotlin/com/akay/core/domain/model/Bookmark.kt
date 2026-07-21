package com.akay.core.domain.model

import java.util.UUID

data class Bookmark(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String,
    val faviconUrl: String? = null,
    val folderId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class BookmarkFolder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val parentId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
