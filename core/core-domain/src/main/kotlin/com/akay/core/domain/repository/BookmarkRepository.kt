package com.akay.core.domain.repository

import com.akay.core.domain.model.Bookmark
import com.akay.core.domain.model.BookmarkFolder
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    fun getAllBookmarks(): Flow<List<Bookmark>>
    fun getBookmarksByFolder(folderId: String?): Flow<List<Bookmark>>
    fun getAllFolders(): Flow<List<BookmarkFolder>>
    suspend fun getBookmark(id: String): Bookmark?
    suspend fun addBookmark(bookmark: Bookmark): String
    suspend fun updateBookmark(bookmark: Bookmark)
    suspend fun deleteBookmark(id: String)
    suspend fun createFolder(folder: BookmarkFolder): String
    suspend fun deleteFolder(id: String)
    suspend fun searchBookmarks(query: String): Flow<List<Bookmark>>
}
