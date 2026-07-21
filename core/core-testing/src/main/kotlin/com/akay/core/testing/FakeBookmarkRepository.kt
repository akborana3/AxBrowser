package com.akay.core.testing

import com.akay.core.domain.model.Bookmark
import com.akay.core.domain.model.BookmarkFolder
import com.akay.core.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FakeBookmarkRepository : BookmarkRepository {

    private val bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    private val folders = MutableStateFlow<List<BookmarkFolder>>(emptyList())

    override fun getAllBookmarks(): Flow<List<Bookmark>> = bookmarks

    override fun getBookmarksByFolder(folderId: String?): Flow<List<Bookmark>> {
        return flowOf(bookmarks.value.filter { it.folderId == folderId })
    }

    override fun getAllFolders(): Flow<List<BookmarkFolder>> = folders

    override suspend fun getBookmark(id: String): Bookmark? {
        return bookmarks.value.find { it.id == id }
    }

    override suspend fun addBookmark(bookmark: Bookmark): String {
        bookmarks.value = bookmarks.value + bookmark
        return bookmark.id
    }

    override suspend fun updateBookmark(bookmark: Bookmark) {
        bookmarks.value = bookmarks.value.map {
            if (it.id == bookmark.id) bookmark else it
        }
    }

    override suspend fun deleteBookmark(id: String) {
        bookmarks.value = bookmarks.value.filter { it.id != id }
    }

    override suspend fun createFolder(folder: BookmarkFolder): String {
        folders.value = folders.value + folder
        return folder.id
    }

    override suspend fun deleteFolder(id: String) {
        folders.value = folders.value.filter { it.id != id }
    }

    override fun searchBookmarks(query: String): Flow<List<Bookmark>> {
        return flowOf(bookmarks.value.filter {
            it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true)
        })
    }
}
