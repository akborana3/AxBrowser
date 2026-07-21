package com.akay.core.data.repository

import com.akay.core.data.db.dao.BookmarkDao
import com.akay.core.data.mapper.toDomain
import com.akay.core.data.mapper.toEntity
import com.akay.core.domain.model.Bookmark
import com.akay.core.domain.model.BookmarkFolder
import com.akay.core.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao
) : BookmarkRepository {

    override fun getAllBookmarks(): Flow<List<Bookmark>> {
        return bookmarkDao.getAllBookmarks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getBookmarksByFolder(folderId: String?): Flow<List<Bookmark>> {
        return bookmarkDao.getBookmarksByFolder(folderId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAllFolders(): Flow<List<BookmarkFolder>> {
        return bookmarkDao.getAllFolders().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getBookmark(id: String): Bookmark? {
        return bookmarkDao.getBookmark(id)?.toDomain()
    }

    override suspend fun addBookmark(bookmark: Bookmark): String {
        bookmarkDao.insertBookmark(bookmark.toEntity())
        return bookmark.id
    }

    override suspend fun updateBookmark(bookmark: Bookmark) {
        bookmarkDao.updateBookmark(bookmark.toEntity())
    }

    override suspend fun deleteBookmark(id: String) {
        bookmarkDao.deleteBookmarkById(id)
    }

    override suspend fun createFolder(folder: BookmarkFolder): String {
        bookmarkDao.insertFolder(folder.toEntity())
        return folder.id
    }

    override suspend fun deleteFolder(id: String) {
        bookmarkDao.deleteFolderById(id)
    }

    override fun searchBookmarks(query: String): Flow<List<Bookmark>> {
        return bookmarkDao.searchBookmarks(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }
}
